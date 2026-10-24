package com.vidopos.inventory.reservation;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservations;
    private final ReserveDelay reserveDelay;

    public ReservationController(ReservationService reservations, ReserveDelay reserveDelay) {
        this.reservations = reservations;
        this.reserveDelay = reserveDelay;
    }

    @PostMapping
    public ReservationResponse reserve(@Valid @RequestBody ReserveStockRequest request) {
        ReservationDecision decision = reservations.reserve(request);
        if (decision.firstDecision()) {
            reserveDelay.awaitAfterCommit();
        }
        return decision.response();
    }

    @GetMapping("/{orderId}")
    public ReservationResponse get(@PathVariable UUID orderId) {
        return reservations.getByOrderId(orderId);
    }
}
