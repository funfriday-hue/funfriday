CREATE TABLE sudoku_puzzles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    size TINYINT NOT NULL,
    grid VARCHAR(81) NOT NULL,
    solution VARCHAR(81) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_size (size)
);