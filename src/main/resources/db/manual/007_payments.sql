-- MySQL 8.0.16+. Apply using the checked migration helper documented in README.
-- Existing bookings retain their status and get payment_status='UNPAID'. No payment is invented.
-- The helper adds these columns only when absent, and replaces the named CHECK atomically.
ALTER TABLE stay_bookings ADD COLUMN payment_status VARCHAR(16) NOT NULL DEFAULT 'UNPAID';
ALTER TABLE stay_bookings ADD COLUMN expires_at DATETIME(6) NULL;
ALTER TABLE stay_bookings DROP CHECK ck_stay_booking_values,
    ADD CONSTRAINT ck_stay_booking_values CHECK (quantity BETWEEN 1 AND 20 AND guests > 0 AND total >= 0 AND check_out > check_in AND status IN ('PENDING_PAYMENT','CONFIRMED','CANCELLED','EXPIRED'));
CREATE TABLE IF NOT EXISTS stay_payment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    attempt_key VARCHAR(36) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    session_id VARCHAR(255) NULL,
    session_url VARCHAR(2048) NULL,
    payment_intent_id VARCHAR(255) NULL,
    requested_expires_at DATETIME(6) NOT NULL,
    cancellation_requested BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    paid_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stay_payment_booking UNIQUE (booking_id),
    CONSTRAINT uk_stay_payment_key UNIQUE (attempt_key),
    CONSTRAINT uk_stay_payment_session UNIQUE (session_id),
    CONSTRAINT fk_stay_payment_booking FOREIGN KEY (booking_id) REFERENCES stay_bookings(id) ON DELETE RESTRICT,
    CONSTRAINT ck_stay_payment_amount CHECK (amount_minor >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS stay_payment_events (
    id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
