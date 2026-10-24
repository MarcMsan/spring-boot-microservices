package com.vidopos.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.vidopos.inventory.product.ProductResponse;
import com.vidopos.inventory.reservation.ReservationResponse;
import com.vidopos.inventory.reservation.ReservationStatus;
import com.vidopos.inventory.reservation.ReserveStockRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class InventoryConcurrencyTest extends InventoryIntegrationTest {

    @Test
    @Timeout(30)
    void atMostOneOrderGetsTheLastUnit() throws Exception {
        ProductResponse cable = createProduct("Cable", "5.00", 1);
        int threads = 12;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        try {
            List<Future<ReservationResponse>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return reserve(UUID.randomUUID(), cable.id(), 1).getBody();
                }));
            }
            long reserved = 0;
            long rejected = 0;
            for (Future<ReservationResponse> result : results) {
                ReservationStatus status = result.get(20, TimeUnit.SECONDS).status();
                if (status == ReservationStatus.RESERVED) {
                    reserved++;
                } else if (status == ReservationStatus.REJECTED) {
                    rejected++;
                }
            }
            assertThat(reserved).isEqualTo(1);
            assertThat(rejected).isEqualTo(threads - 1);
            assertThat(stock(cable.id())).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(30)
    void duplicateDeliveriesOfTheSameOrderReserveOnce() throws Exception {
        ProductResponse cable = createProduct("Cable", "5.00", 10);
        UUID orderId = UUID.randomUUID();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        try {
            List<Future<ReservationResponse>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return reserve(orderId, cable.id(), 3).getBody();
                }));
            }
            for (Future<ReservationResponse> result : results) {
                assertThat(result.get(20, TimeUnit.SECONDS).status()).isEqualTo(ReservationStatus.RESERVED);
            }
            assertThat(stock(cable.id())).isEqualTo(7);
            assertThat(jdbc.queryForObject("select count(*) from reservations where order_id = ?", Integer.class, orderId))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(30)
    void overlappingOrdersLockProductsInAStableOrder() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            ProductResponse first = createProduct("First-" + attempt, "1.00", 1);
            ProductResponse second = createProduct("Second-" + attempt, "1.00", 1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CyclicBarrier barrier = new CyclicBarrier(2);
            try {
                Future<ReservationResponse> forward = executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return reserve(UUID.randomUUID(), List.of(
                            new ReserveStockRequest.Item(first.id(), 1),
                            new ReserveStockRequest.Item(second.id(), 1))).getBody();
                });
                Future<ReservationResponse> backward = executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return reserve(UUID.randomUUID(), List.of(
                            new ReserveStockRequest.Item(second.id(), 1),
                            new ReserveStockRequest.Item(first.id(), 1))).getBody();
                });
                long reserved = List.of(forward.get(10, TimeUnit.SECONDS), backward.get(10, TimeUnit.SECONDS)).stream()
                        .filter(response -> response.status() == ReservationStatus.RESERVED)
                        .count();
                assertThat(reserved).isEqualTo(1);
                assertThat(stock(first.id())).isZero();
                assertThat(stock(second.id())).isZero();
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
