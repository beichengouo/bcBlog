-- ============================================================
-- 062 旅人委托板 / 旅人纪闻的自动刷新配置（与旅人集市同一套口径）
--
-- 背景：集市早就有「刷新间隔 + 当天首次时间」，委托板和纪闻只有「当天几点跑一次」
--   （纪闻甚至是写死的"每天一次"，委托板的自动刷新开关在后台根本没露出来）。
--   现在三者统一：
--     间隔 ≥ 24 小时 → 每天在「当天首次时间」刷一次；
--     间隔 < 24 小时 → 距上一批满 N 小时就刷，一天可以刷多次。
--   委托板：接取中的委托刷新时保留；纪闻：多出来的批次叠加在当天的纪闻里。
--
-- 默认值：
--   委托板 = 开 / 24 小时 / 09:00（每天早九点换一批）
--   纪闻   = 开 / 24 小时 / 07:00（每天早七点生成一批，维持原行为）
--
-- 注意：最后那句 UPDATE 是"一次性"的——把 057 里的旧种子默认值 0 改成新的默认值 1。
--   如果你之后在后台手动关掉了委托板的自动刷新，就不要再重跑这一句了。
-- ============================================================

SET NAMES utf8mb4;
USE `bc_blog`;

-- 纪闻：新增刷新间隔（原来只有"每天几点"）
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_news_interval_hours', '24',
     '旅人纪闻自动生成的刷新间隔（小时）：24 = 每天一次；填 6 就是一天四次。以「上一批生成时间 + 间隔」到期');

-- 委托板：补齐三个键（老库可能一个都没有），默认改成"开 / 每天 09:00 一批"
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_quest_auto_enabled', '1', '旅人委托板是否自动刷新（默认开启：每天按「刷新间隔 + 首次时间」换一批）'),
    ('sandbox_quest_interval_hours', '24', '委托板刷新间隔（小时）：24 = 每天一次；填 6 就是一天四次'),
    ('sandbox_quest_auto_time', '09:00', '委托板「当天首次刷新」时间 HH:mm');

-- 一次性：把 057 写下的旧种子默认值（自动刷新关闭）改成新默认值（开启）
UPDATE `sys_config` SET `config_value` = '1'
WHERE `config_key` = 'sandbox_quest_auto_enabled' AND `config_value` = '0';

SELECT `config_key`, `config_value` FROM `sys_config`
WHERE `config_key` IN ('sandbox_quest_auto_enabled', 'sandbox_quest_interval_hours', 'sandbox_quest_auto_time',
                       'sandbox_news_auto_enabled', 'sandbox_news_interval_hours', 'sandbox_news_auto_time',
                       'sandbox_shop_auto_enabled', 'sandbox_shop_interval_hours', 'sandbox_shop_auto_time');
