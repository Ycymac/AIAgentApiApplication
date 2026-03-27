ALTER TABLE `interview_record`
ADD COLUMN `interview_point` int NOT NULL DEFAULT 0 COMMENT '面试总分，100分制' AFTER `report_record`;
