-- ============================================================
-- 049 沙盒「思考阶段」（四段式）+ 审计日志记录输出字数
--
-- 背景：用户实测所用公益接口输出可达上千甚至上万 token，输出长度不是瓶颈，
--   所以把行动提示词从三段扩成四段，让 AI 先把"想清楚"这一步写扎实：
--
--     <think>   处境 + 2~3 种可选做法 + 为什么选这个 + 代价与风险（篇幅不限，鼓励真正权衡）
--     <draft>   这一步具体做什么（动作顺序 + 数值变化）
--     <review>  对照清单自检并给出改法
--     <final>   修正后的最终 JSON（必须是整段回复的最后内容、且完整）
--
--   服务端只认 <final>；解析失败时的前台兜底文本会把 think/draft/review 一起清掉。
--   思考阶段有独立开关（默认开），只有「三段式输出」开启时才生效。
--
--   另外给 admin_api_log 增加 output_chars：记录模型返回内容的字符数，
--   配合已有的 cost_ms 就能看出"放开篇幅后是否变慢 / 有没有撞到某个接口的隐形上限"。
-- ============================================================

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_think_stage', 'on', '沙盒行动提示词的思考阶段（四段式的 <think>）：on 开启（默认）/ off 关闭；仅在三段式开启时生效');

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
CALL bcblog_add_col('admin_api_log', 'output_chars',
    'int DEFAULT NULL COMMENT ''模型返回内容的字符数（观察输出长度与耗时用）'' AFTER `cost_ms`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;
