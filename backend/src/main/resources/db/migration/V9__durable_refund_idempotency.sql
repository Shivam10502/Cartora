ALTER TABLE payment_refund ADD COLUMN idempotency_key VARCHAR(40);

UPDATE payment_refund SET idempotency_key = receipt WHERE idempotency_key IS NULL;

ALTER TABLE payment_refund ALTER COLUMN idempotency_key SET NOT NULL;
ALTER TABLE payment_refund ADD CONSTRAINT uk_refund_idempotency_key UNIQUE (idempotency_key);
