CREATE TABLE atomic_oauth_relay_code (
  relay_code VARCHAR2(255 CHAR) PRIMARY KEY,
  payload_json CLOB NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_atomic_oauth_relay_code_expires_at
  ON atomic_oauth_relay_code (expires_at);
