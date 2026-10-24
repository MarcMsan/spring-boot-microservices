package com.vidopos.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

final class InventoryDouble {

    enum Behavior {
        RESERVE,
        REJECT,
        UNAVAILABLE
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile Behavior behavior = Behavior.RESERVE;
    private volatile long delayMs;

    void reset() {
        behavior = Behavior.RESERVE;
        delayMs = 0;
        calls.set(0);
        requests.clear();
    }

    void setBehavior(Behavior behavior) {
        this.behavior = behavior;
    }

    void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    int calls() {
        return calls.get();
    }

    List<RecordedRequest> requests() {
        return requests;
    }

    Dispatcher dispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                calls.incrementAndGet();
                requests.add(request);
                long delay = delayMs;
                Behavior selected = behavior;
                try {
                    if (selected == Behavior.UNAVAILABLE) {
                        return delayed(new MockResponse()
                                .setResponseCode(503)
                                .setHeader("Content-Type", "application/json")
                                .setBody("{\"message\":\"unavailable\"}"), delay);
                    }
                    JsonNode root = mapper.readTree(request.getBody().readUtf8());
                    ObjectNode body = mapper.createObjectNode();
                    body.put("orderId", root.get("orderId").asText());
                    if (selected == Behavior.REJECT) {
                        body.put("status", "REJECTED");
                        body.put("reason", "Insufficient stock for product");
                        body.putArray("items");
                    } else {
                        body.put("status", "RESERVED");
                        body.putNull("reason");
                        ArrayNode items = body.putArray("items");
                        for (JsonNode item : root.get("items")) {
                            ObjectNode priced = items.addObject();
                            priced.put("productId", item.get("productId").asText());
                            priced.put("quantity", item.get("quantity").asInt());
                            priced.put("unitPrice", new BigDecimal("10.00"));
                        }
                    }
                    return delayed(new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(mapper.writeValueAsString(body)), delay);
                } catch (Exception exception) {
                    return new MockResponse().setResponseCode(500).setBody("{\"message\":\"inventory double failed\"}");
                }
            }
        };
    }

    private static MockResponse delayed(MockResponse response, long delay) {
        if (delay > 0) {
            response.setHeadersDelay(delay, TimeUnit.MILLISECONDS);
        }
        return response;
    }
}
