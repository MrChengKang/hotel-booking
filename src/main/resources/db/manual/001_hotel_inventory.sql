-- STAY hotel inventory schema for MySQL 8.0.16+ (enforced CHECK constraints).
-- Existing stay_users must exist. No sample data, DROP, or ALTER statements.
-- This file is manual; Spring Boot does not execute it automatically.

CREATE TABLE IF NOT EXISTS stay_hotels (
    id BIGINT NOT NULL AUTO_INCREMENT,
    host_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    address VARCHAR(500) NOT NULL,
    city VARCHAR(100) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_stay_hotels_slug UNIQUE (slug),
    CONSTRAINT fk_stay_hotels_host FOREIGN KEY (host_id) REFERENCES stay_users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_stay_hotels_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    INDEX ix_stay_hotels_host (host_id),
    INDEX ix_stay_hotels_destination (country_code, city, status)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS stay_room_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hotel_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    max_guests INT NOT NULL,
    beds INT NOT NULL,
    total_rooms INT NOT NULL,
    base_price DECIMAL(12,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_stay_room_type_code UNIQUE (hotel_id, code),
    CONSTRAINT fk_stay_room_types_hotel FOREIGN KEY (hotel_id) REFERENCES stay_hotels(id) ON DELETE RESTRICT,
    CONSTRAINT ck_stay_room_type_values CHECK (max_guests > 0 AND beds > 0 AND total_rooms > 0 AND base_price >= 0),
    INDEX ix_stay_room_types_hotel (hotel_id, active)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS stay_room_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_type_id BIGINT NOT NULL,
    stay_date DATE NOT NULL,
    total_rooms INT NOT NULL,
    booked_rooms INT NOT NULL DEFAULT 0,
    blocked_rooms INT NOT NULL DEFAULT 0,
    nightly_price DECIMAL(12,2) NOT NULL,
    stop_sell BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_stay_inventory_room_date UNIQUE (room_type_id, stay_date),
    CONSTRAINT fk_stay_inventory_room_type FOREIGN KEY (room_type_id) REFERENCES stay_room_types(id) ON DELETE RESTRICT,
    CONSTRAINT ck_stay_inventory_values CHECK (
        total_rooms >= 0 AND booked_rooms >= 0 AND blocked_rooms >= 0
        AND booked_rooms + blocked_rooms <= total_rooms AND nightly_price >= 0
    ),
    INDEX ix_stay_inventory_date (stay_date, room_type_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
