CREATE TABLE service_version (
  id BIGSERIAL PRIMARY KEY,
  main_version INTEGER NOT NULL,
  minor_version INTEGER NOT NULL,
  patch_number INTEGER NOT NULL,
  require_update BOOLEAN NOT NULL DEFAULT FALSE,
  store_available BOOLEAN NOT NULL DEFAULT TRUE,
  platform VARCHAR(255) NOT NULL,
  service VARCHAR(255) NOT NULL,
  store_url VARCHAR(2048) NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_service_version_service_platform_semver
    UNIQUE (service, platform, main_version, minor_version, patch_number)
);
