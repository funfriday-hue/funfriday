-- Run once on existing databases before deploying persisted duplicate-question detection.
ALTER TABLE quiz_questions
    ADD COLUMN similar_question_id BIGINT UNSIGNED NULL AFTER last_synced_at,
    ADD COLUMN similarity_score DECIMAL(5,4) NULL AFTER similar_question_id,
    ADD CONSTRAINT fk_quiz_questions_similar_question
        FOREIGN KEY (similar_question_id) REFERENCES quiz_questions(id) ON DELETE SET NULL;
