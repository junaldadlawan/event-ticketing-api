-- A cart holding a promo code that has since been deleted could not check out (422) and could not show the code
-- to remove it. Deleting a code now clears it from carts; this clears the carts that already point at one.
UPDATE carts c SET promo_code_id = NULL
    FROM promo_codes p
    WHERE c.promo_code_id = p.id AND p.deleted_at IS NOT NULL;
