package com.vidopos.inventory.reservation;

import com.vidopos.inventory.product.Product;
import com.vidopos.inventory.product.ProductService;
import com.vidopos.inventory.web.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationTransaction {

    private static final Logger log = LoggerFactory.getLogger(ReservationTransaction.class);

    private final ReservationRepository reservations;
    private final EntityManager entityManager;
    private final Clock clock;

    public ReservationTransaction(ReservationRepository reservations, EntityManager entityManager, Clock clock) {
        this.reservations = reservations;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * One database transaction is the consistency boundary. The advisory lock serializes
     * duplicate deliveries of the same order. Product rows are then locked in id order so
     * overlapping orders cannot deadlock or oversell. The decision is inserted here, so a
     * later delivery returns it and does not move stock again.
     */
    @Transactional
    public ReservationDecision decide(ReserveStockRequest request) {
        lockOrder(request.orderId());
        var existing = reservations.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            warnIfPayloadDiffers(existing.get(), request);
            return new ReservationDecision(ReservationService.toResponse(existing.get()), false);
        }

        List<UUID> productIds = request.items().stream()
                .map(ReserveStockRequest.Item::productId)
                .sorted()
                .toList();
        Map<UUID, Product> products = lockProducts(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        List<String> problems = new ArrayList<>();
        for (ReserveStockRequest.Item item : request.items()) {
            Product product = products.get(item.productId());
            if (product == null) {
                problems.add("Unknown product " + item.productId());
            } else if (product.getAvailableQuantity() < item.quantity()) {
                problems.add("Insufficient stock for product " + product.getId()
                        + ": requested " + item.quantity()
                        + ", available " + product.getAvailableQuantity());
            }
        }

        if (!problems.isEmpty()) {
            Reservation rejected = store(request, ReservationStatus.REJECTED, String.join("; ", problems), products);
            log.info("Reservation rejected orderId={} reason={}", request.orderId(), rejected.getReason());
            return new ReservationDecision(ReservationService.toResponse(rejected), true);
        }

        for (ReserveStockRequest.Item item : request.items()) {
            Product product = products.get(item.productId());
            product.setAvailableQuantity(product.getAvailableQuantity() - item.quantity());
        }
        Reservation reserved = store(request, ReservationStatus.RESERVED, null, products);
        log.info("Reservation stored orderId={} status=RESERVED", request.orderId());
        return new ReservationDecision(ReservationService.toResponse(reserved), true);
    }

    @Transactional(readOnly = true)
    public ReservationResponse load(UUID orderId) {
        return reservations.findByOrderId(orderId)
                .map(ReservationService::toResponse)
                .orElseThrow(() -> new NotFoundException("Reservation not found for order " + orderId));
    }

    private Reservation store(
            ReserveStockRequest request,
            ReservationStatus status,
            String reason,
            Map<UUID, Product> products) {
        Reservation reservation = Reservation.newDecision(request.orderId(), status, reason, clock.instant());
        for (ReserveStockRequest.Item item : request.items()) {
            Product product = products.get(item.productId());
            BigDecimal unitPrice = product == null ? null : ProductService.money(product.getPrice());
            reservation.addItem(new ReservationItem(item.productId(), item.quantity(), unitPrice));
        }
        reservations.saveAndFlush(reservation);
        return reservation;
    }

    private List<Product> lockProducts(List<UUID> ids) {
        return entityManager.createQuery("""
                        select p from Product p
                        where p.id in :ids
                        order by p.id
                        """, Product.class)
                .setParameter("ids", ids)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    private void lockOrder(UUID orderId) {
        entityManager.createNativeQuery("select pg_advisory_xact_lock(?1)")
                .setParameter(1, advisoryKey(orderId))
                .getResultList();
    }

    static long advisoryKey(UUID orderId) {
        return orderId.getMostSignificantBits() ^ orderId.getLeastSignificantBits();
    }

    private static void warnIfPayloadDiffers(Reservation existing, ReserveStockRequest request) {
        Map<UUID, Integer> stored = existing.getItems().stream()
                .collect(Collectors.toMap(ReservationItem::getProductId, ReservationItem::getQuantity));
        Map<UUID, Integer> incoming = request.items().stream()
                .collect(Collectors.toMap(ReserveStockRequest.Item::productId, ReserveStockRequest.Item::quantity));
        if (!stored.equals(incoming)) {
            log.warn(
                    "Ignoring a different reservation payload for orderId={} because a decision already exists correlationId={}",
                    request.orderId(),
                    MDC.get("correlationId"));
        }
    }
}
