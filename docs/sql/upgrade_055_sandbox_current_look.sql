-- ============================================================
-- 055 沙盒「此刻的样子」：让角色的外貌随行动变化
--
-- 背景：角色的 appearance 是"人设卡里的底子"（发色、瞳色、身形、标志性装扮），
--   它同时被用作提示词里的角色卡和前台档案展示。如果让 AI 每步重写它，
--   "银白色长发、淡紫罗兰色眼眸"这些固定特征会被"此刻的样子"吃掉，
--   而且 AI 可能越改越远（今天长发、明天短发）。
--
-- 所以拆成两层：
--   · appearance（不变）      —— 外貌底子，由人设卡决定，AI 不许改；
--   · current_look（随步变化） —— 此刻的样子：穿什么、干不干净、有没有伤、
--                                头发怎么束着、神情如何，以及睡下/起床时的换装。
--
-- 前台角色档案会先显示「外貌」再显示「此刻」；后台角色编辑里也能手工微调。
-- sandbox_act.look 记录每一步结束时角色的样子，便于回溯"他这一天是怎么过来的"。
-- ============================================================

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_look_enabled', 'on', '沙盒「此刻的样子」开关：on 开启（默认，AI 每步输出当前穿着与状态）/ off 关闭');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DELIMITER //
CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;
CALL bcblog_add_col('sandbox_character', 'current_look',
    'varchar(200) DEFAULT NULL COMMENT ''此刻的样子：穿着、干净程度、伤势外观、发型神态等（随行动更新）'' AFTER `appearance`');
CALL bcblog_add_col('sandbox_act', 'look',
    'varchar(200) DEFAULT NULL COMMENT ''这一步结束时角色的样子（用于回溯外貌变化）'' AFTER `luck`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;
