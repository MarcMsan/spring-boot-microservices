package com.vidopos.order;

import com.vidopos.order.config.DemoProperties;
import com.vidopos.order.orders.CreateOrderRequest;
import com.vidopos.order.orders.OrderResponse;
import com.vidopos.order.outbox.OutboxRelay;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import okhttp3.mockwebserver.MockWebServer;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.outbox.enabled=false",
        "inventory.client.connect-timeout=500ms",
        "inventory.client.read-timeout=750ms",
        "spring.datasource.hikari.maximum-pool-size=20"
})
abstract class OrderIntegrationTest {

    static final InventoryDouble INVENTORY = new InventoryDouble();
    static final MockWebServer INVENTORY_SERVER = startInventory();
    static final EmbeddedPostgres POSTGRES = EmbeddedPostgresSupport.start();

    private static MockWebServer startInventory() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(INVENTORY.dispatcher());
        try {
            server.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.shutdown();
            } catch (IOException ignored) {
            }
        }));
        return server;
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("inventory.client.base-url", OrderIntegrationTest::inventoryBaseUrl);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DemoProperties demoProperties;

    @Autowired
    OutboxRelay relay;

    @BeforeEach
    void resetState() {
        demoProperties.setFailBeforeStatusUpdate(false);
        INVENTORY.reset();
        jdbc.execute("TRUNCATE TABLE order_items, outbox, customer_orders");
    }

    ResponseEntity<OrderResponse> postOrder(String key, String email, UUID productId, int quantity) {
        return postOrder(key, email, productId, quantity, null);
    }

    ResponseEntity<OrderResponse> postOrder(
            String key,
            String email,
            UUID productId,
            int quantity,
            String correlationId) {
        CreateOrderRequest request = new CreateOrderRequest(
                email,
                List.of(new CreateOrderRequest.Item(productId, quantity)));
        return postRaw(key, request, correlationId, OrderResponse.class);
    }

    <T> ResponseEntity<T> postRaw(String key, Object body, String correlationId, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        if (correlationId != null) {
            headers.set("X-Correlation-Id", correlationId);
        }
        return rest.exchange("/api/orders", HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    int orders() {
        Integer count = jdbc.queryForObject("select count(*) from customer_orders", Integer.class);
        return count == null ? 0 : count;
    }

    private static String inventoryBaseUrl() {
        String url = INVENTORY_SERVER.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
