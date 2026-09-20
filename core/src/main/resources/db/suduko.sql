CREATE TABLE sudoku_puzzles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    size TINYINT NOT NULL,                 -- 6 for 6x6, 9 for 9x9
    grid VARCHAR(81) NOT NULL,             -- The starting puzzle configuration (0s are empty cells)
    solution VARCHAR(81) NOT NULL,         -- The matching solution matrix configuration
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Fast execution index when querying by puzzle grid size
    INDEX idx_size (size)
);

ALTER TABLE sudoku_puzzles
ADD UNIQUE KEY uk_grid(grid);

ALTER TABLE sudoku_puzzles
ADD COLUMN difficulty ENUM('easy','medium','hard','expert')
DEFAULT 'medium';