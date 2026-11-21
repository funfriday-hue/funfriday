-- Verified account profile data from Google Identity Services.
-- This data stays server-side: player_id is the stable ID used by the game,
-- while email is never included in the browser session JWT.
CREATE TABLE IF NOT EXISTS player_accounts (
    player_id VARCHAR(64) NOT NULL,
    google_subject VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    picture_url VARCHAR(1000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (player_id),
    UNIQUE KEY uk_player_accounts_google_subject (google_subject),
    KEY idx_player_accounts_email (email)
);
