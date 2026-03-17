CREATE TABLE IF NOT EXISTS image (
  id VARCHAR(255) PRIMARY KEY,
  bucket VARCHAR(255) NOT NULL,
  service_name VARCHAR(255) NOT NULL,
  storage_service VARCHAR(255) NOT NULL,
  status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
  uploader_id VARCHAR(255) NULL,
  storage_type VARCHAR(255) NOT NULL,
  file_name TEXT NULL,
  thumbnail_file_name TEXT NULL,
  url TEXT NOT NULL,
  thumbnail_url TEXT NULL,
  width INTEGER NULL,
  height INTEGER NULL,
  file_size BIGINT NOT NULL,
  thumbnail_width INTEGER NULL,
  thumbnail_height INTEGER NULL,
  thumbnail_file_size BIGINT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_image_service_storage
  ON image (service_name, storage_service);
