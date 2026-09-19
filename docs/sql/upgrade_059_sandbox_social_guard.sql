-- ============================================================
-- 059 沙盒互动保险：必须处在同一个一级地点才能互动
--
-- 背景（用户实测）：互动原本只看距离——同一片区域，或相距不超过 30km 就算"附近的人"，
-- 于是出现「两个人明明分属不同的一级地点，却因为地图上这两片区挨得近（不到 30km）
-- 而互相写进 companions / 加好感度 / 触发回应行动」。
--
-- 现在默认要求：① 必须在同一个一级地点；② 在同一级地点内，要么在同一个二级地点（无条件算相遇），
-- 要么实际距离不超过 sandbox_social_max_km（默认 30km）。
-- 想回到旧行为（只看距离）可以把 sandbox_social_same_area_only 设为 0。
--
-- 脚本幂等，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
USE `bc_blog`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_social_same_area_only', '1',
     '沙盒互动保险：是否强制「必须同一个一级地点才能互动」（1 开 / 0 关）。地图上有的一级地点彼此不到 30km，不限制会出现跨地区互动');

SELECT `config_key`, `config_value` FROM `sys_config`
WHERE `config_key` IN ('sandbox_social_same_area_only', 'sandbox_social_max_km');
