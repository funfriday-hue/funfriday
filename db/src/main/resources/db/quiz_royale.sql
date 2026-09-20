CREATE TABLE IF NOT EXISTS quiz_questions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    question_key VARCHAR(120) NOT NULL UNIQUE,
    category ENUM('CRICKET', 'WWE', 'BOLLYWOOD', 'FOOTBALL') NOT NULL,
    question_type ENUM('LIST', 'CHRONOLOGY') NOT NULL,
    prompt TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_quiz_questions_category_type (category, question_type, is_active)
);

CREATE TABLE IF NOT EXISTS quiz_answers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT UNSIGNED NOT NULL,
    canonical_answer VARCHAR(255) NOT NULL,
    display_order INT NOT NULL,
    hint VARCHAR(255) NULL,
    CONSTRAINT fk_quiz_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    UNIQUE KEY uq_quiz_answer_order (question_id, display_order)
);

CREATE TABLE IF NOT EXISTS quiz_answer_aliases (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    answer_id BIGINT UNSIGNED NOT NULL,
    alias VARCHAR(255) NOT NULL,
    CONSTRAINT fk_quiz_answer_aliases_answer FOREIGN KEY (answer_id) REFERENCES quiz_answers(id) ON DELETE CASCADE,
    UNIQUE KEY uq_quiz_answer_alias (answer_id, alias)
);

INSERT INTO quiz_questions (question_key, category, question_type, prompt)
VALUES
    ('cricket-2011-world-cup-squad', 'CRICKET', 'LIST', 'Name players from India''s 2011 Cricket World Cup-winning squad.'),
    ('cricket-world-cup-winners-reverse', 'CRICKET', 'CHRONOLOGY', 'Name the winner of every ICC Men''s Cricket World Cup in reverse chronological order.')
ON DUPLICATE KEY UPDATE
    category = VALUES(category), question_type = VALUES(question_type), prompt = VALUES(prompt), is_active = TRUE;

INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'MS Dhoni', 1, NULL FROM quiz_questions WHERE question_key = 'cricket-2011-world-cup-squad'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Virat Kohli', 2, NULL FROM quiz_questions WHERE question_key = 'cricket-2011-world-cup-squad'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Yuvraj Singh', 3, NULL FROM quiz_questions WHERE question_key = 'cricket-2011-world-cup-squad'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Sachin Tendulkar', 4, NULL FROM quiz_questions WHERE question_key = 'cricket-2011-world-cup-squad'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Gautam Gambhir', 5, NULL FROM quiz_questions WHERE question_key = 'cricket-2011-world-cup-squad'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);

INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Australia', 1, '2023' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'England', 2, '2019' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Australia', 3, '2015' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'India', 4, '2011' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Australia', 5, '2007' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Australia', 6, '2003' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Australia', 7, '1999' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Sri Lanka', 8, '1996' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Pakistan', 9, '1992' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'Australia', 10, '1987' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'India', 11, '1983' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'West Indies', 12, '1979' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);
INSERT INTO quiz_answers (question_id, canonical_answer, display_order, hint)
SELECT id, 'West Indies', 13, '1975' FROM quiz_questions WHERE question_key = 'cricket-world-cup-winners-reverse'
ON DUPLICATE KEY UPDATE canonical_answer = VALUES(canonical_answer), hint = VALUES(hint);

INSERT INTO quiz_answer_aliases (answer_id, alias)
SELECT qa.id, aliases.alias
FROM quiz_answers qa
JOIN quiz_questions qq ON qq.id = qa.question_id
JOIN (
    SELECT 'cricket-2011-world-cup-squad' AS question_key, 1 AS display_order, 'Dhoni' AS alias
    UNION ALL SELECT 'cricket-2011-world-cup-squad', 1, 'M S Dhoni'
    UNION ALL SELECT 'cricket-2011-world-cup-squad', 1, 'Mahendra Singh Dhoni'
    UNION ALL SELECT 'cricket-2011-world-cup-squad', 2, 'Kohli'
    UNION ALL SELECT 'cricket-2011-world-cup-squad', 2, 'Kolhi'
    UNION ALL SELECT 'cricket-2011-world-cup-squad', 3, 'Yuvraj'
    UNION ALL SELECT 'cricket-2011-world-cup-squad', 4, 'Sachin'
    UNION ALL SELECT 'cricket-2011-world-cup-squad', 4, 'Tendulkar'
    UNION ALL SELECT 'cricket-2011-world-cup-squad', 5, 'Gambhir'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 1, 'AUS'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 2, 'ENG'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 3, 'AUS'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 4, 'IND'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 5, 'AUS'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 6, 'AUS'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 7, 'AUS'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 8, 'Srilanka'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 8, 'SL'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 9, 'PAK'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 10, 'AUS'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 11, 'IND'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 12, 'WestIndies'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 12, 'WI'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 12, 'Windies'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 13, 'WestIndies'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 13, 'WI'
    UNION ALL SELECT 'cricket-world-cup-winners-reverse', 13, 'Windies'
) aliases ON aliases.question_key = qq.question_key AND aliases.display_order = qa.display_order
ON DUPLICATE KEY UPDATE alias = VALUES(alias);
