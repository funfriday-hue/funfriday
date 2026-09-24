CREATE TABLE IF NOT EXISTS quiz_question_audits (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT UNSIGNED NOT NULL,
    question_key VARCHAR(120) NOT NULL,
    category ENUM('CRICKET', 'WWE', 'BOLLYWOOD', 'FOOTBALL') NOT NULL,
    question_type ENUM('LIST', 'CHRONOLOGY') NOT NULL,
    prompt TEXT NOT NULL,
    model VARCHAR(120) NULL,
    status ENUM('PENDING', 'ACCEPTED', 'DECLINED', 'CORRECT') NOT NULL DEFAULT 'PENDING',
    suggestions_json JSON NOT NULL,
    decline_reason TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    INDEX idx_quiz_audits_status_created (status, created_at),
    INDEX idx_quiz_audits_category_status (category, status)
);
