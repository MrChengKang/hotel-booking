-- STAY bookings. Requires stay_users, stay_hotels, stay_room_types and stay_room_inventory.
-- No DROP/ALTER statements or seed data. MySQL 8.0.16+.
CREATE TABLE IF NOT EXISTS stay_bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference VARCHAR(36) NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    guest_id BIGINT NOT NULL,
    room_type_id BIGINT NOT NULL,
    hotel_name VARCHAR(160) NOT NULL,
    room_name VARCHAR(120) NOT NULL,
    hotel_time_zone VARCHAR(64) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    check_in DATE NOT NULL,
    check_out DATE NOT NULL,
    quantity INT NOT NULL,
    guests INT NOT NULL,
    total DECIMAL(16,2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    created_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_stay_booking_reference UNIQUE (reference),
    CONSTRAINT uk_stay_booking_request UNIQUE (guest_id, request_key),
    CONSTRAINT fk_stay_booking_guest FOREIGN KEY (guest_id) REFERENCES stay_users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stay_booking_room FOREIGN KEY (room_type_id) REFERENCES stay_room_types(id) ON DELETE RESTRICT,
    CONSTRAINT ck_stay_booking_values CHECK (quantity BETWEEN 1 AND 20 AND guests > 0 AND total >= 0 AND check_out > check_in AND status IN ('CONFIRMED','CANCELLED')),
    INDEX ix_stay_booking_guest (guest_id, created_at),
    INDEX ix_stay_booking_room (room_type_id, check_in, status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS stay_booking_nights (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    stay_date DATE NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stay_booking_night UNIQUE (booking_id, stay_date),
    CONSTRAINT fk_stay_booking_night FOREIGN KEY (booking_id) REFERENCES stay_bookings(id) ON DELETE RESTRICT,
    CONSTRAINT ck_stay_booking_night_price CHECK (unit_price >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
