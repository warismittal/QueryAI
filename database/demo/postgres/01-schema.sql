-- QueryAI demo e-commerce analytics database (PostgreSQL)
-- This is a *target* business database that QueryAI connects to. It is separate from the QueryAI application database.

CREATE TABLE categories (
    id                  INTEGER PRIMARY KEY,
    name                VARCHAR(80) NOT NULL,
    parent_category_id  INTEGER REFERENCES categories (id)
);
COMMENT ON TABLE categories IS 'Product category hierarchy. Top-level categories have a NULL parent_category_id.';

CREATE TABLE warehouses (
    id       INTEGER PRIMARY KEY,
    name     VARCHAR(120) NOT NULL,
    city     VARCHAR(80)  NOT NULL,
    country  VARCHAR(80)  NOT NULL
);
COMMENT ON TABLE warehouses IS 'Fulfillment centers that ship orders and hold inventory.';

CREATE TABLE customers (
    id           INTEGER PRIMARY KEY,
    first_name   VARCHAR(60)  NOT NULL,
    last_name    VARCHAR(60)  NOT NULL,
    email        VARCHAR(160) NOT NULL UNIQUE,
    city         VARCHAR(80)  NOT NULL,
    state        VARCHAR(80),
    country      VARCHAR(80)  NOT NULL,
    segment      VARCHAR(30)  NOT NULL,
    signup_date  DATE         NOT NULL
);
COMMENT ON TABLE customers IS 'Registered customers of the online store.';
COMMENT ON COLUMN customers.segment IS 'Customer segment: Consumer, Small Business or Corporate.';
COMMENT ON COLUMN customers.signup_date IS 'Date the customer created their account.';

CREATE TABLE products (
    id           INTEGER PRIMARY KEY,
    sku          VARCHAR(30)    NOT NULL UNIQUE,
    name         VARCHAR(160)   NOT NULL,
    category_id  INTEGER        NOT NULL REFERENCES categories (id),
    brand        VARCHAR(80)    NOT NULL,
    unit_price   NUMERIC(10, 2) NOT NULL,
    unit_cost    NUMERIC(10, 2) NOT NULL,
    is_active    BOOLEAN        NOT NULL,
    launched_on  DATE           NOT NULL
);
COMMENT ON TABLE products IS 'Product catalog with current list price and unit cost.';
COMMENT ON COLUMN products.unit_price IS 'Current list price in USD.';
COMMENT ON COLUMN products.unit_cost IS 'Landed cost per unit in USD, used for margin calculations.';

CREATE TABLE inventory (
    warehouse_id      INTEGER NOT NULL REFERENCES warehouses (id),
    product_id        INTEGER NOT NULL REFERENCES products (id),
    quantity_on_hand  INTEGER NOT NULL,
    reorder_level     INTEGER NOT NULL,
    PRIMARY KEY (warehouse_id, product_id)
);
COMMENT ON TABLE inventory IS 'Current stock per product per warehouse.';
COMMENT ON COLUMN inventory.reorder_level IS 'Stock is considered low when quantity_on_hand is below this level.';

CREATE TABLE orders (
    id               INTEGER PRIMARY KEY,
    customer_id      INTEGER        NOT NULL REFERENCES customers (id),
    order_date       TIMESTAMP      NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    channel          VARCHAR(20)    NOT NULL,
    warehouse_id     INTEGER        NOT NULL REFERENCES warehouses (id),
    shipping_cost    NUMERIC(10, 2) NOT NULL,
    discount_amount  NUMERIC(10, 2) NOT NULL,
    total_amount     NUMERIC(12, 2) NOT NULL
);
COMMENT ON TABLE orders IS 'Customer orders. One row per checkout.';
COMMENT ON COLUMN orders.status IS 'processing, shipped, delivered, cancelled or returned. Revenue normally excludes cancelled and returned orders.';
COMMENT ON COLUMN orders.channel IS 'Sales channel: web, mobile_app or marketplace.';
COMMENT ON COLUMN orders.total_amount IS 'Amount charged in USD: sum of line totals minus order discount plus shipping.';
CREATE INDEX ix_orders_customer ON orders (customer_id);
CREATE INDEX ix_orders_date ON orders (order_date);

CREATE TABLE order_items (
    id             INTEGER PRIMARY KEY,
    order_id       INTEGER        NOT NULL REFERENCES orders (id),
    product_id     INTEGER        NOT NULL REFERENCES products (id),
    quantity       INTEGER        NOT NULL,
    unit_price     NUMERIC(10, 2) NOT NULL,
    line_discount  NUMERIC(10, 2) NOT NULL,
    line_total     NUMERIC(12, 2) NOT NULL
);
COMMENT ON TABLE order_items IS 'Line items of each order with the price actually paid.';
COMMENT ON COLUMN order_items.line_total IS 'quantity * unit_price - line_discount, in USD.';
CREATE INDEX ix_order_items_order ON order_items (order_id);
CREATE INDEX ix_order_items_product ON order_items (product_id);

CREATE TABLE payments (
    id              INTEGER PRIMARY KEY,
    order_id        INTEGER        NOT NULL REFERENCES orders (id),
    payment_method  VARCHAR(30)    NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    paid_at         TIMESTAMP      NOT NULL
);
COMMENT ON TABLE payments IS 'Payments captured for orders. Status is completed or refunded.';
CREATE INDEX ix_payments_order ON payments (order_id);

CREATE TABLE product_reviews (
    id           INTEGER PRIMARY KEY,
    product_id   INTEGER  NOT NULL REFERENCES products (id),
    customer_id  INTEGER  NOT NULL REFERENCES customers (id),
    rating       SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    review_date  DATE     NOT NULL
);
COMMENT ON TABLE product_reviews IS 'Star ratings (1-5) left by customers on delivered products.';
CREATE INDEX ix_product_reviews_product ON product_reviews (product_id);
