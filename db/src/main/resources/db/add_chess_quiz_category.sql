-- Run once in GameData before deploying Chess Quiz.
USE GameData;

ALTER TABLE quiz_questions
    MODIFY category ENUM('CRICKET', 'WWE', 'BOLLYWOOD', 'FOOTBALL', 'INDIA', 'CHESS') NOT NULL;

ALTER TABLE quiz_question_decline_feedback
    MODIFY category ENUM('CRICKET', 'WWE', 'BOLLYWOOD', 'FOOTBALL', 'INDIA', 'CHESS') NOT NULL;

ALTER TABLE quiz_question_audits
    MODIFY category ENUM('CRICKET', 'WWE', 'BOLLYWOOD', 'FOOTBALL', 'INDIA', 'CHESS') NOT NULL;
