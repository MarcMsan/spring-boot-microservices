package com.vidopos.order.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxMessage {

    @Id
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "order_id", nullable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "timestamptz")
    private Instant nextAttemptAt;

    @Column(name = "locked_until", columnDefinition = "timestamptz")
    private Instant lockedUntil;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    protected OutboxMessage() {
    }

    public static OutboxMessage pending(UUID orderId, Instant now) {
        OutboxMessage message = new OutboxMessage();
        message.id = UUID.randomUUID();
        message.orderId = orderId;
        message.status = OutboxStatus.PENDING;
        message.attemptCount = 0;
        message.nextAttemptAt = now;
        message.createdAt = now;
        message.updatedAt = now;
        return message;
    }

    public void markSent(Instant now) {
        this.status = OutboxStatus.SENT;
        this.lockedUntil = null;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void reschedule(String error, Instant nextAttempt, Instant now) {
        this.status = OutboxStatus.PENDING;
        this.lockedUntil = null;
        this.lastError = truncate(error);
        this.nextAttemptAt = nextAttempt;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        String singleLine = error.replace('\n', ' ').replace('\r', ' ');
        return singleLine.length() <= 500 ? singleLine : singleLine.substring(0, 500);
    }
}
