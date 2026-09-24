CREATE TABLE IF NOT EXISTS quiz_question_decline_feedback (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    question_key VARCHAR(120) NOT NULL,
    category ENUM('CRICKET', 'WWE', 'BOLLYWOOD', 'FOOTBALL') NOT NULL,
    question_type ENUM('LIST', 'CHRONOLOGY') NOT NULL,
    prompt TEXT NOT NULL,
    decline_reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_quiz_decline_feedback_category (category, created_at)
);
