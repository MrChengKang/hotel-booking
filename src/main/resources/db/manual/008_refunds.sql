-- Full pre-check-in sandbox cancellation refunds. Existing bookings are unchanged.
CREATE TABLE IF NOT EXISTS stay_refund_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    payment_intent_id VARCHAR(255) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    refund_id VARCHAR(255) NULL,
    policy_version VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_stay_refund_booking UNIQUE (booking_id),
    CONSTRAINT uk_stay_refund_key UNIQUE (request_key),
    CONSTRAINT uk_stay_refund_provider UNIQUE (refund_id),
    CONSTRAINT fk_stay_refund_booking FOREIGN KEY (booking_id) REFERENCES stay_bookings(id),
    CONSTRAINT ck_stay_refund_amount CHECK (amount_minor > 0),
    INDEX ix_stay_refund_reconcile (status, booking_id)
);
