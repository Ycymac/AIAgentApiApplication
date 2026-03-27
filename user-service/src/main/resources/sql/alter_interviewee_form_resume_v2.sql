ALTER TABLE `interviewee_form`
DROP COLUMN `grade`,
DROP COLUMN `major`,
DROP COLUMN `learning_direction`,
DROP COLUMN `learning_progress`,
ADD COLUMN `candidate_name` varchar(64) DEFAULT NULL COMMENT '候选人姓名' AFTER `user_id`,
ADD COLUMN `job_intention` varchar(255) DEFAULT NULL COMMENT '求职意向' AFTER `candidate_name`,
ADD COLUMN `professional_skills` text COMMENT '专业技能，JSON数组' AFTER `job_intention`,
ADD COLUMN `education_experiences` text COMMENT '教育经历，JSON数组' AFTER `professional_skills`,
ADD COLUMN `work_experiences` text COMMENT '工作经历，JSON数组' AFTER `education_experiences`,
ADD COLUMN `project_experiences` text COMMENT '项目经历，JSON数组' AFTER `work_experiences`;
