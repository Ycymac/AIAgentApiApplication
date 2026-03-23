CREATE TABLE IF NOT EXISTS `interviewee_form` (
  `id` bigint NOT NULL,
  `form_name` varchar(255) NOT NULL COMMENT '简历名称',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `grade` varchar(64) NOT NULL COMMENT '面试对象年级',
  `major` varchar(128) NOT NULL COMMENT '学习专业',
  `learning_direction` varchar(255) NOT NULL COMMENT '学习方向',
  `learning_progress` text NOT NULL COMMENT '学习进度',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_id_create_time` (`user_id`, `create_time`),
  KEY `idx_user_id_form_name` (`user_id`, `form_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历信息表';
