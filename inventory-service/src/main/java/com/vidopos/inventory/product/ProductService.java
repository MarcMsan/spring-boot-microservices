package com.vidopos.inventory.product;

import com.vidopos.inventory.web.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final Clock clock;

    public ProductService(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = Product.create(
                request.name().trim(),
                money(request.price()),
                request.availableQuantity(),
                clock.instant());
        products.save(product);
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return products.findAll().stream()
                .sorted(Comparator.comparing(Product::getCreatedAt).reversed())
                .map(ProductService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return toResponse(load(id));
    }

    @Transactional
    public ProductResponse updatePrice(UUID id, UpdatePriceRequest request) {
        Product product = load(id);
        product.changePrice(money(request.price()), clock.instant());
        return toResponse(product);
    }

    private Product load(UUID id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }

    static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                money(product.getPrice()),
                product.getAvailableQuantity());
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
