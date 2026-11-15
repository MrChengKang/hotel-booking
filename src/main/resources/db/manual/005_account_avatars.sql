-- Private account avatar metadata. File bytes share STAY_MEDIA_DIRECTORY with hotel photos.
-- Create only; no existing account/profile records are changed.
CREATE TABLE IF NOT EXISTS stay_account_avatars (
    user_id BIGINT NOT NULL,
    storage_key VARCHAR(50) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_stay_avatar_storage UNIQUE (storage_key),
    CONSTRAINT fk_stay_avatar_user FOREIGN KEY (user_id) REFERENCES stay_users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
