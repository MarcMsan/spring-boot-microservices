package com.vidopos.inventory.reservation;

import com.vidopos.inventory.product.ProductService;
import com.vidopos.inventory.web.InvalidRequestException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationTransaction transactions;

    public ReservationService(ReservationTransaction transactions) {
        this.transactions = transactions;
    }

    public ReservationDecision reserve(ReserveStockRequest request) {
        rejectDuplicateProducts(request.items());
        try {
            return transactions.decide(request);
        } catch (DataIntegrityViolationException exception) {
            if (!isOrderConflict(exception)) {
                throw exception;
            }
            log.info("Reservation already stored for orderId={}; returning the original decision", request.orderId());
            return new ReservationDecision(transactions.load(request.orderId()), false);
        }
    }

    public ReservationResponse getByOrderId(UUID orderId) {
        return transactions.load(orderId);
    }

    private static void rejectDuplicateProducts(List<ReserveStockRequest.Item> items) {
        Set<UUID> seen = new HashSet<>();
        for (ReserveStockRequest.Item item : items) {
            if (!seen.add(item.productId())) {
                throw new InvalidRequestException("Duplicate product in reservation: " + item.productId());
            }
        }
    }

    private static boolean isOrderConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("uq_reservations_order_id")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static ReservationResponse toResponse(Reservation reservation) {
        List<ReservationResponse.Item> items = reservation.getItems().stream()
                .sorted(java.util.Comparator.comparing(ReservationItem::getProductId))
                .map(item -> new ReservationResponse.Item(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getUnitPrice() == null ? null : ProductService.money(item.getUnitPrice())))
                .toList();
        return new ReservationResponse(
                reservation.getOrderId(),
                reservation.getStatus(),
                reservation.getReason(),
                items);
    }
}
