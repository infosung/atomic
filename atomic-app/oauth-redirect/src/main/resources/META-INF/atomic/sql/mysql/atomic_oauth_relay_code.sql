CREATE TABLE IF NOT EXISTS atomic_oauth_relay_code (
  relay_code VARCHAR(255) NOT NULL,
  payload_json TEXT NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (relay_code),
  KEY idx_atomic_oauth_relay_code_expires_at (expires_at)
) ENGINE=InnoDB;
