WITH ranked_addresses AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY user_id
               ORDER BY default_address DESC, created_at ASC, id ASC
           ) AS address_rank
    FROM addresses
)
UPDATE addresses AS address
SET default_address = (ranked_addresses.address_rank = 1)
FROM ranked_addresses
WHERE address.id = ranked_addresses.id;

CREATE UNIQUE INDEX uk_address_single_default_per_user
    ON addresses(user_id)
    WHERE default_address = TRUE;
