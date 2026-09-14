-- 邮件模板支持同一场景保存多个模板，并切换当前启用模板

DROP PROCEDURE IF EXISTS bcblog_email_template_multi;
DELIMITER //
CREATE PROCEDURE bcblog_email_template_multi()
BEGIN
    -- 去掉场景唯一索引，允许同一场景保存多个模板
    IF EXISTS (SELECT 1 FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'sys_email_template'
                 AND index_name = 'uk_scenario') THEN
        ALTER TABLE `sys_email_template` DROP INDEX `uk_scenario`;
    END IF;
    -- 增加当前启用标记
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'sys_email_template'
                     AND column_name = 'active') THEN
        ALTER TABLE `sys_email_template`
            ADD COLUMN `active` tinyint NOT NULL DEFAULT 0 COMMENT '是否为该场景当前启用模板' AFTER `enabled`;
    END IF;
END //
DELIMITER ;
CALL bcblog_email_template_multi();
DROP PROCEDURE IF EXISTS bcblog_email_template_multi;

-- 已有模板默认启用一套
UPDATE `sys_email_template` SET `active` = 1
WHERE `id` = (
    SELECT `id` FROM (
        SELECT MIN(`id`) AS `id` FROM `sys_email_template` WHERE `scenario` = 'register_code'
    ) t
);
