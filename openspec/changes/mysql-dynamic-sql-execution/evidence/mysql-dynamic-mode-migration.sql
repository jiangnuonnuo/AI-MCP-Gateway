-- Forward migration: run after verifying all existing MySQL protocol rows contain SQL text.
START TRANSACTION;

ALTER TABLE mcp_protocol_mysql
    ADD COLUMN execution_mode VARCHAR(32) NOT NULL DEFAULT 'TEMPLATE' AFTER protocol_id;

UPDATE mcp_protocol_mysql
SET execution_mode = 'TEMPLATE'
WHERE execution_mode IS NULL OR execution_mode = '';

ALTER TABLE mcp_protocol_mysql
    MODIFY COLUMN sql_text MEDIUMTEXT NULL,
    ADD CONSTRAINT chk_mysql_protocol_execution_mode
        CHECK (execution_mode IN ('TEMPLATE', 'DYNAMIC_READONLY')),
    ADD CONSTRAINT chk_mysql_protocol_sql_mode
        CHECK ((execution_mode = 'TEMPLATE' AND sql_text IS NOT NULL)
            OR (execution_mode = 'DYNAMIC_READONLY' AND sql_text IS NULL));

COMMIT;

-- Rollback: disable/delete dynamic records before running this block.
-- START TRANSACTION;
-- ALTER TABLE mcp_protocol_mysql
--     DROP CHECK chk_mysql_protocol_sql_mode,
--     DROP CHECK chk_mysql_protocol_execution_mode;
-- ALTER TABLE mcp_protocol_mysql MODIFY COLUMN sql_text MEDIUMTEXT NOT NULL;
-- ALTER TABLE mcp_protocol_mysql DROP COLUMN execution_mode;
-- COMMIT;
