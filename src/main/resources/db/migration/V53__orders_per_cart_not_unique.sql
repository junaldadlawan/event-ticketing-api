-- A cart can now be paid in parts (a selection of its items per checkout), so one cart may have several orders. The unique index from V11 allowed
-- only one; it becomes a plain index (the lookup by cart stays fast). Double payment of the same items is still prevented by the cart row lock and
-- by the items being deleted when sold.
DROP INDEX IF EXISTS uq_orders_cart_id;
CREATE INDEX IF NOT EXISTS idx_orders_cart_id ON orders (cart_id) WHERE cart_id IS NOT NULL;
