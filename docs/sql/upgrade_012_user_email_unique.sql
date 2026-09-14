-- 用户邮箱唯一约束，防止并发注册产生重复邮箱

DROP PROCEDURE IF EXISTS bcblog_user_email_unique;
DELIMITER //
CREATE PROCEDURE bcblog_user_email_unique()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'sys_user'
                     AND index_name = 'uk_email') THEN
        ALTER TABLE `sys_user` ADD UNIQUE KEY `uk_email` (`email`);
    END IF;
END //
DELIMITER ;
CALL bcblog_user_email_unique();
DROP PROCEDURE IF EXISTS bcblog_user_email_unique;
