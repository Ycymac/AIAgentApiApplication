CREATE TABLE IF NOT EXISTS `interviewee_form` (
  `id` bigint NOT NULL,
  `form_name` varchar(255) NOT NULL COMMENT '简历名称',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `candidate_name` varchar(64) NOT NULL COMMENT '候选人姓名',
  `job_intention` varchar(255) NOT NULL COMMENT '求职意向',
  `professional_skills` text NOT NULL COMMENT '专业技能，JSON数组',
  `education_experiences` text NOT NULL COMMENT '教育经历，JSON数组',
  `work_experiences` text DEFAULT NULL COMMENT '工作经历，JSON数组',
  `project_experiences` text NOT NULL COMMENT '项目经历，JSON数组',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_create_time` (`user_id`, `create_time`),
  KEY `idx_user_id_form_name` (`user_id`, `form_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历信息表';
