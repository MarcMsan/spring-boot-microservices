package com.vidopos.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidopos.order.idempotency.RequestHasher;
import com.vidopos.order.orders.CreateOrderRequest;
import com.vidopos.order.outbox.RetryBackoff;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderRulesTest {

    @Test
    void requestHashIgnoresEmailCaseAndItemOrder() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        CreateOrderRequest left = new CreateOrderRequest("A@Example.com", List.of(
                new CreateOrderRequest.Item(second, 2),
                new CreateOrderRequest.Item(first, 1)));
        CreateOrderRequest right = new CreateOrderRequest("a@example.com", List.of(
                new CreateOrderRequest.Item(first, 1),
                new CreateOrderRequest.Item(second, 2)));
        CreateOrderRequest different = new CreateOrderRequest("a@example.com", List.of(
                new CreateOrderRequest.Item(first, 3),
                new CreateOrderRequest.Item(second, 2)));

        assertThat(RequestHasher.hash(left)).isEqualTo(RequestHasher.hash(right));
        assertThat(RequestHasher.hash(left)).isNotEqualTo(RequestHasher.hash(different));
    }

    @Test
    void backoffGrowsAndThenCaps() {
        assertThat(RetryBackoff.nextDelay(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(RetryBackoff.nextDelay(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(RetryBackoff.nextDelay(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(RetryBackoff.nextDelay(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(RetryBackoff.nextDelay(5)).isEqualTo(Duration.ofSeconds(30));
        assertThat(RetryBackoff.nextDelay(9)).isEqualTo(Duration.ofSeconds(30));
    }
}
