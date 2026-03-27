ALTER TABLE `interview_record`
ADD COLUMN `interview_keywords` json NULL COMMENT 'Interview keywords, professionalSkills JSON string' AFTER `interview_process_record`;

ALTER TABLE `interview_record`
MODIFY COLUMN `interview_process_record` json NOT NULL COMMENT 'Interview process record, only stores answerEvaluationRespS JSON string';
