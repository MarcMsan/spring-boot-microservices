CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    available_quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_products_name_present CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_products_price_non_negative CHECK (price >= 0),
    CONSTRAINT ck_products_quantity_non_negative CHECK (available_quantity >= 0)
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_reservations_order_id UNIQUE (order_id),
    CONSTRAINT ck_reservations_status CHECK (status IN ('RESERVED', 'REJECTED'))
);

CREATE TABLE reservation_items (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations (id),
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2),
    CONSTRAINT uq_reservation_items_product UNIQUE (reservation_id, product_id),
    CONSTRAINT ck_reservation_items_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX ix_reservation_items_reservation ON reservation_items (reservation_id);
