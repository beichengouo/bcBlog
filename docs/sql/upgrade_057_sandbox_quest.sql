-- ============================================================================
-- 沙盒：旅人委托板（角色自己接委托、推进进度、拿奖励）
-- ----------------------------------------------------------------------------
-- 玩法：委托板上的委托由管理员手动新增或让 AI 生成（结合世界观 / 地图 / 今日纪闻）。
--       角色行动时会在提示词里看到「可接的委托」（最多 N 条，带距离），自己决定接哪一条；
--       接下后变成「当前委托」，每步由 AI 汇报进度，进度到 100 且通过服务端两道轻校验才算完成，
--       完成后奖励（金币 + 物品）直接进角色的账；角色也可以主动放弃（委托回到板上、进度清零）。
--
-- 设计要点：
--   1. 一人一委托：有进行中的委托时，其他委托不再注入提示词（省 token，也防误判）；
--   2. 全世界可见：接了远处的委托就自己赶路过去，前台每条标注距离；
--   3. 无时限：改为在提示词里告知"已奔波 X 小时 + 进度 Y%"，由 AI 按角色性格决定继续还是放弃；
--   4. 刷新时保留"接取中"的委托：新生成条数 = 委托板总数 − 接取中条数（在服务端计算）；
--   5. 前台显示"最新一批可接 + 所有接取中 + 近三天已完成（标『已被 XX 完成』）"；
--      已完成的委托同时会出现在该角色的档案里（「最近完成的委托」，同样保留三天）；
--   6. 未接取的旧批次委托与三天前的已完成委托由数据清理任务删除（cleanup_sandbox_quest_days）。
--
-- 执行方式：Navicat 选中 bc_blog → 运行 SQL 文件；命令行
--   mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_057_sandbox_quest.sql
-- 脚本幂等，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

-- 1. 委托
CREATE TABLE IF NOT EXISTS `sandbox_quest` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `batch_time` datetime NOT NULL COMMENT '所属批次（刷新时间）：前台可接只展示最新一批',
    `title` varchar(120) NOT NULL COMMENT '委托标题（角色按标题一字不差地接取）',
    `description` varchar(500) DEFAULT NULL COMMENT '委托详情（背景、注意事项）',
    `quest_type` varchar(20) NOT NULL DEFAULT 'other' COMMENT 'hunt 讨伐 / gather 采集 / escort 护送 / explore 探索 / chore 杂务 / other',
    `difficulty` tinyint NOT NULL DEFAULT 1 COMMENT '难度 1~5（前台按星展示）',
    `location_name` varchar(90) DEFAULT NULL COMMENT '目标地区（一级地点名）',
    `target` varchar(200) DEFAULT NULL COMMENT '目标与数量（一句话，如「清除 3 只影狼」）',
    `power` int DEFAULT NULL COMMENT '对手战斗力（讨伐类；服务端会夹到该地点的战力区间）',
    `reward_coins` int NOT NULL DEFAULT 0 COMMENT '奖励金币',
    `reward_items` varchar(500) DEFAULT NULL COMMENT '奖励物品 JSON：[{"name":"","quantity":1,"description":""}]',
    `progress` int NOT NULL DEFAULT 0 COMMENT '完成进度 0~100（服务端只增不减）',
    `progress_note` varchar(200) DEFAULT NULL COMMENT 'AI 最近一次对进度的判断（一句话）',
    `status` varchar(20) NOT NULL DEFAULT 'open' COMMENT 'open 可接 / taken 接取中 / completed 已完成',
    `taker_id` bigint DEFAULT NULL COMMENT '接取人角色 ID',
    `taker_name` varchar(90) DEFAULT NULL COMMENT '接取人角色名（快照）',
    `taken_at` datetime DEFAULT NULL COMMENT '接取时间',
    `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
    `completion_note` varchar(300) DEFAULT NULL COMMENT '完成经过（一句话，前台展示）',
    `abandoned_by` varchar(200) DEFAULT NULL COMMENT '曾接取后放弃的角色名（顿号分隔，用于提示"你之前放弃过它"）',
    `source` varchar(20) NOT NULL DEFAULT 'admin' COMMENT 'ai / admin',
    `pinned` tinyint NOT NULL DEFAULT 0 COMMENT '管理员置顶',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否上板',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_world_status` (`world_id`, `status`),
    KEY `idx_world_batch` (`world_id`, `batch_time`),
    KEY `idx_taker` (`taker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒旅人委托板';

-- 2. 行动记录补一列：这一步和委托有关时记一句（前台「最新动态」据此显示「✓ 完成委托：XX」徽章）
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
CALL bcblog_add_col('sandbox_act', 'quest_event',
    'varchar(200) DEFAULT NULL COMMENT ''这一步的委托事件，如「完成委托：XXX」「接取委托：XXX」'' AFTER `look`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 3. 配置项
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_quest_title', '旅人委托板', '前台委托板栏目名'),
    ('sandbox_quest_enabled', '1', '旅人委托板总开关：1 开启，0 前台隐藏（角色也不再接取）'),
    ('sandbox_quest_visible_count', '8', '提示词里最多列几条可接委托（1~12）'),
    ('sandbox_quest_per_generate', '4', '每次刷新生成几条委托（1~10）'),
    ('sandbox_quest_provider_id', '', '生成委托使用的 AI 服务商 id（留空用系统服务商）'),
    ('sandbox_quest_model', '', '生成委托使用的模型（留空用系统服务商默认模型）'),
    ('sandbox_quest_prompt_extra', '', '生成委托的附加要求（会追加到提示词）'),
    ('sandbox_quest_progress_step_max', '40', '单步进度上限（%）：防止 AI 一步把进度从 0 写到 100'),
    ('sandbox_quest_auto_enabled', '0', '是否按间隔自动刷新委托（默认关闭，手动刷新即可）'),
    ('sandbox_quest_interval_hours', '24', '自动刷新间隔（小时）'),
    ('sandbox_quest_auto_time', '09:00', '当天第一次自动刷新的时间 HH:mm'),
    ('cleanup_sandbox_quest_days', '3', 'sandbox_quest 已完成委托与旧批次委托保留天数');

SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sandbox_quest')
ORDER BY TABLE_NAME;
