-- ============================================================
-- 042 沙盒·旅人集市改为「金币」计价 + 角色可以自己买
--
-- 背景：
--   1. 集市原来用「积分」标价，只有前台用户能买（买下来送给角色）。
--   2. 现在商品统一用「金币」标价：角色自己的钱包是金币，可以自行购买；
--      前台用户购买时按汇率把金币价折算成积分扣款（1 积分 = 1 金币，汇率取自 sandbox_coin_rate）。
--
-- 说明：本脚本执行时集市还没有产生任何订单/礼物数据，所以不需要数据迁移。
-- ============================================================

-- 1. 购买记录：补充「金币价」与「购买者类型」
--    购买者展示名可直接由这两列推导：character → character_name，user → user_name
ALTER TABLE `sandbox_shop_order`
    ADD COLUMN `coin_price` int NOT NULL DEFAULT 0 COMMENT '商品单价（金币）' AFTER `points_cost`,
    ADD COLUMN `buyer_type` varchar(20) NOT NULL DEFAULT 'user' COMMENT '购买者：user=前台用户赠送 / character=沙盒角色自购' AFTER `coin_price`;

-- 2. 礼物记录：补充「金币价」（角色自购不写礼物表，只有前台赠送才写）
ALTER TABLE `sandbox_gift`
    ADD COLUMN `coin_price` int NOT NULL DEFAULT 0 COMMENT '商品单价（金币）' AFTER `points_cost`;

-- 3. 汇率调整为 1 积分 = 1 金币：金币与积分等值，后台看数据不用再换乘
UPDATE `sys_config` SET `config_value` = '1' WHERE `config_key` = 'sandbox_coin_rate';

-- 4. 新增配置：角色每天最多在集市自购几件商品（防止 AI 每步都买、把金币和 token 烧光）
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_shop_buy_per_day', '2', '沙盒角色每天最多在旅人集市自购几件商品，0 表示不限制');
