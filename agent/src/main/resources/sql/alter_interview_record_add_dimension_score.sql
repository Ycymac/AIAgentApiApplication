ALTER TABLE `interview_record`
ADD COLUMN `accuracy_score` int DEFAULT NULL COMMENT '准确度总分，10分制' AFTER `report_record`,
ADD COLUMN `completeness_score` int DEFAULT NULL COMMENT '完整度总分，10分制' AFTER `accuracy_score`,
ADD COLUMN `level_of_detail_score` int DEFAULT NULL COMMENT '详细度总分，10分制' AFTER `completeness_score`,
ADD COLUMN `logic_score` int DEFAULT NULL COMMENT '逻辑度总分，10分制' AFTER `level_of_detail_score`,
ADD COLUMN `expression_ability_score` int DEFAULT NULL COMMENT '表达能力总分，10分制' AFTER `logic_score`;
