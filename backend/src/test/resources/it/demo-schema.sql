-- Small target "business" schema used by integration tests. Lives in a separate schema of the test database so it
-- never interferes with QueryAI's own Flyway-managed tables in "public".
CREATE SCHEMA demo;

CREATE TABLE demo.customers (
    id       INTEGER PRIMARY KEY,
    name     VARCHAR(80) NOT NULL,
    email    VARCHAR(120) NOT NULL,
    segment  VARCHAR(30) NOT NULL
);
COMMENT ON TABLE demo.customers IS 'Registered customers';

CREATE TABLE demo.orders (
    id            INTEGER PRIMARY KEY,
    customer_id   INTEGER NOT NULL REFERENCES demo.customers (id),
    order_date    DATE NOT NULL,
    status        VARCHAR(20) NOT NULL,
    total_amount  NUMERIC(10, 2) NOT NULL
);
COMMENT ON TABLE demo.orders IS 'Purchases with their delivery status and order value';
CREATE INDEX ix_demo_orders_customer ON demo.orders (customer_id);

INSERT INTO demo.customers VALUES
    (1, 'Ada', 'ada@example.com', 'Consumer'),
    (2, 'Grace', 'grace@example.com', 'Corporate'),
    (3, 'Linus', 'linus@example.com', 'Consumer');
INSERT INTO demo.orders VALUES
    (1, 1, DATE '2025-03-01', 'delivered', 120.00),
    (2, 1, DATE '2026-02-11', 'delivered', 80.50),
    (3, 2, DATE '2026-04-20', 'delivered', 300.00),
    (4, 3, DATE '2026-05-05', 'cancelled', 45.00),
    (5, 2, DATE '2025-09-09', 'returned', 60.00);
