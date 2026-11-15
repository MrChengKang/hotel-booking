-- Create-only STAY profile extensions and per-account hotel favorites. No sample records.
CREATE TABLE IF NOT EXISTS stay_account_profiles (
    user_id BIGINT NOT NULL,
    phone VARCHAR(30) NOT NULL,
    city VARCHAR(100) NOT NULL,
    bio VARCHAR(500) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_stay_profile_user FOREIGN KEY (user_id) REFERENCES stay_users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS stay_favorite_hotels (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    hotel_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stay_favorite_user_hotel UNIQUE (user_id, hotel_id),
    CONSTRAINT fk_stay_favorite_user FOREIGN KEY (user_id) REFERENCES stay_users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stay_favorite_hotel FOREIGN KEY (hotel_id) REFERENCES stay_hotels(id) ON DELETE RESTRICT,
    INDEX ix_stay_favorite_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
