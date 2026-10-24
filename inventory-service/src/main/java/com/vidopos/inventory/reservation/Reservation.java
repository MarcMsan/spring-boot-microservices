package com.vidopos.inventory.reservation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column
    private String reason;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId ASC")
    private List<ReservationItem> items = new ArrayList<>();

    protected Reservation() {
    }

    public static Reservation newDecision(UUID orderId, ReservationStatus status, String reason, Instant createdAt) {
        Reservation reservation = new Reservation();
        reservation.id = UUID.randomUUID();
        reservation.orderId = orderId;
        reservation.status = status;
        reservation.reason = reason;
        reservation.createdAt = createdAt;
        return reservation;
    }

    public void addItem(ReservationItem item) {
        items.add(item);
        item.assignReservation(this);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public List<ReservationItem> getItems() {
        return items;
    }
}
