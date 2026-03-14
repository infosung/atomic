CREATE TABLE IF NOT EXISTS service_version (
  id BIGSERIAL PRIMARY KEY,
  main_version INTEGER NOT NULL,
  minor_version INTEGER NOT NULL,
  patch_number INTEGER NOT NULL,
  require_update BOOLEAN NOT NULL DEFAULT FALSE,
  platform VARCHAR(255) NOT NULL,
  service VARCHAR(255) NOT NULL,
  store_url VARCHAR(255) NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_service_version_service_platform_version
  ON service_version (service, platform, main_version DESC, minor_version DESC, patch_number DESC);
