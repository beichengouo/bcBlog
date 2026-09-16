-- ============================================================================
-- 沙盒：旅人集市（AI 定时刷新商品 + 积分赠送角色）
-- ----------------------------------------------------------------------------
-- 玩法：集市按配置的间隔自动刷新出一批商品（数量、价格、库存由 AI 结合世界观决定），
--       登录用户花积分买下商品并**直接赠送给某个角色**，商品进入该角色的背包，
--       角色下一次行动时会在提示词里看到「收到来自异世界的礼物」（不出现赠送者名字）。
--
-- 设计要点：
--   1. 前台只展示**最新一批**商品；旧批次立即下架，数据保留 3 天后由数据清理删除；
--   2. 限购：同一用户对同一商品、每个角色各限 N 件/天（默认 1），防止扫货；
--   3. 库存为 0 显示「已售罄」；购买用条件更新扣库存，并发不会超卖；
--   4. 礼物链路独立于「旅人低语」开关，低语关掉礼物照样能送到；
--   5. 商场内容**不进入角色行动提示词**，只有「收到礼物」这一条会写进提示词。
--
-- 执行方式：Navicat 选中 bc_blog → 运行 SQL 文件；命令行
--   mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_039_sandbox_shop.sql
-- 脚本幂等，可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

-- 1. 集市商品（每一批对应一个 batch_time）
CREATE TABLE IF NOT EXISTS `sandbox_shop_item` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `batch_time` datetime NOT NULL COMMENT '所属批次（刷新时间）：前台只展示最新一批',
    `name` varchar(100) NOT NULL COMMENT '商品名',
    `description` varchar(300) DEFAULT NULL COMMENT '描述（含一句来源小故事）',
    `icon` varchar(500) DEFAULT NULL COMMENT '自定义图标；为空时按名字匹配 emoji',
    `rarity` tinyint NOT NULL DEFAULT 1 COMMENT '品质 1 普通 ~ 5 传说',
    `price` int NOT NULL DEFAULT 1 COMMENT '现价（积分）',
    `original_price` int DEFAULT NULL COMMENT '原价（打折时显示划线价）',
    `stock` int NOT NULL DEFAULT 1 COMMENT '剩余库存',
    `total_stock` int NOT NULL DEFAULT 1 COMMENT '本批总量',
    `source` varchar(20) NOT NULL DEFAULT 'ai' COMMENT 'ai / admin',
    `pinned` tinyint NOT NULL DEFAULT 0 COMMENT '管理员置顶',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否上架',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_world_batch` (`world_id`, `batch_time`),
    KEY `idx_world_enabled` (`world_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒旅人集市商品';

-- 2. 购买记录（谁把什么送给了哪个角色）
CREATE TABLE IF NOT EXISTS `sandbox_shop_order` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `item_id` bigint NOT NULL COMMENT '商品 id',
    `item_name` varchar(100) NOT NULL COMMENT '商品名（快照）',
    `user_id` bigint DEFAULT NULL COMMENT '购买者（前台可见，角色提示词里绝不出现）',
    `user_name` varchar(100) DEFAULT NULL COMMENT '购买者昵称快照',
    `character_id` bigint NOT NULL COMMENT '收礼角色',
    `character_name` varchar(100) DEFAULT NULL COMMENT '收礼角色名快照',
    `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
    `points_cost` int NOT NULL DEFAULT 0 COMMENT '消耗积分（管理员为 0）',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_world_time` (`world_id`, `create_time`),
    KEY `idx_item` (`item_id`),
    KEY `idx_character` (`character_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒旅人集市购买记录';

-- 3. 礼物记录（驱动角色提示词里的「旅人的馈赠」）
CREATE TABLE IF NOT EXISTS `sandbox_gift` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `character_id` bigint NOT NULL COMMENT '收礼角色',
    `item_name` varchar(100) NOT NULL COMMENT '礼物名',
    `item_description` varchar(300) DEFAULT NULL COMMENT '礼物描述',
    `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
    `points_cost` int NOT NULL DEFAULT 0 COMMENT '消耗积分',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_character_time` (`character_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒异世界礼物（写进角色提示词）';

-- 4. 配置项
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_shop_title', '旅人集市', '前台集市栏目名'),
    ('sandbox_shop_enabled', '1', '旅人集市总开关：1 开启，0 前台隐藏'),
    ('sandbox_shop_auto_enabled', '1', '是否按间隔自动刷新商品'),
    ('sandbox_shop_interval_hours', '24', '刷新间隔（小时）：24 = 每天一次，6 = 一天四次'),
    ('sandbox_shop_auto_time', '08:00', '当天第一次刷新的时间 HH:mm（从这一天开始按间隔排）'),
    ('sandbox_shop_per_generate', '3', '每次刷新生成几件商品（1~10）'),
    ('sandbox_shop_provider_id', '', '生成商品使用的 AI 服务商 id（留空用系统服务商）'),
    ('sandbox_shop_model', '', '生成商品使用的模型（留空用系统服务商默认模型）'),
    ('sandbox_shop_prompt_extra', '', '生成商品的附加要求（会追加到提示词）'),
    ('sandbox_shop_limit_per_character', '1', '同一用户对同一商品、每个角色的限购数量'),
    ('cleanup_sandbox_shop_days', '3', 'sandbox_shop_item 商品数据保留天数（旧批次商品会被清理）');

SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sandbox_shop_item', 'sandbox_shop_order', 'sandbox_gift')
ORDER BY TABLE_NAME;
