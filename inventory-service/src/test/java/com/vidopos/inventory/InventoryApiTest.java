package com.vidopos.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidopos.inventory.product.CreateProductRequest;
import com.vidopos.inventory.product.ProductResponse;
import com.vidopos.inventory.product.UpdatePriceRequest;
import com.vidopos.inventory.reservation.ReservationResponse;
import com.vidopos.inventory.reservation.ReservationStatus;
import com.vidopos.inventory.reservation.ReserveStockRequest;
import com.vidopos.inventory.web.ApiError;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class InventoryApiTest extends InventoryIntegrationTest {

    @Test
    void createsProductAndRejectsInvalidInput() {
        ProductResponse created = createProduct("Cable", "9.50", 5);
        assertThat(created.price()).isEqualByComparingTo("9.50");
        assertThat(created.availableQuantity()).isEqualTo(5);

        ResponseEntity<ProductResponse> loaded = rest.getForEntity("/api/products/" + created.id(), ProductResponse.class);
        assertThat(loaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loaded.getBody().name()).isEqualTo("Cable");

        ResponseEntity<ApiError> negativePrice = rest.exchange(
                "/api/products",
                HttpMethod.POST,
                json(new CreateProductRequest("Bad", new BigDecimal("-1.00"), 1)),
                ApiError.class);
        assertThat(negativePrice.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(negativePrice.getBody().message()).contains("Validation failed");

        ResponseEntity<ApiError> blankName = rest.exchange(
                "/api/products",
                HttpMethod.POST,
                json(new CreateProductRequest("  ", new BigDecimal("1.00"), 1)),
                ApiError.class);
        assertThat(blankName.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void reservesAllItemsAtTheCurrentPrice() {
        ProductResponse cable = createProduct("Cable", "4.50", 5);
        ProductResponse adapter = createProduct("Adapter", "1.25", 5);

        ResponseEntity<ReservationResponse> response = reserve(
                UUID.randomUUID(),
                List.of(
                        new ReserveStockRequest.Item(adapter.id(), 4),
                        new ReserveStockRequest.Item(cable.id(), 2)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(response.getBody().items()).extracting(ReservationResponse.Item::unitPrice)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(new BigDecimal("1.25"), new BigDecimal("4.50"));
        assertThat(stock(cable.id())).isEqualTo(3);
        assertThat(stock(adapter.id())).isEqualTo(1);

        rest.exchange(
                "/api/products/" + cable.id() + "/price",
                HttpMethod.PATCH,
                json(new UpdatePriceRequest(new BigDecimal("9.99"))),
                ProductResponse.class);

        ResponseEntity<ReservationResponse> stored = rest.getForEntity(
                "/api/reservations/" + response.getBody().orderId(),
                ReservationResponse.class);
        assertThat(stored.getBody().items())
                .filteredOn(item -> item.productId().equals(cable.id()))
                .singleElement()
                .extracting(ReservationResponse.Item::unitPrice)
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("4.50"));
        assertThat(rest.getForEntity("/api/products/" + cable.id(), ProductResponse.class).getBody().price())
                .isEqualByComparingTo("9.99");
    }

    @Test
    void insufficientStockRejectsTheWholeOrderAndLeavesStockUntouched() {
        ProductResponse cable = createProduct("Cable", "4.50", 5);
        ProductResponse adapter = createProduct("Adapter", "1.25", 0);

        ResponseEntity<ReservationResponse> response = reserve(
                UUID.randomUUID(),
                List.of(
                        new ReserveStockRequest.Item(cable.id(), 1),
                        new ReserveStockRequest.Item(adapter.id(), 1)));

        assertThat(response.getBody().status()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(response.getBody().reason()).contains("Insufficient stock");
        assertThat(stock(cable.id())).isEqualTo(5);
        assertThat(stock(adapter.id())).isEqualTo(0);
        assertThat(jdbc.queryForObject("select count(*) from reservations", Integer.class)).isEqualTo(1);
    }

    @Test
    void unknownProductRejectsWithoutReservingTheKnownProduct() {
        ProductResponse cable = createProduct("Cable", "4.50", 5);
        UUID missing = UUID.randomUUID();

        ResponseEntity<ReservationResponse> response = reserve(
                UUID.randomUUID(),
                List.of(
                        new ReserveStockRequest.Item(cable.id(), 1),
                        new ReserveStockRequest.Item(missing, 1)));

        assertThat(response.getBody().status()).isEqualTo(ReservationStatus.REJECTED);
        assertThat(response.getBody().reason()).contains("Unknown product " + missing);
        assertThat(stock(cable.id())).isEqualTo(5);
    }

    @Test
    void repeatingAReservationDoesNotMoveStockAgain() {
        ProductResponse cable = createProduct("Cable", "4.50", 5);
        UUID orderId = UUID.randomUUID();

        ReservationResponse first = reserve(orderId, cable.id(), 2).getBody();
        ReservationResponse second = reserve(orderId, cable.id(), 2).getBody();

        assertThat(first.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(second.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(second.items().get(0).unitPrice()).isEqualByComparingTo(first.items().get(0).unitPrice());
        assertThat(stock(cable.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from reservations where order_id = ?", Integer.class, orderId))
                .isEqualTo(1);
    }

    @Test
    void lostResponseIsAlreadyCommittedAndARetryDoesNotReserveAgain() throws Exception {
        ProductResponse cable = createProduct("Cable", "4.50", 10);
        UUID orderId = UUID.randomUUID();
        demoProperties.setReserveDelay(Duration.ofMillis(1200));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<ReservationResponse>> first = executor.submit(() -> reserve(orderId, cable.id(), 2));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            Integer decisions = 0;
            while (System.nanoTime() < deadline) {
                decisions = jdbc.queryForObject(
                        "select count(*) from reservations where order_id = ?",
                        Integer.class,
                        orderId);
                if (decisions != null && decisions == 1) {
                    break;
                }
                Thread.sleep(20);
            }
            assertThat(decisions).isEqualTo(1);
            assertThat(stock(cable.id())).isEqualTo(8);

            ResponseEntity<ReservationResponse> retry = reserve(orderId, cable.id(), 2);
            assertThat(retry.getBody().status()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(stock(cable.id())).isEqualTo(8);

            ResponseEntity<ReservationResponse> held = first.get(5, TimeUnit.SECONDS);
            assertThat(held.getBody().status()).isEqualTo(ReservationStatus.RESERVED);
            assertThat(stock(cable.id())).isEqualTo(8);
        } finally {
            executor.shutdownNow();
        }
    }

    private HttpEntity<?> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
