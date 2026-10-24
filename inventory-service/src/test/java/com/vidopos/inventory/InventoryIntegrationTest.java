package com.vidopos.inventory;

import com.vidopos.inventory.config.DemoProperties;
import com.vidopos.inventory.product.CreateProductRequest;
import com.vidopos.inventory.product.ProductResponse;
import com.vidopos.inventory.reservation.ReservationResponse;
import com.vidopos.inventory.reservation.ReserveStockRequest;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class InventoryIntegrationTest {

    static final EmbeddedPostgres POSTGRES = EmbeddedPostgresSupport.start();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DemoProperties demoProperties;

    @BeforeEach
    void resetDatabase() {
        demoProperties.setReserveDelay(Duration.ZERO);
        jdbc.execute("TRUNCATE TABLE reservation_items, reservations, products");
    }

    ProductResponse createProduct(String name, String price, int quantity) {
        ResponseEntity<ProductResponse> response = rest.exchange(
                "/api/products",
                HttpMethod.POST,
                new HttpEntity<>(new CreateProductRequest(name, new BigDecimal(price), quantity), json()),
                ProductResponse.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("Product was not created: " + response);
        }
        return response.getBody();
    }

    ResponseEntity<ReservationResponse> reserve(UUID orderId, UUID productId, int quantity) {
        return reserve(orderId, List.of(new ReserveStockRequest.Item(productId, quantity)));
    }

    ResponseEntity<ReservationResponse> reserve(UUID orderId, List<ReserveStockRequest.Item> items) {
        return rest.exchange(
                "/api/reservations",
                HttpMethod.POST,
                new HttpEntity<>(new ReserveStockRequest(orderId, items), json()),
                ReservationResponse.class);
    }

    int stock(UUID productId) {
        Integer available = jdbc.queryForObject(
                "select available_quantity from products where id = ?",
                Integer.class,
                productId);
        if (available == null) {
            throw new IllegalStateException("Missing product " + productId);
        }
        return available;
    }

    private static HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
