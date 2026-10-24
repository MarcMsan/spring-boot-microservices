package com.vidopos.inventory.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    protected Product() {
    }

    public static Product create(String name, BigDecimal price, int availableQuantity, Instant now) {
        Product product = new Product();
        product.id = UUID.randomUUID();
        product.name = name;
        product.price = price;
        product.availableQuantity = availableQuantity;
        product.createdAt = now;
        product.updatedAt = now;
        return product;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public void changePrice(BigDecimal price, Instant now) {
        this.price = price;
        this.updatedAt = now;
    }
}
