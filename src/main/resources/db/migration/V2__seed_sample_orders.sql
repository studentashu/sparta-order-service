-- =====================================================================
-- Order Service — Sample data for local/manual testing (e.g. Postman)
-- =====================================================================

-- Order for customer 1001: 2 items, PENDING
WITH new_order AS (
    INSERT INTO orders (customer_id, customer_name, customer_email, shipping_address, status, total_amount)
    VALUES (1001, 'Alice Johnson', 'alice.johnson@example.com', '12 Baker Street, London, NW1 6XE', 'PENDING', 141.48)
    RETURNING order_id
)
INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity)
SELECT order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity
FROM new_order
CROSS JOIN (VALUES
    (501, 'Wireless Mouse', 25.99, 2),
    (502, 'Mechanical Keyboard', 89.50, 1)
) AS items(product_id, product_name_snapshot, unit_price_snapshot, quantity);

-- Order for customer 1002: 1 item, CONFIRMED
WITH new_order AS (
    INSERT INTO orders (customer_id, customer_name, customer_email, shipping_address, status, total_amount)
    VALUES (1002, 'Bob Smith', 'bob.smith@example.com', '45 Elm Street, Manchester, M1 2WD', 'CONFIRMED', 29.97)
    RETURNING order_id
)
INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity)
SELECT order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity
FROM new_order
CROSS JOIN (VALUES
    (601, 'USB-C Cable', 9.99, 3)
) AS items(product_id, product_name_snapshot, unit_price_snapshot, quantity);

-- Second order for customer 1001: 2 items, SHIPPED
WITH new_order AS (
    INSERT INTO orders (customer_id, customer_name, customer_email, shipping_address, status, total_amount)
    VALUES (1001, 'Alice Johnson', 'alice.johnson@example.com', '12 Baker Street, London, NW1 6XE', 'SHIPPED', 105.00)
    RETURNING order_id
)
INSERT INTO order_items (order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity)
SELECT order_id, product_id, product_name_snapshot, unit_price_snapshot, quantity
FROM new_order
CROSS JOIN (VALUES
    (701, 'Laptop Stand', 45.00, 1),
    (702, 'Webcam', 60.00, 1)
) AS items(product_id, product_name_snapshot, unit_price_snapshot, quantity);
