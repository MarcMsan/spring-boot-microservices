package com.vidopos.order.orders;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    @Id
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public static CustomerOrder pending(
            String idempotencyKey,
            String requestHash,
            String customerEmail,
            String correlationId,
            Instant now) {
        CustomerOrder order = new CustomerOrder();
        order.id = UUID.randomUUID();
        order.idempotencyKey = idempotencyKey;
        order.requestHash = requestHash;
        order.customerEmail = customerEmail;
        order.status = OrderStatus.PENDING;
        order.correlationId = correlationId;
        order.createdAt = now;
        order.updatedAt = now;
        return order;
    }

    public void addItem(UUID productId, int quantity) {
        items.add(new OrderItem(this, productId, quantity));
    }

    public void confirm(BigDecimal total, Instant now) {
        this.status = OrderStatus.CONFIRMED;
        this.total = total;
        this.rejectionReason = null;
        this.updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        this.status = OrderStatus.REJECTED;
        this.rejectionReason = reason;
        this.total = null;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }
}
