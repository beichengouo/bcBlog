-- ============================================================
-- 070 举报表（兼容版：不含 USE / SET NAMES，排序规则用 general_ci）
--
-- 用法（二选一）：
--   A. Navicat：先在左侧双击选中要升级的数据库（例如 bc_blog），再打开本文件「运行 SQL 文件」；
--   B. 命令行：mysql --default-character-set=utf8mb4 -uroot -p 你的库名 < upgrade_070_report_no_use.sql
--
-- 说明：原版 upgrade_070_report.sql 里带了 USE `bc_blog`（假设库名就是 bc_blog）。
--       如果你服务器上的库名不叫 bc_blog，或者 Navicat 里没选中库，就会报
--       "Unknown database 'bc_blog'" 或 "No database selected"，看起来像 CREATE TABLE 出错。
-- ============================================================
-- ============================================================
-- 070 违法有害信息举报（前台举报入口 + 后台处理队列）
--
-- 背景：备案安全评估会检查"用户举报渠道"与"违法有害信息处置记录"。
--   以前只有管理员主动巡查（敏感词 + 评论审核），缺少用户举报这一环。
--
-- 内容：
--   sys_report 表——记录用户对评论等内容的举报，含被举报内容快照（评论被删也能追溯）、
--   举报人、原因、处理状态与处理记录（谁在什么时候处理、结论）。
--
-- 脚本幂等，可重复执行。
-- ============================================================


CREATE TABLE IF NOT EXISTS `sys_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `target_type` varchar(20) NOT NULL DEFAULT 'comment' COMMENT '被举报对象类型：comment / article / user',
  `target_id` bigint NOT NULL COMMENT '被举报对象ID',
  `article_id` bigint DEFAULT NULL COMMENT '所属文章ID（便于定位）',
  `article_title` varchar(200) DEFAULT NULL COMMENT '文章标题快照',
  `content_snapshot` varchar(1000) DEFAULT NULL COMMENT '被举报内容快照（对象被删后仍可追溯）',
  `reason` varchar(40) NOT NULL COMMENT '举报原因：违法有害信息/广告垃圾/人身攻击/色情低俗/侵权/其他',
  `detail` varchar(500) DEFAULT NULL COMMENT '补充说明',
  `reporter_user_id` bigint DEFAULT NULL COMMENT '举报人用户ID',
  `reporter_name` varchar(100) DEFAULT NULL COMMENT '举报人昵称快照',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending 待处理 / handled 已处理 / ignored 已忽略',
  `handle_note` varchar(300) DEFAULT NULL COMMENT '处理结论与说明',
  `handler_id` bigint DEFAULT NULL COMMENT '处理人（管理员）ID',
  `handler_name` varchar(100) DEFAULT NULL COMMENT '处理人用户名快照',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_target` (`target_type`,`target_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='违法有害信息举报记录';

SELECT '举报表已就绪' AS item, COUNT(*) AS value FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_report';