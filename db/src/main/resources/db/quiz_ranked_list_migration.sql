-- Run once on existing MySQL databases before deploying Ranked List questions.
ALTER TABLE quiz_questions MODIFY question_type ENUM('LIST', 'CHRONOLOGY', 'RANKED_LIST') NOT NULL, ADD COLUMN IF NOT EXISTS last_synced_at DATE NULL AFTER prompt;

ALTER TABLE quiz_question_audits
    MODIFY question_type ENUM('LIST', 'CHRONOLOGY', 'RANKED_LIST') NOT NULL;

ALTER TABLE quiz_question_decline_feedback
    MODIFY question_type ENUM('LIST', 'CHRONOLOGY', 'RANKED_LIST') NOT NULL;
