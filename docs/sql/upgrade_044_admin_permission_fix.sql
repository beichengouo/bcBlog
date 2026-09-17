-- ============================================================
-- 044 后台权限边界修正 + 沙盒系统服务商配置
--
-- 背景（本次修的问题）：
--   1. 一些"其实只有超级管理员能用"的菜单（邮件管理、第三方接口、DeepSeek、Gitalk 评论）
--      之前可以被分配给一级/二级管理员：分配后点进去接口全 403，但页面又能显示一部分数据，很迷惑；
--   2. 沙盒「世界与地图」是整个沙盒的控制台（运行参数、AI 开关与模型、提示词相关），收紧为超管专属；
--   3. 被授权的菜单点进去偶发报「没有该菜单的权限」（其实页面能用）——站点设置、后台壁纸透明度、
--      系统监控、模型下拉框这些公共读接口参与了菜单校验，现已在代码里豁免；
--   4. 「立即清理」这类破坏性操作收紧为超管专属。
--
-- 本脚本只做两件事：
--   1. 新增「沙盒系统服务商」配置（后台→沙盒世界→世界与地图 里可选、可获取模型）；
--   2. 把已经存进 sys_user.menus、但现在已经不可分配的超管专属菜单键清掉，避免后台一直显示这些标签。
--
-- 说明：即使不执行第 2 步，前端侧边栏/路由与后端也会忽略这些键（不影响功能），执行只是让数据更干净。
-- ============================================================

-- 1. 系统级调用（定时行动 / 记忆总结）统一使用的服务商；留空 = 沿用原来的自动规则
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_system_provider_id', '', '沙盒系统级 AI 调用统一使用的服务商 id，留空表示自动（角色绑定的系统服务商 / 默认服务商）');

-- 2. 清理非超管管理员身上已经失效的超管专属菜单键
UPDATE `sys_user` SET `menus` = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', `menus`, ','), ',gitalk,', ','))
WHERE `role` <> 'SUPER' AND `menus` LIKE '%gitalk%';

UPDATE `sys_user` SET `menus` = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', `menus`, ','), ',deepseek,', ','))
WHERE `role` <> 'SUPER' AND `menus` LIKE '%deepseek%';

UPDATE `sys_user` SET `menus` = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', `menus`, ','), ',third,', ','))
WHERE `role` <> 'SUPER' AND `menus` LIKE '%third%';

UPDATE `sys_user` SET `menus` = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', `menus`, ','), ',email,', ','))
WHERE `role` <> 'SUPER' AND `menus` LIKE '%email%';

UPDATE `sys_user` SET `menus` = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', `menus`, ','), ',sandboxWorld,', ','))
WHERE `role` <> 'SUPER' AND `menus` LIKE '%sandboxWorld%';

-- 清理后如果变成了空串，置为 NULL，后台显示"未配置"
UPDATE `sys_user` SET `menus` = NULL WHERE `role` <> 'SUPER' AND (`menus` = '' OR `menus` = ',');
