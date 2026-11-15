-- Additive MySQL 8 schema. Development ddl-auto=update creates this automatically.
-- Existing hotels intentionally have no declared amenities until their host supplies them.
CREATE TABLE IF NOT EXISTS stay_hotel_amenities (
    hotel_id BIGINT NOT NULL,
    amenity VARCHAR(32) NOT NULL,
    PRIMARY KEY (hotel_id, amenity),
    CONSTRAINT fk_stay_hotel_amenities_hotel FOREIGN KEY (hotel_id) REFERENCES stay_hotels(id),
    CONSTRAINT ck_stay_hotel_amenity CHECK (amenity IN (
        'WIFI', 'PARKING', 'POOL', 'GYM', 'RESTAURANT',
        'AIR_CONDITIONING', 'BREAKFAST', 'ACCESSIBLE'
    ))
);
