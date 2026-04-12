CREATE TABLE image (
  id VARCHAR2(255 CHAR) PRIMARY KEY,
  bucket VARCHAR2(255 CHAR) NOT NULL,
  service_name VARCHAR2(255 CHAR) NOT NULL,
  storage_service VARCHAR2(255 CHAR) NOT NULL,
  status VARCHAR2(255 CHAR) DEFAULT 'ACTIVE' NOT NULL,
  uploader_id VARCHAR2(255 CHAR) NULL,
  storage_type VARCHAR2(255 CHAR) NOT NULL,
  file_name CLOB NULL,
  thumbnail_file_name CLOB NULL,
  url CLOB NOT NULL,
  thumbnail_url CLOB NULL,
  width NUMBER(10) NULL,
  height NUMBER(10) NULL,
  file_size NUMBER(19) NOT NULL,
  thumbnail_width NUMBER(10) NULL,
  thumbnail_height NUMBER(10) NULL,
  thumbnail_file_size NUMBER(19) NULL,
  delete_recovery_claim_token VARCHAR2(255 CHAR) NULL,
  delete_recovery_claimed_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_image_service_storage
  ON image (service_name, storage_service);

CREATE INDEX idx_image_status_created_at
  ON image (status, created_at);

CREATE INDEX idx_image_status_claim_created_at
  ON image (status, delete_recovery_claim_token, created_at);
