CREATE TABLE IF NOT EXISTS admin_passwords (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_admin_password_label (label)
);
