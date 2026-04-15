ALTER TABLE knowledge_document
    ADD COLUMN object_key VARCHAR(512) NULL COMMENT 'OSS object key' AFTER file_url;
