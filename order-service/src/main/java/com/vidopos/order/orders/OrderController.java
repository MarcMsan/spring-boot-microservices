package com.vidopos.order.orders;

import com.vidopos.order.web.Correlation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderSubmissionService orders;

    public OrderController(OrderSubmissionService orders) {
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletResponse servletResponse) {
        OrderSubmission submission = orders.submit(idempotencyKey, request);
        servletResponse.setHeader(Correlation.HEADER, submission.order().correlationId());
        HttpStatus status = switch (submission.outcome()) {
            case CREATED_PENDING -> HttpStatus.ACCEPTED;
            case CREATED_FINAL -> HttpStatus.CREATED;
            case REPLAYED -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(submission.order());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id, HttpServletResponse servletResponse) {
        OrderResponse order = orders.get(id);
        servletResponse.setHeader(Correlation.HEADER, order.correlationId());
        return order;
    }
}
