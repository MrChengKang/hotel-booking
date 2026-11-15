-- Create-only review and notification schema. No historical/sample events are inserted.
CREATE TABLE IF NOT EXISTS stay_hotel_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    rating INT NOT NULL,
    comment VARCHAR(2000) NOT NULL,
    host_reply VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    replied_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stay_review_booking UNIQUE (booking_id),
    CONSTRAINT fk_stay_review_booking FOREIGN KEY (booking_id) REFERENCES stay_bookings(id) ON DELETE RESTRICT,
    CONSTRAINT ck_stay_review_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS stay_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT NOT NULL,
    event_key VARCHAR(100) NOT NULL,
    title VARCHAR(60) NOT NULL,
    message VARCHAR(500) NOT NULL,
    target VARCHAR(180) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stay_notification_event UNIQUE (recipient_id, event_key),
    INDEX ix_stay_notification_inbox (recipient_id, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
-- recipient_id is bound by the server, intentionally without a physical users FK.
-- Transactional event insertion must not acquire recipient user locks after booking/hotel locks.
-- Future account deletion must explicitly delete notifications for that recipient.
