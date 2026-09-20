-- ============================================================
-- 066 沙盒：免遭遇地点 + 遭遇对手记录 + 每步自检开关
--
-- 一、免遭遇（两件事一起做）
--   ① 地点级：危险度 0 = 「安全」地点，默认不刷遭遇（新增 sandbox_encounter_chance_0，默认 0）。
--      适合魔王城、城镇中心这种"本来就不该有袭击"的地方。
--   ② 角色级：角色新增「免遭遇地点」名单——同一个地点对别人危险，对 TA 不危险。
--      实测背景：魔王格蕾待在自己的魔王城，却按危险度 3 的地点区间（88~500）频繁遭遇袭击，
--      与她"魔王在自己家里"的设定冲突。
--
-- 二、遭遇的对手要能被看见
--   以前 only 把"你被盯上了：对方实力相当于战斗力 455（你现在是 350）"写进行动记录，
--   前台行动记录并没有渲染这段；而且"对手是什么"从来没落库。
--   现在新增 sandbox_act.encounter_foe（AI 输出的对手名字/种类，例如「雾隐豹」），
--   前台行动记录会把两者拼起来显示：遭遇：雾隐豹 · 战斗力 455（比你强）。
--
-- 三、每步自检开关
--   sandbox_step_selfcheck=1：每一步行动结束后，再调一次便宜模型做"质量与逻辑自检"
--   （只允许改叙述字段：actions / summary / inner_voice / look），默认开启，后台可关。
--
-- 脚本幂等，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
USE `bc_blog`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_encounter_chance_0', '0',
     '危险度 0（安全）地点的遭遇概率 %：默认 0 = 完全安全，适合魔王城、城镇中心这类地方'),
    ('sandbox_step_selfcheck', '1',
     '每步行动后的自检开关：1 开启（默认）/ 0 关闭。开启后每一步会多调一次模型检查故事逻辑与质量（只改叙述字段）');

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_step_selfcheck_mode', 'suspicious',
     '每步自检模式：off 关闭 / suspicious 命中才查（默认，只有规则检查挑出疑点才多调一次模型）/ always 每步都查（更稳，但每步多一次调用、延迟大致翻倍）');

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

CALL bcblog_add_col('sandbox_character', 'encounter_exempt_locations',
    'varchar(300) DEFAULT NULL COMMENT ''免遭遇地点（一级地点名，英文逗号分隔）：在这个角色身上这些地方不刷遭遇'' AFTER `equip_power`');
CALL bcblog_add_col('sandbox_act', 'encounter_foe',
    'varchar(60) DEFAULT NULL COMMENT ''本步遭遇的对手名字/种类（AI 判断，例如 雾隐豹）'' AFTER `encounter`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;

SELECT `config_key`, `config_value` FROM `sys_config`
WHERE `config_key` IN ('sandbox_encounter_enabled', 'sandbox_encounter_chance_0', 'sandbox_encounter_chance_1',
                       'sandbox_encounter_chance_2', 'sandbox_encounter_chance_3', 'sandbox_step_selfcheck');

SELECT COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'sandbox_character' AND COLUMN_NAME = 'encounter_exempt_locations')
    OR (TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'encounter_foe'));
