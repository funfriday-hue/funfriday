CREATE TABLE IF NOT EXISTS logic_grid_puzzles (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  puzzle_key VARCHAR(120) NOT NULL UNIQUE,
  category VARCHAR(60) NOT NULL,
  title VARCHAR(160) NOT NULL,
  story TEXT NULL,
  entities_json JSON NOT NULL,
  attributes_json JSON NOT NULL,
  clues_json JSON NOT NULL,
  solution_json JSON NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_logic_grid_catalogue (is_active, category, id)
);
CREATE TABLE IF NOT EXISTS logic_grid_drafts LIKE logic_grid_puzzles;
-- MySQL does not support ADD COLUMN IF NOT EXISTS on all supported versions.
-- Use INFORMATION_SCHEMA checks so this migration is safe to run repeatedly.
DROP PROCEDURE IF EXISTS ensure_logic_grid_draft_columns;
DELIMITER //
CREATE PROCEDURE ensure_logic_grid_draft_columns()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logic_grid_drafts' AND COLUMN_NAME = 'status'
  ) THEN
    ALTER TABLE logic_grid_drafts ADD COLUMN status ENUM('DRAFT','APPROVED','DECLINED') NOT NULL DEFAULT 'DRAFT';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logic_grid_drafts' AND COLUMN_NAME = 'model'
  ) THEN
    ALTER TABLE logic_grid_drafts ADD COLUMN model VARCHAR(120) NULL;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logic_grid_drafts' AND COLUMN_NAME = 'decline_reason'
  ) THEN
    ALTER TABLE logic_grid_drafts ADD COLUMN decline_reason TEXT NULL;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'logic_grid_drafts' AND COLUMN_NAME = 'reviewed_at'
  ) THEN
    ALTER TABLE logic_grid_drafts ADD COLUMN reviewed_at TIMESTAMP NULL;
  END IF;
END//
DELIMITER ;
CALL ensure_logic_grid_draft_columns();
DROP PROCEDURE IF EXISTS ensure_logic_grid_draft_columns;
CREATE TABLE IF NOT EXISTS player_logic_grid_progress (
  player_id VARCHAR(64) NOT NULL, puzzle_id BIGINT UNSIGNED NOT NULL,
  best_progress_percent TINYINT UNSIGNED NOT NULL DEFAULT 0,
  is_completed BOOLEAN NOT NULL DEFAULT FALSE,
  first_played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  completed_at TIMESTAMP NULL,
  PRIMARY KEY(player_id,puzzle_id),
  CONSTRAINT fk_player_logic_grid_puzzle FOREIGN KEY(puzzle_id) REFERENCES logic_grid_puzzles(id) ON DELETE CASCADE
);
