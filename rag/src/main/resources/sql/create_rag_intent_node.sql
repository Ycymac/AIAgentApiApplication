CREATE TABLE IF NOT EXISTS `rag_intent_node` (
  `id` varchar(64) NOT NULL,
  `kb_id` varchar(64) NOT NULL,
  `name` varchar(128) NOT NULL,
  `description` varchar(512) DEFAULT NULL,
  `examples` text DEFAULT NULL,
  `prompt_snippet` text DEFAULT NULL,
  `prompt_template` text DEFAULT NULL,
  `enabled` tinyint NOT NULL DEFAULT 1,
  `created_by` varchar(64) DEFAULT NULL,
  `updated_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rag_intent_node_kb_id_deleted` (`kb_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
