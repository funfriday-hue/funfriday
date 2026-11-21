-- One compact, topic-specific crossword per published puzzle. The grid and
-- entries are stored separately so the answer letters never need to be sent
-- to catalogue visitors.
CREATE TABLE IF NOT EXISTS crossword_puzzles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    puzzle_key VARCHAR(120) NOT NULL UNIQUE,
    category VARCHAR(60) NOT NULL,
    title VARCHAR(160) NOT NULL,
    width TINYINT UNSIGNED NOT NULL,
    height TINYINT UNSIGNED NOT NULL,
    grid_json JSON NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crossword_puzzles_catalogue (is_active, category, id)
);

CREATE TABLE IF NOT EXISTS crossword_entries (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    puzzle_id BIGINT UNSIGNED NOT NULL,
    clue_number SMALLINT UNSIGNED NOT NULL,
    direction ENUM('ACROSS', 'DOWN') NOT NULL,
    row_index TINYINT UNSIGNED NOT NULL,
    column_index TINYINT UNSIGNED NOT NULL,
    answer VARCHAR(80) NOT NULL,
    clue VARCHAR(400) NOT NULL,
    UNIQUE KEY uq_crossword_entry_direction (puzzle_id, clue_number, direction),
    CONSTRAINT fk_crossword_entries_puzzle FOREIGN KEY (puzzle_id) REFERENCES crossword_puzzles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS crossword_drafts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    draft_key VARCHAR(120) NOT NULL UNIQUE,
    category VARCHAR(60) NOT NULL,
    title VARCHAR(160) NOT NULL,
    width TINYINT UNSIGNED NOT NULL,
    height TINYINT UNSIGNED NOT NULL,
    grid_json JSON NOT NULL,
    entries_json JSON NOT NULL,
    model VARCHAR(120) NULL,
    status ENUM('DRAFT', 'APPROVED', 'DECLINED') NOT NULL DEFAULT 'DRAFT',
    decline_reason TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    INDEX idx_crossword_drafts_status (status, created_at)
);

CREATE TABLE IF NOT EXISTS player_crossword_progress (
    player_id VARCHAR(64) NOT NULL,
    puzzle_id BIGINT UNSIGNED NOT NULL,
    best_progress_percent TINYINT UNSIGNED NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    first_played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (player_id, puzzle_id),
    CONSTRAINT fk_player_crossword_progress_puzzle FOREIGN KEY (puzzle_id) REFERENCES crossword_puzzles(id) ON DELETE CASCADE
);
