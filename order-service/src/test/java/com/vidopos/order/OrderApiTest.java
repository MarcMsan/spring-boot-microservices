package com.vidopos.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidopos.order.orders.CreateOrderRequest;
import com.vidopos.order.orders.OrderItemResponse;
import com.vidopos.order.orders.OrderResponse;
import com.vidopos.order.orders.OrderStatus;
import com.vidopos.order.web.ApiError;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderApiTest extends OrderIntegrationTest {

    @Test
    void confirmsAnOrderWithAuthoritativePrices() {
        UUID productId = UUID.randomUUID();
        ResponseEntity<OrderResponse> response = postOrder("order-happy-1", "Buyer@Example.com", productId, 3, "corr-happy");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderResponse order = response.getBody();
        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.customerEmail()).isEqualTo("buyer@example.com");
        assertThat(order.total()).isEqualByComparingTo("30.00");
        assertThat(order.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.unitPrice()).isEqualByComparingTo("10.00");
                    assertThat(item.lineTotal()).isEqualByComparingTo("30.00");
                    assertThat(item.quantity()).isEqualTo(3);
                });
        assertThat(order.correlationId()).isEqualTo("corr-happy");
        assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("corr-happy");
        assertThat(INVENTORY.requests())
                .anyMatch(request -> "corr-happy".equals(request.getHeader("X-Correlation-Id")));

        ResponseEntity<OrderResponse> loaded = rest.getForEntity("/api/orders/" + order.id(), OrderResponse.class);
        assertThat(loaded.getBody().total()).isEqualByComparingTo("30.00");
        assertThat(loaded.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("corr-happy");
    }

    @Test
    void pricesTwoLinesFromInventory() {
        CreateOrderRequest request = new CreateOrderRequest(
                "lines@example.com",
                List.of(
                        new CreateOrderRequest.Item(UUID.randomUUID(), 2),
                        new CreateOrderRequest.Item(UUID.randomUUID(), 3)));
        ResponseEntity<OrderResponse> response = postRaw("order-lines-1", request, null, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().total()).isEqualByComparingTo("50.00");
        assertThat(response.getBody().items()).extracting(OrderItemResponse::lineTotal)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(new BigDecimal("20.00"), new BigDecimal("30.00"));
    }

    @Test
    void rejectsMissingKeyInvalidEmailClientPriceAndDuplicateProducts() {
        UUID productId = UUID.randomUUID();
        ResponseEntity<ApiError> missingKey = postRaw(
                null,
                new CreateOrderRequest("a@example.com", List.of(new CreateOrderRequest.Item(productId, 1))),
                null,
                ApiError.class);
        assertThat(missingKey.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingKey.getBody().message()).contains("Idempotency-Key");

        ResponseEntity<ApiError> badEmail = postRaw(
                "order-email-1",
                new CreateOrderRequest("not-an-email", List.of(new CreateOrderRequest.Item(productId, 1))),
                null,
                ApiError.class);
        assertThat(badEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String withPrice = """
                {"customerEmail":"a@example.com","items":[{"productId":"%s","quantity":1,"price":1.00}]}
                """.formatted(productId);
        ResponseEntity<ApiError> clientPrice = postRaw("order-price-1", withPrice, null, ApiError.class);
        assertThat(clientPrice.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(INVENTORY.calls()).isZero();

        ResponseEntity<ApiError> duplicate = postRaw(
                "order-duplicate-1",
                new CreateOrderRequest("a@example.com", List.of(
                        new CreateOrderRequest.Item(productId, 1),
                        new CreateOrderRequest.Item(productId, 2))),
                null,
                ApiError.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(duplicate.getBody().message()).contains("Duplicate product");
        assertThat(orders()).isZero();
    }

    @Test
    void sameKeyAndBodyReturnsTheExistingOrder() {
        UUID productId = UUID.randomUUID();
        OrderResponse created = postOrder("order-same-1", "a@example.com", productId, 1).getBody();
        ResponseEntity<OrderResponse> replay = postOrder("order-same-1", "A@Example.com", productId, 1);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().id()).isEqualTo(created.id());
        assertThat(replay.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(orders()).isEqualTo(1);
        assertThat(INVENTORY.calls()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentContentIsRejected() {
        UUID productId = UUID.randomUUID();
        OrderResponse created = postOrder("order-conflict-1", "a@example.com", productId, 1).getBody();
        ResponseEntity<ApiError> conflict = postRaw(
                "order-conflict-1",
                new CreateOrderRequest("a@example.com", List.of(new CreateOrderRequest.Item(productId, 2))),
                null,
                ApiError.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().message()).contains("different request");
        assertThat(orders()).isEqualTo(1);
        assertThat(rest.getForEntity("/api/orders/" + created.id(), OrderResponse.class).getBody().items().get(0).quantity())
                .isEqualTo(1);
    }

    @Test
    void inventoryRejectionBecomesARejectedOrder() {
        INVENTORY.setBehavior(InventoryDouble.Behavior.REJECT);
        ResponseEntity<OrderResponse> response = postOrder("order-reject-1", "a@example.com", UUID.randomUUID(), 1);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(response.getBody().total()).isNull();
        assertThat(response.getBody().rejectionReason()).contains("Insufficient stock");
        assertThat(orders()).isEqualTo(1);
    }

    @Test
    void unavailableInventoryStaysPendingAndRecoversWithoutAFalseConfirmation() {
        INVENTORY.setBehavior(InventoryDouble.Behavior.UNAVAILABLE);
        ResponseEntity<OrderResponse> pending = postOrder("order-down-1", "a@example.com", UUID.randomUUID(), 2);

        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(pending.getBody().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(pending.getBody().total()).isNull();
        assertThat(jdbc.queryForObject(
                "select status from customer_orders where id = ?",
                String.class,
                pending.getBody().id())).isEqualTo("PENDING");

        INVENTORY.setBehavior(InventoryDouble.Behavior.RESERVE);
        ResponseEntity<OrderResponse> recovered = postOrder(
                "order-down-1",
                "a@example.com",
                pending.getBody().items().get(0).productId(),
                2);

        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recovered.getBody().id()).isEqualTo(pending.getBody().id());
        assertThat(recovered.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(recovered.getBody().total()).isEqualByComparingTo("20.00");
        assertThat(orders()).isEqualTo(1);
    }

    @Test
    void timedOutReservationCanBeRetriedOnceTheResponseWasLost() {
        INVENTORY.setDelayMs(2_000);
        UUID productId = UUID.randomUUID();
        ResponseEntity<OrderResponse> pending = postOrder("order-timeout-1", "a@example.com", productId, 1);

        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(pending.getBody().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(INVENTORY.calls()).isEqualTo(1);

        INVENTORY.setDelayMs(0);
        ResponseEntity<OrderResponse> recovered = postOrder("order-timeout-1", "a@example.com", productId, 1);

        assertThat(recovered.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(recovered.getBody().id()).isEqualTo(pending.getBody().id());
        assertThat(INVENTORY.calls()).isGreaterThanOrEqualTo(2);
        assertThat(orders()).isEqualTo(1);
    }

    @Test
    void crashAfterReservationRecoversToTheSameOrder() {
        demoProperties.setFailBeforeStatusUpdate(true);
        UUID productId = UUID.randomUUID();
        ResponseEntity<OrderResponse> pending = postOrder("order-crash-1", "a@example.com", productId, 4);

        assertThat(pending.getBody().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(INVENTORY.calls()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from outbox", String.class)).isEqualTo("PENDING");

        demoProperties.setFailBeforeStatusUpdate(false);
        relay.processDue();
        jdbc.update("update outbox set next_attempt_at = now() - interval '1 minute' where status = 'PENDING'");
        relay.processDue();

        OrderResponse recovered = rest.getForEntity("/api/orders/" + pending.getBody().id(), OrderResponse.class).getBody();
        assertThat(recovered.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(recovered.total()).isEqualByComparingTo("40.00");
        assertThat(INVENTORY.calls()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from outbox", String.class)).isEqualTo("SENT");
    }

    @Test
    void schedulerRecoversAPendingOrderAfterInventoryReturns() {
        INVENTORY.setBehavior(InventoryDouble.Behavior.UNAVAILABLE);
        OrderResponse pending = postOrder("order-relay-1", "a@example.com", UUID.randomUUID(), 1).getBody();
        assertThat(pending.status()).isEqualTo(OrderStatus.PENDING);

        INVENTORY.setBehavior(InventoryDouble.Behavior.RESERVE);
        jdbc.update("update outbox set next_attempt_at = now() - interval '1 minute', status = 'PENDING'");
        relay.processDue();

        OrderResponse recovered = rest.getForEntity("/api/orders/" + pending.id(), OrderResponse.class).getBody();
        assertThat(recovered.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(orders()).isEqualTo(1);
    }

    @Test
    @Timeout(30)
    void concurrentRepeatsOfTheSameKeyCreateOneOrder() throws Exception {
        UUID productId = UUID.randomUUID();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        try {
            List<Future<ResponseEntity<OrderResponse>>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return postOrder("order-race-1", "a@example.com", productId, 1);
                }));
            }
            UUID id = null;
            for (Future<ResponseEntity<OrderResponse>> result : results) {
                ResponseEntity<OrderResponse> response = result.get(20, TimeUnit.SECONDS);
                assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                if (id == null) {
                    id = response.getBody().id();
                } else {
                    assertThat(response.getBody().id()).isEqualTo(id);
                }
            }
            assertThat(orders()).isEqualTo(1);
            assertThat(rest.getForEntity("/api/orders/" + id, OrderResponse.class).getBody().status())
                    .isEqualTo(OrderStatus.CONFIRMED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void unknownOrderIsNotFound() {
        ResponseEntity<ApiError> response = rest.getForEntity("/api/orders/" + UUID.randomUUID(), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
    }
}
