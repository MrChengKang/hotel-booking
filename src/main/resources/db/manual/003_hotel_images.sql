-- Create-only photo metadata schema. Actual image bytes live in STAY_MEDIA_DIRECTORY.
-- Existing stay_hotels and stay_room_types must exist. No sample records.
CREATE TABLE IF NOT EXISTS stay_hotel_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    hotel_id BIGINT NOT NULL,
    room_type_id BIGINT NULL,
    storage_key VARCHAR(50) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    byte_size BIGINT NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    alt_text VARCHAR(120) NOT NULL,
    sort_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stay_images_storage UNIQUE (storage_key),
    CONSTRAINT fk_stay_images_hotel FOREIGN KEY (hotel_id) REFERENCES stay_hotels(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stay_images_room FOREIGN KEY (room_type_id) REFERENCES stay_room_types(id) ON DELETE RESTRICT,
    CONSTRAINT ck_stay_images_values CHECK (byte_size > 0 AND width > 0 AND height > 0 AND sort_order >= 0),
    INDEX ix_stay_images_scope (hotel_id, room_type_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
