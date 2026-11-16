-- Per-player best results for explicitly selected Quiz Royale catalogue questions.
-- player_id is the anonymous browser ID or stable ID derived from a verified Google account.
CREATE TABLE IF NOT EXISTS player_question_progress (
    player_id VARCHAR(64) NOT NULL,
    question_key VARCHAR(120) NOT NULL,
    best_correct_answers SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    answer_count SMALLINT UNSIGNED NOT NULL,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    first_played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (player_id, question_key),
    INDEX idx_player_question_progress_question (question_key)
);
