-- Target schema for the MySQL integration tests (runs inside the container's default database).
CREATE TABLE products (
    id        INT PRIMARY KEY,
    name      VARCHAR(80) NOT NULL,
    category  VARCHAR(40) NOT NULL,
    price     DECIMAL(10, 2) NOT NULL
) COMMENT = 'Catalogue of products for sale';

CREATE TABLE sales (
    id          INT PRIMARY KEY,
    product_id  INT NOT NULL,
    sold_on     DATE NOT NULL,
    quantity    INT NOT NULL,
    CONSTRAINT fk_sales_product FOREIGN KEY (product_id) REFERENCES products (id)
);

INSERT INTO products VALUES
    (1, 'Desk lamp', 'Home', 39.00),
    (2, 'Notebook', 'Office', 4.50),
    (3, 'Headphones', 'Audio', 129.00);
INSERT INTO sales VALUES
    (1, 1, '2026-01-04', 3),
    (2, 2, '2026-01-05', 40),
    (3, 3, '2026-02-10', 2),
    (4, 2, '2026-03-01', 25);
