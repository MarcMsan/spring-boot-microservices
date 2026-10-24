CREATE TABLE customer_orders (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    customer_email VARCHAR(254) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total NUMERIC(14, 2),
    rejection_reason TEXT,
    correlation_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uq_customer_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_customer_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED'))
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders (id),
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2),
    CONSTRAINT uq_order_items_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX ix_order_items_order ON order_items (order_id);

CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders (id),
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_outbox_order UNIQUE (order_id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'SENT')),
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX ix_outbox_due ON outbox (status, next_attempt_at);
