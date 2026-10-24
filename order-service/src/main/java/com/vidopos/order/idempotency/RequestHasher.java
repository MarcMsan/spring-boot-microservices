package com.vidopos.order.idempotency;

import com.vidopos.order.orders.CreateOrderRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;

public final class RequestHasher {

    private RequestHasher() {
    }

    public static String hash(CreateOrderRequest request) {
        String canonical = canonical(request);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    static String canonical(CreateOrderRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append(request.customerEmail().trim().toLowerCase(Locale.ROOT)).append('\n');
        request.items().stream()
                .sorted(Comparator.comparing(CreateOrderRequest.Item::productId))
                .forEach(item -> builder.append(item.productId()).append(':').append(item.quantity()).append('\n'));
        return builder.toString();
    }
}
