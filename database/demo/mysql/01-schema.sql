-- QueryAI demo e-commerce analytics database (MySQL 8)
-- Same logical model as the PostgreSQL demo so both supported dialects can be compared.

CREATE TABLE categories (
    id                  INT PRIMARY KEY,
    name                VARCHAR(80) NOT NULL,
    parent_category_id  INT NULL,
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_category_id) REFERENCES categories (id)
) COMMENT = 'Product category hierarchy. Top-level categories have a NULL parent_category_id.';

CREATE TABLE warehouses (
    id       INT PRIMARY KEY,
    name     VARCHAR(120) NOT NULL,
    city     VARCHAR(80)  NOT NULL,
    country  VARCHAR(80)  NOT NULL
) COMMENT = 'Fulfillment centers that ship orders and hold inventory.';

CREATE TABLE customers (
    id           INT PRIMARY KEY,
    first_name   VARCHAR(60)  NOT NULL,
    last_name    VARCHAR(60)  NOT NULL,
    email        VARCHAR(160) NOT NULL UNIQUE,
    city         VARCHAR(80)  NOT NULL,
    state        VARCHAR(80)  NULL,
    country      VARCHAR(80)  NOT NULL,
    segment      VARCHAR(30)  NOT NULL COMMENT 'Customer segment: Consumer, Small Business or Corporate.',
    signup_date  DATE         NOT NULL COMMENT 'Date the customer created their account.'
) COMMENT = 'Registered customers of the online store.';

CREATE TABLE products (
    id           INT PRIMARY KEY,
    sku          VARCHAR(30)    NOT NULL UNIQUE,
    name         VARCHAR(160)   NOT NULL,
    category_id  INT            NOT NULL,
    brand        VARCHAR(80)    NOT NULL,
    unit_price   DECIMAL(10, 2) NOT NULL COMMENT 'Current list price in USD.',
    unit_cost    DECIMAL(10, 2) NOT NULL COMMENT 'Landed cost per unit in USD, used for margin calculations.',
    is_active    BOOLEAN        NOT NULL,
    launched_on  DATE           NOT NULL,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
) COMMENT = 'Product catalog with current list price and unit cost.';

CREATE TABLE inventory (
    warehouse_id      INT NOT NULL,
    product_id        INT NOT NULL,
    quantity_on_hand  INT NOT NULL,
    reorder_level     INT NOT NULL COMMENT 'Stock is considered low when quantity_on_hand is below this level.',
    PRIMARY KEY (warehouse_id, product_id),
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id)
) COMMENT = 'Current stock per product per warehouse.';

CREATE TABLE orders (
    id               INT PRIMARY KEY,
    customer_id      INT            NOT NULL,
    order_date       DATETIME       NOT NULL,
    status           VARCHAR(20)    NOT NULL COMMENT 'processing, shipped, delivered, cancelled or returned. Revenue normally excludes cancelled and returned orders.',
    channel          VARCHAR(20)    NOT NULL COMMENT 'Sales channel: web, mobile_app or marketplace.',
    warehouse_id     INT            NOT NULL,
    shipping_cost    DECIMAL(10, 2) NOT NULL,
    discount_amount  DECIMAL(10, 2) NOT NULL,
    total_amount     DECIMAL(12, 2) NOT NULL COMMENT 'Amount charged in USD: sum of line totals minus order discount plus shipping.',
    INDEX ix_orders_date (order_date),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) COMMENT = 'Customer orders. One row per checkout.';

CREATE TABLE order_items (
    id             INT PRIMARY KEY,
    order_id       INT            NOT NULL,
    product_id     INT            NOT NULL,
    quantity       INT            NOT NULL,
    unit_price     DECIMAL(10, 2) NOT NULL,
    line_discount  DECIMAL(10, 2) NOT NULL,
    line_total     DECIMAL(12, 2) NOT NULL COMMENT 'quantity * unit_price - line_discount, in USD.',
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) COMMENT = 'Line items of each order with the price actually paid.';

CREATE TABLE payments (
    id              INT PRIMARY KEY,
    order_id        INT            NOT NULL,
    payment_method  VARCHAR(30)    NOT NULL,
    amount          DECIMAL(12, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    paid_at         DATETIME       NOT NULL,
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
) COMMENT = 'Payments captured for orders. Status is completed or refunded.';

CREATE TABLE product_reviews (
    id           INT PRIMARY KEY,
    product_id   INT      NOT NULL,
    customer_id  INT      NOT NULL,
    rating       SMALLINT NOT NULL,
    review_date  DATE     NOT NULL,
    CONSTRAINT ck_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) COMMENT = 'Star ratings (1-5) left by customers on delivered products.';
