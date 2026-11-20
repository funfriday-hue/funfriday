-- Run once in GameData before deploying the Sudoku catalogue and progress feature.
CREATE TABLE IF NOT EXISTS player_sudoku_progress (
    player_id VARCHAR(64) NOT NULL,
    puzzle_id INT NOT NULL,
    best_progress_percent TINYINT UNSIGNED NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    first_played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (player_id, puzzle_id),
    INDEX idx_player_sudoku_progress_puzzle (puzzle_id),
    CONSTRAINT fk_player_sudoku_progress_puzzle
        FOREIGN KEY (puzzle_id) REFERENCES sudoku_puzzles(id) ON DELETE CASCADE
);
