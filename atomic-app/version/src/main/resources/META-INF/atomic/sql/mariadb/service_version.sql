CREATE TABLE IF NOT EXISTS service_version (
  id BIGINT NOT NULL AUTO_INCREMENT,
  main_version INT NOT NULL,
  minor_version INT NOT NULL,
  patch_number INT NOT NULL,
  require_update BOOLEAN NOT NULL DEFAULT FALSE,
  store_available BOOLEAN NOT NULL DEFAULT TRUE,
  platform VARCHAR(255) NOT NULL,
  service VARCHAR(255) NOT NULL,
  store_url TEXT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_service_version_service_platform_semver
    UNIQUE (service, platform, main_version, minor_version, patch_number),
  KEY idx_service_version_service_platform_required_update (
    service,
    platform,
    require_update,
    main_version,
    minor_version,
    patch_number
  )
) ENGINE=InnoDB;
