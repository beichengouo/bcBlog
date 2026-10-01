-- ============================================================
-- 072 写作计划（纯 ALTER 版，兼容受限账号 / phpMyAdmin 导入）
--
-- 与 upgrade_072_article_plan.sql 内容相同，只是去掉了"存储过程 + information_schema"那套
-- 幂等写法——受限账号（例如只能操作 bc_blog 的 bcblog）读 information_schema 会被拒（#1044）。
--
-- 用法：在 phpMyAdmin 里选中 bc_blog → SQL 标签页 → 粘贴运行；
--      或 导入 → 选择本文件。
-- 如果某列已存在，会提示 #1060 Duplicate column name，忽略即可（说明那一列已经有了）。
--
-- 建议先看一眼现有列：
--   SHOW COLUMNS FROM `site_ai_profile` LIKE 'article%';
-- ============================================================

USE `bc_blog`;

ALTER TABLE `site_ai_profile`
  ADD COLUMN `article_window_start` varchar(5) NOT NULL DEFAULT '09:00' COMMENT '写作窗口开始时间';

ALTER TABLE `site_ai_profile`
  ADD COLUMN `article_window_end` varchar(5) NOT NULL DEFAULT '22:00' COMMENT '写作窗口结束时间';

ALTER TABLE `site_ai_profile`
  ADD COLUMN `article_random` tinyint NOT NULL DEFAULT 1 COMMENT '写作时间是否随机分布：1 随机 / 0 均匀';

ALTER TABLE `site_ai_profile`
  ADD COLUMN `article_plan_json` text COMMENT '当天写作计划 JSON（日期 + 各时段与状态）';

SELECT '完成：若没有出现 #1060 报错，说明 4 列都加上了' AS tip;