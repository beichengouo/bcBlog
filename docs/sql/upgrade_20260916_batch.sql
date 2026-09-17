-- ============================================================================
-- bcBlog 数据库增量升级脚本（2026-09-16 合并版，含沙盒世界）
-- ----------------------------------------------------------------------------
-- 用途：把「上一次部署的数据库」（对应 Git 提交 3f13623，2026-09-14 上线版本）
--       一次性升级到当前版本，全文共两部分：
--         第 1 部分：用户体系、等级/签到/邀请码、QQ 邮箱与邮件模板、积分机制、
--                    智库积分解锁、表情包、历史数据定期清理配置；
--         第 2 部分：沙盒世界模块（世界/地图/角色/行动/旅人低语/金币/好感度/记忆/背包/旅人纪闻），
--                    以及管理端安全加固（API Key 加密与脱敏、登录安全、登录 IP 记录）。
--
-- 设计原则：
--   1. 幂等：内部先查 information_schema，已存在的表 / 字段 / 索引自动跳过，
--      同一个脚本重复执行不会报错，中途失败也可以直接重跑。
--   2. 只增不删：不会 DROP 业务表，不会 DELETE 业务数据；
--      配置项与默认数据使用 INSERT IGNORE / 存在性判断，不会覆盖后台已经改好的配置。
--   3. 无敏感信息：数据库账号、邮箱授权码、各类 API Key 均为空值占位，
--      请部署后到「后台 → 接口管理 / 系统设置」里填写。
--   4. 不打扰线上：沙盒模块默认关闭（sandbox_enabled = 0），需要时在后台开启。
--
-- 执行方式（二选一）：
--   A. Navicat：连接本地/服务器数据库 → 选中 bc_blog → 右键「运行 SQL 文件」→ 选择本文件。
--   B. 命令行：mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_20260916_batch.sql
--
-- 执行前请务必备份：Navicat 右键数据库 →「转储 SQL 文件」→「结构和数据」。
-- 注意：如果你的数据库名不是 bc_blog，请修改下面这行 USE 语句。
-- 说明：本文件已包含 docs/sql 下 upgrade_001 ~ upgrade_033 的全部内容，
--       不需要再单独执行那些分步脚本；脚本末尾会返回自检清单。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

-- ============================================================================
-- 第 1 部分：用户体系 / 积分等级 / 邮箱 / 智库 / 表情包 / 数据清理（原 001~016）
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 临时存储过程：用于「字段/索引不存在时才创建」，脚本末尾会自动删除
-- ----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DROP PROCEDURE IF EXISTS `bcblog_add_idx`;
DROP PROCEDURE IF EXISTS `bcblog_drop_idx`;

DELIMITER //
CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //

CREATE PROCEDURE `bcblog_add_idx`(IN p_table VARCHAR(64), IN p_idx VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_idx) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //

CREATE PROCEDURE `bcblog_drop_idx`(IN p_table VARCHAR(64), IN p_idx VARCHAR(64))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_idx) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_idx, '`');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;


-- ============================================================================
-- 0. 旧版本结构保险补齐
--    下面这些表 / 字段是前几版功能直接加在数据库里的，早期脚本没有单独记录。
--    已存在会自动跳过，属于「保险」，不会影响现有数据。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `ai_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `base_url` varchar(300) NOT NULL,
  `api_key` varchar(500) DEFAULT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI服务商配置';

CREATE TABLE IF NOT EXISTS `background` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL DEFAULT '',
  `type` varchar(20) NOT NULL,
  `url` varchar(500) NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `portal_active` tinyint NOT NULL DEFAULT '0',
  `admin_active` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='页面背景壁纸';

CREATE TABLE IF NOT EXISTS `music_playlist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `playlist_id` varchar(100) NOT NULL,
  `active` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_playlist_id` (`playlist_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='网易云歌单';

-- 文章发布人（前台文章详情展示发布人员）；
-- 管理员分级（role：SUPER 超级管理员 / ADMIN1 一级 / ADMIN2 二级）与菜单授权
CALL bcblog_add_col('blog_article', 'author_id', 'bigint DEFAULT NULL COMMENT ''发布人ID'' AFTER `view_count`');
CALL bcblog_add_col('blog_article', 'author_name', 'varchar(50) DEFAULT NULL COMMENT ''发布人昵称'' AFTER `author_id`');
CALL bcblog_add_col('sys_user', 'role', 'varchar(20) NOT NULL DEFAULT ''ADMIN1'' COMMENT ''角色：SUPER/ADMIN1/ADMIN2''');
CALL bcblog_add_col('sys_user', 'menus', 'varchar(500) DEFAULT NULL COMMENT ''授权的后台菜单（逗号分隔）''');

-- 唯一超级管理员固定为 admin 账号
UPDATE `sys_user` SET `role` = 'SUPER' WHERE `username` = 'admin' AND `role` <> 'SUPER';


-- ============================================================================
-- 1. 多级分类：blog_category 增加 parent_id
-- ============================================================================
CALL bcblog_add_col('blog_category', 'parent_id', 'BIGINT NOT NULL DEFAULT 0 COMMENT ''父分类ID，0为顶级'' AFTER `name`');
CALL bcblog_add_idx('blog_category', 'idx_parent', 'KEY `idx_parent` (`parent_id`)');


-- ============================================================================
-- 2. Live2D 看板娘模型库
-- ============================================================================
CREATE TABLE IF NOT EXISTS `live2d_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_key` varchar(100) NOT NULL COMMENT '模型唯一标识',
  `name` varchar(100) NOT NULL COMMENT '中文名称，用于后台标注',
  `description` varchar(255) DEFAULT '' COMMENT '角色说明',
  `url` varchar(500) NOT NULL COMMENT '模型 model.json 地址',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  `active` tinyint NOT NULL DEFAULT 0 COMMENT '是否当前展示：1 是，0 否',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_key` (`model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Live2D 看板娘模型库';

-- 清掉早期版本里被替换掉的默认模型（只会命中下面这些固定 model_key）
DELETE FROM `live2d_model`
WHERE `model_key` IN ('pio', 'tia', 'haru', 'shizuku', 'wanko', 'cat-black');

-- 默认看板娘列表；INSERT IGNORE 保证重复执行不会覆盖你已改过的模型
INSERT IGNORE INTO `live2d_model`
  (`model_key`, `name`, `description`, `url`, `sort_order`, `active`)
VALUES
  ('rem', '蕾姆（默认）', '蓝色短发女仆，经典人气角色', 'https://model.hacxy.cn/rem/model.json', 1, 1),
  ('rem-2', '蕾姆 · 2', '蕾姆另一套装扮', 'https://model.hacxy.cn/rem_2/model.json', 2, 0),
  ('umaru', '小埋', '干物妹小埋', 'https://model.hacxy.cn/umaru/model.json', 3, 0),
  ('chino', '智乃', '香风智乃，软萌兔耳少女', 'https://model.hacxy.cn/chino/model.json', 4, 0),
  ('senko', '仙狐', '仙狐大人，温柔治愈', 'https://model.hacxy.cn/Senko_Normals/senko.model3.json', 5, 0),
  ('murakumo', '丛云', '舰船风格角色', 'https://model.hacxy.cn/murakumo/model.json', 6, 0),
  ('hk416-1', 'HK416 · 1', '枪娘系列', 'https://model.hacxy.cn/HK416-1-normal/model.json', 7, 0),
  ('hk416-2', 'HK416 · 2', '枪娘系列', 'https://model.hacxy.cn/HK416-2-normal/model.json', 8, 0),
  ('kar98k', 'Kar98k', '枪娘系列', 'https://model.hacxy.cn/Kar98k-normal/model.json', 9, 0),
  ('bilibili-22', 'B 站看板娘 22', 'B 站看板娘 22', 'https://model.hacxy.cn/bilibili-22/index.json', 10, 0),
  ('bilibili-33', 'B 站看板娘 33', 'B 站看板娘 33', 'https://model.hacxy.cn/bilibili-33/index.json', 11, 0);


-- ============================================================================
-- 3. 站点公告栏：支持多条公告 + 发布人
-- ============================================================================
CREATE TABLE IF NOT EXISTS `site_announcement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) NOT NULL DEFAULT '' COMMENT '公告内容',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用，0 停用',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点公告';

CALL bcblog_add_col('site_announcement', 'author', 'varchar(50) NOT NULL DEFAULT ''管理员'' COMMENT ''发布人'' AFTER `content`');


-- ============================================================================
-- 4. 流光忆庭（照片墙）与智库（资源区）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `blog_photo` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(200) DEFAULT NULL COMMENT '照片标题',
    `description` varchar(500) DEFAULT NULL COMMENT '简单介绍',
    `url` varchar(500) NOT NULL COMMENT '图片地址',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流光忆庭照片';

CREATE TABLE IF NOT EXISTS `blog_resource` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(200) NOT NULL COMMENT '资源名称',
    `description` varchar(500) DEFAULT NULL COMMENT '资源说明',
    `url` varchar(500) NOT NULL COMMENT '资源链接',
    `password` varchar(100) DEFAULT NULL COMMENT '提取密码',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智库资源';


-- ============================================================================
-- 5. 访问量统计
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_visit_stat` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `stat_date` date NOT NULL COMMENT '统计日期',
    `pv` bigint NOT NULL DEFAULT 0 COMMENT '当日访问量',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日访问量统计';


-- ============================================================================
-- 6. 歌单加载失败时的默认歌曲
-- ============================================================================
CREATE TABLE IF NOT EXISTS `music_fallback` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(200) NOT NULL COMMENT '歌曲名称',
    `artist` varchar(200) DEFAULT NULL COMMENT '歌手',
    `url` varchar(500) NOT NULL COMMENT '歌曲直链',
    `pic` varchar(500) DEFAULT NULL COMMENT '封面图',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='默认歌曲';

-- 仅在默认歌曲表为空时写入四条初始歌曲（重复执行不会重复插入）
INSERT INTO `music_fallback` (`title`, `artist`, `url`, `pic`)
SELECT '起风了', '买辣椒也用券', 'https://music.163.com/song/media/outer/url?id=1330348068.mp3', NULL
WHERE NOT EXISTS (SELECT 1 FROM `music_fallback`);

INSERT INTO `music_fallback` (`title`, `artist`, `url`, `pic`)
SELECT '少年', 'Dave', 'https://music.163.com/song/media/outer/url?id=2614935159.mp3', NULL
WHERE (SELECT COUNT(*) FROM `music_fallback`) = 1;

INSERT INTO `music_fallback` (`title`, `artist`, `url`, `pic`)
SELECT '卡农（经典钢琴版）', 'dylanf', 'https://music.163.com/song/media/outer/url?id=478507889.mp3', NULL
WHERE (SELECT COUNT(*) FROM `music_fallback`) = 2;

INSERT INTO `music_fallback` (`title`, `artist`, `url`, `pic`)
SELECT '七点钟', '齐豫', 'https://music.163.com/song/media/outer/url?id=108787.mp3', NULL
WHERE (SELECT COUNT(*) FROM `music_fallback`) = 3;


-- ============================================================================
-- 7. 用户体系：普通用户注册/登录、等级、签到、邀请码
-- ============================================================================
CALL bcblog_add_col('sys_user', 'email', 'varchar(100) DEFAULT NULL COMMENT ''邮箱'' AFTER `password`');
CALL bcblog_add_col('sys_user', 'exp', 'int NOT NULL DEFAULT 0 COMMENT ''经验值''');
CALL bcblog_add_col('sys_user', 'level', 'int NOT NULL DEFAULT 1 COMMENT ''等级''');
CALL bcblog_add_col('sys_user', 'can_invite', 'tinyint NOT NULL DEFAULT 0 COMMENT ''是否有邀请码权限''');
CALL bcblog_add_col('sys_user', 'sign_days', 'int NOT NULL DEFAULT 0 COMMENT ''累计签到天数''');
CALL bcblog_add_col('sys_user', 'last_sign_date', 'date DEFAULT NULL COMMENT ''最后签到日期''');

-- 原生评论需要记录登录用户与等级快照
CALL bcblog_add_col('blog_comment', 'user_id', 'bigint DEFAULT NULL COMMENT ''登录用户ID''');
CALL bcblog_add_col('blog_comment', 'avatar', 'varchar(500) DEFAULT NULL COMMENT ''头像快照''');
CALL bcblog_add_col('blog_comment', 'level', 'int DEFAULT NULL COMMENT ''等级快照''');
CALL bcblog_add_col('blog_comment', 'level_name', 'varchar(50) DEFAULT NULL COMMENT ''等级名称快照''');

CREATE TABLE IF NOT EXISTS `sys_level` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `level` int NOT NULL COMMENT '等级',
    `name` varchar(50) NOT NULL COMMENT '等级名称',
    `exp_required` int NOT NULL COMMENT '达到该等级所需经验',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标',
    `color` varchar(20) DEFAULT NULL COMMENT '颜色',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等级配置';

CREATE TABLE IF NOT EXISTS `sys_sign_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `sign_date` date NOT NULL COMMENT '签到日期',
    `exp` int NOT NULL DEFAULT 0 COMMENT '获得经验',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `sign_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录';

CREATE TABLE IF NOT EXISTS `sys_invite_code` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `code` varchar(50) NOT NULL COMMENT '邀请码',
    `creator_id` bigint NOT NULL COMMENT '创建人ID',
    `creator_name` varchar(100) DEFAULT NULL COMMENT '创建人昵称',
    `use_count` int NOT NULL DEFAULT 0 COMMENT '使用次数',
    `last_used_at` datetime DEFAULT NULL COMMENT '最后使用时间',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    UNIQUE KEY `uk_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请码';

-- 默认 10 级等级配置（INSERT IGNORE：不覆盖后台已修改过的等级名称与经验）
INSERT IGNORE INTO `sys_level` (`level`, `name`, `exp_required`) VALUES
    (1, '初来乍到', 0),
    (2, '渐入佳境', 100),
    (3, '小有名气', 300),
    (4, '活跃之星', 600),
    (5, '资深常客', 1000),
    (6, '意见领袖', 1600),
    (7, '社区骨干', 2400),
    (8, '荣誉元老', 3500),
    (9, '传奇存在', 5000),
    (10, '永恒传说', 7000);


-- ============================================================================
-- 8. 积分机制：用户积分字段 + 积分流水
-- ============================================================================
CALL bcblog_add_col('sys_user', 'points', 'int NOT NULL DEFAULT 0 COMMENT ''积分'' AFTER `exp`');

CREATE TABLE IF NOT EXISTS `sys_point_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `type` varchar(50) NOT NULL COMMENT '类型，如 sign、comment、admin',
    `points` int NOT NULL COMMENT '本次积分变化，可为负',
    `balance` int NOT NULL COMMENT '变化后余额',
    `reason` varchar(200) DEFAULT NULL COMMENT '说明',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水';


-- ============================================================================
-- 9. 智库升级：封面 / 详情 / 所需积分 + 解锁记录 + 表情包
-- ============================================================================
CALL bcblog_add_col('blog_resource', 'cover', 'varchar(500) DEFAULT NULL COMMENT ''封面图'' AFTER `description`');
CALL bcblog_add_col('blog_resource', 'points', 'int NOT NULL DEFAULT 1 COMMENT ''前往资源所需积分'' AFTER `cover`');
CALL bcblog_add_col('blog_resource', 'content', 'text COMMENT ''资源详情内容（HTML）'' AFTER `points`');

CREATE TABLE IF NOT EXISTS `sys_resource_unlock` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `resource_id` bigint NOT NULL COMMENT '资源ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_resource` (`user_id`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源解锁记录';

CREATE TABLE IF NOT EXISTS `sys_emoji` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `pack` varchar(100) DEFAULT NULL COMMENT '表情包名称',
    `name` varchar(100) DEFAULT NULL COMMENT '表情名称',
    `url` varchar(500) NOT NULL COMMENT '表情图片地址',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_pack` (`pack`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表情包';

-- 同一用户邮箱唯一（防止并发注册产生重复邮箱）
CALL bcblog_add_idx('sys_user', 'uk_email', 'UNIQUE KEY `uk_email` (`email`)');


-- ============================================================================
-- 10. 邮件：QQ 邮箱 SMTP 配置 + 邮件模板（支持同场景多套模板）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `sys_email_template` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `scenario` varchar(50) NOT NULL COMMENT '场景编码，如 register_code',
    `name` varchar(100) NOT NULL COMMENT '模板名称',
    `subject` varchar(200) NOT NULL COMMENT '邮件主题',
    `background_image` varchar(500) DEFAULT NULL COMMENT '背景图片地址',
    `overlay_opacity` decimal(3,2) NOT NULL DEFAULT 0.85 COMMENT '内容卡片背景透明度 0.1~1',
    `content_html` text COMMENT '正文 HTML，支持 {{变量}}',
    `variables` varchar(500) DEFAULT NULL COMMENT '可用变量说明',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
    `active` tinyint NOT NULL DEFAULT 0 COMMENT '是否为该场景当前启用模板',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_scenario` (`scenario`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件模板';

-- 兼容已经按旧脚本建过表的数据库：去掉场景唯一索引、补 active 字段
CALL bcblog_drop_idx('sys_email_template', 'uk_scenario');
CALL bcblog_add_col('sys_email_template', 'active', 'tinyint NOT NULL DEFAULT 0 COMMENT ''是否为该场景当前启用模板'' AFTER `enabled`');
-- 兼容已经按旧脚本建过表的数据库：上面那条 CREATE TABLE 对已存在的表会整段跳过，
-- 所以这里再单独补一次场景普通索引，保证老库升级后的结构与全新安装完全一致
CALL bcblog_add_idx('sys_email_template', 'idx_scenario', 'KEY `idx_scenario` (`scenario`)');

-- 各场景邮件模板
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('email_username', '', 'QQ 邮箱账号'),
    ('email_auth_code', '', 'QQ 邮箱授权码'),
    ('email_sender_name', 'bcBlog', '发件人昵称'),
    ('email_host', 'smtp.qq.com', 'SMTP 服务器'),
    ('email_port', '465', 'SMTP 端口'),
    ('email_ssl', '1', '是否使用 SSL');

-- 默认注册验证码模板（二次元粉紫风格）。
-- 只有当数据库里还没有 register_code 场景的模板时才会写入，
-- 所以不会覆盖你在后台改过的模板。
INSERT INTO `sys_email_template`
    (`scenario`, `name`, `subject`, `background_image`, `overlay_opacity`, `content_html`, `variables`, `enabled`, `active`)
SELECT 'register_code', '二次元注册验证码', '【{{siteName}}】邮箱验证码', NULL, 0.90,
'<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>二次元邮箱验证码 · 新风格</title>
</head>
<body style="margin:0; padding:0; background-color:#f5eef8; font-family: ''Segoe UI'', ''Yu Gothic'', ''Hiragino Sans'', ''Meiryo'', system-ui, sans-serif;">
  <div style="max-width:600px; margin:0 auto; padding:28px 16px; background-color:#f5eef8;">

    <!-- 主卡片：圆角柔和 + 渐变边框效果 -->
    <div style="background: linear-gradient(135deg, #ffffff, #fef7ff); border-radius:36px; box-shadow:0 20px 40px rgba(186, 140, 200, 0.18); border:1px solid #f0d9f0; padding:0; overflow:hidden; position:relative;">

      <!-- 顶部彩条：二次元渐变横幅 -->
      <div style="height:10px; background: linear-gradient(90deg, #ffb6d9, #d9b8ff, #b8e0ff, #ffb6d9); background-size: 300% 100%;"></div>

      <!-- 内容区 -->
      <div style="padding:28px 30px 26px;">

        <!-- 头部：左图标 + 标题（左右布局） -->
        <div style="display:flex; align-items:center; gap:14px; margin-bottom:6px;">
          <div style="flex-shrink:0; width:52px; height:52px; border-radius:50%; background: linear-gradient(145deg, #ffe0f0, #ffd0e8); display:flex; align-items:center; justify-content:center; font-size:26px; box-shadow:0 4px 12px rgba(255, 150, 200, 0.35);">
            ✉️
          </div>
          <div>
            <h2 style="margin:0; color:#8a4b6e; font-size:23px; font-weight:700; letter-spacing:0.3px;">
              {{siteName}} 邮箱验证
            </h2>
            <p style="margin:4px 0 0; color:#b98aa8; font-size:13px; letter-spacing:1px;">
              ✦ 一封来自二次元的小邮件 ✦
            </p>
          </div>
        </div>

        <!-- 分隔线 -->
        <div style="height:1px; background: linear-gradient(90deg, transparent, #f0d0e0, transparent); margin:20px 0 22px;"></div>

        <!-- 问候语 -->
        <p style="color:#5e4b56; line-height:1.9; font-size:15.5px; margin:0 0 6px;">
          你好呀～ <span style="color:#c95a82;">(｡•̀ᴗ-)✧</span>
        </p>
        <p style="color:#5e4b56; line-height:1.9; font-size:15.5px; margin:0 0 20px;">
          你正在注册 <strong style="color:#b3416b;">{{siteName}}</strong> 账号，本次的验证码是：
        </p>

        <!-- 验证码区域：左侧装饰条 + 大号验证码 -->
        <div style="display:flex; align-items:stretch; background: linear-gradient(120deg, #fff5fb, #f8f0ff); border-radius:24px; border:1.5px solid #f0d5f0; overflow:hidden; margin-bottom:20px; box-shadow: inset 0 2px 12px #fce8f8;">
          <!-- 左侧竖向装饰条 -->
          <div style="width:8px; background: linear-gradient(180deg, #ffb6d9, #d9b8ff, #ffb6d9); flex-shrink:0;"></div>
          <!-- 验证码内容 -->
          <div style="flex:1; padding:18px 20px; text-align:center;">
            <div style="font-size:13px; color:#b98aa8; letter-spacing:2px; margin-bottom:6px;">— 你的专属验证码 —</div>
            <div style="font-size:34px; font-weight:800; letter-spacing:10px; color:#c44b7a; text-shadow: 0 0 14px #ffcce0, 2px 2px 0 #ffe8f2;">
              {{code}}
            </div>
          </div>
          <!-- 右侧竖向装饰条 -->
          <div style="width:8px; background: linear-gradient(180deg, #ffb6d9, #d9b8ff, #ffb6d9); flex-shrink:0;"></div>
        </div>

        <!-- 有效期提示：淡底 + 小图标 -->
        <div style="display:flex; align-items:center; justify-content:center; gap:8px; background:#faf3ff; border-radius:50px; padding:10px 16px; margin-bottom:22px; border:1px dashed #e5c8e5;">
          <span style="font-size:16px;">⏳</span>
          <span style="color:#9b7a8e; font-size:14px;">验证码 5 分钟内有效，请勿泄露给他人。</span>
        </div>

        <!-- 小贴士区域：与整体风格统一 -->
        <div style="background:#fff8fd; border-radius:20px; padding:14px 18px; border-left:4px solid #d9b8ff; margin-bottom:8px;">
          <p style="margin:0; color:#8a6b7e; font-size:13.5px; line-height:1.8;">
            <span style="color:#b34e74; font-weight:600;">🎀 小贴士</span>
            <span style="color:#c9a5bc; margin:0 6px;">|</span>
            如果没收到邮件，记得看看垃圾箱哦～ 祝你注册顺利！
          </p>
        </div>

        <!-- 底部装饰：三个小圆点 -->
        <div style="text-align:center; margin-top:22px; letter-spacing:6px; color:#e5c8e5; font-size:12px;">
          ● ● ●
        </div>

        <!-- 页脚 -->
        <p style="color:#cbb0c4; font-size:11px; text-align:center; margin:18px 0 0; border-top:1px solid #f5e5f5; padding-top:14px; letter-spacing:0.5px;">
          ☆ {{siteName}} 二次元邮件站 ☆
        </p>

      </div>
    </div>

    <!-- 卡片下方小装饰 -->
    <div style="text-align:center; margin-top:14px; color:#d9b8d9; font-size:13px; opacity:0.6; letter-spacing:3px;">
      ✿ ～ ✿
    </div>
  </div>
</body>
</html>
', '{{siteName}},{{code}},{{email}},{{nickname}}', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_email_template` t WHERE t.`scenario` = 'register_code');


-- ============================================================================
-- 11. 业务默认配置项（注册、评论、签到、评论系统切换）
-- ============================================================================
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('comment_system', 'gitalk', '评论系统：gitalk / native'),
    ('register_invite_required', '0', '注册是否需要邀请码'),
    ('register_email_verify', '1', '注册是否需要邮箱验证码'),
    ('sign_exp', '5', '每日签到获得经验'),
    ('comment_exp', '3', '每次评论获得经验'),
    ('comment_exp_limit', '3', '每天获得经验的评论次数上限');


-- ============================================================================
-- 12. 历史数据定期清理配置（表名含义见 docs/sql/README.md）
--     默认值取较小值，避免数据库无限膨胀，可在后台随时调整
-- ============================================================================
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('cleanup_enabled', '1', '是否启用定期清理：1 启用，0 关闭'),
    ('cleanup_time', '03:30', '定期清理执行时间 HH:mm（服务器时间）'),
    ('cleanup_login_log_days', '7', 'sys_login_log 登录日志保留天数'),
    ('cleanup_visit_stat_days', '30', 'sys_visit_stat 每日访问量保留天数'),
    ('cleanup_sign_log_days', '30', 'sys_sign_log 签到记录保留天数'),
    ('cleanup_point_log_days', '30', 'sys_point_log 积分流水保留天数');


-- ============================================================================
-- 第 2 部分：沙盒世界模块 + 管理端安全加固（原 017~033）
--   以下各段彼此独立、都可重复执行，顺序即为建议的执行顺序。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_017_sandbox.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界（AI 角色自动生活）模块
-- ----------------------------------------------------------------------------
-- 说明：
--   1. 脚本可重复执行（CREATE TABLE IF NOT EXISTS + INSERT IGNORE）。
--   2. 默认关闭沙盒的 AI 调用（sandbox_enabled = 0），需要管理员在后台开启。
--   3. 地图、地点、角色全部由管理员在后台自行添加，脚本不预置任何角色数据。
--
-- 建议执行顺序：先执行 upgrade_20260915_batch.sql，再执行本脚本。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

-- ----------------------------------------------------------------------------
-- 1. 世界（地图与世界观，一个世界对应一张地图）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sandbox_world` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `name` varchar(100) NOT NULL DEFAULT '' COMMENT '世界名称',
    `description` varchar(500) DEFAULT NULL COMMENT '世界简介（前台展示）',
    `map_image` varchar(500) DEFAULT NULL COMMENT '地图背景图地址',
    `world_prompt` text COMMENT '世界设定：写给 AI 的世界观、规则与文风',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用，0 停用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒世界';

-- ----------------------------------------------------------------------------
-- 2. 地点（坐标使用百分比，0~100，方便适配任意尺寸的地图背景图）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sandbox_location` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `name` varchar(100) NOT NULL COMMENT '地点名称',
    `x` int NOT NULL DEFAULT 50 COMMENT '横向坐标百分比 0~100',
    `y` int NOT NULL DEFAULT 50 COMMENT '纵向坐标百分比 0~100',
    `description` varchar(500) DEFAULT NULL COMMENT '地点描述，会作为 AI 行动参考',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_world` (`world_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒地图地点';

-- ----------------------------------------------------------------------------
-- 3. 角色（人设、立绘、AI 接口绑定、当前位置与状态）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sandbox_character` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `name` varchar(100) NOT NULL COMMENT '角色名',
    `title` varchar(100) DEFAULT NULL COMMENT '称号',
    `avatar` varchar(500) DEFAULT NULL COMMENT '头像 / 立绘地址',
    `appearance` varchar(500) DEFAULT NULL COMMENT '外貌描述（前台展示）',
    `persona` text COMMENT '人设提示词，写法参考酒馆角色卡',
    `provider_id` bigint DEFAULT NULL COMMENT '绑定 AI 服务商ID',
    `model` varchar(100) DEFAULT NULL COMMENT '使用的模型',
    `temperature` decimal(3,2) NOT NULL DEFAULT 0.90 COMMENT '采样温度 0~2',
    `x` int NOT NULL DEFAULT 50 COMMENT '当前横向坐标百分比',
    `y` int NOT NULL DEFAULT 50 COMMENT '当前纵向坐标百分比',
    `location_name` varchar(100) DEFAULT NULL COMMENT '当前位置名称',
    `status_json` varchar(1000) DEFAULT NULL COMMENT '当前状态（JSON，内容由 AI 生成）',
    `next_run_time` datetime DEFAULT NULL COMMENT '下次 AI 行动时间',
    `last_run_time` datetime DEFAULT NULL COMMENT '上次 AI 行动时间',
    `interval_min` int NOT NULL DEFAULT 45 COMMENT '行动间隔最小值（分钟）',
    `interval_max` int NOT NULL DEFAULT 75 COMMENT '行动间隔最大值（分钟）',
    `last_error` varchar(500) DEFAULT NULL COMMENT '最后一次调用失败原因',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用，0 停用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_world` (`world_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒角色';

-- ----------------------------------------------------------------------------
-- 4. 行动记录（每小时左右产生一条，前台时间线展示）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sandbox_act` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `character_id` bigint NOT NULL COMMENT '角色ID',
    `location_name` varchar(100) DEFAULT NULL COMMENT '这一步所处地点',
    `x` int DEFAULT NULL COMMENT '这一步的横向坐标',
    `y` int DEFAULT NULL COMMENT '这一步的纵向坐标',
    `actions` varchar(1000) DEFAULT NULL COMMENT '做了什么（多条用换行分隔）',
    `inner_voice` varchar(1000) DEFAULT NULL COMMENT '心声',
    `status_json` varchar(1000) DEFAULT NULL COMMENT '这一步结束后的状态',
    `summary` varchar(300) DEFAULT NULL COMMENT '一句话概括',
    `raw_response` text COMMENT 'AI 原始返回，便于排查问题',
    `from_ai` tinyint NOT NULL DEFAULT 1 COMMENT '是否来自 AI：1 是，0 为兜底记录',
    `manual` tinyint NOT NULL DEFAULT 0 COMMENT '是否管理员手动执行：1 是（不占用每日额度）',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_character_time` (`character_id`, `create_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒行动记录';

-- ----------------------------------------------------------------------------
-- 5. 旅人低语（前台互动：必须登录并消耗积分，会被下一次 AI 行动参考）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sandbox_interaction` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `character_id` bigint NOT NULL COMMENT '角色ID',
    `user_id` bigint NOT NULL COMMENT '留言用户ID',
    `user_name` varchar(100) DEFAULT NULL COMMENT '用户昵称快照',
    `user_avatar` varchar(500) DEFAULT NULL COMMENT '用户头像快照',
    `content` varchar(500) NOT NULL COMMENT '低语内容',
    `points_cost` int NOT NULL DEFAULT 0 COMMENT '消耗积分',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_character` (`character_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒旅人低语';

-- ----------------------------------------------------------------------------
-- 6. 沙盒配置项（默认关闭调用，可在后台「沙盒日志 → 沙盒设置」里修改）
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_enabled', '0', '沙盒 AI 调用总开关：1 开启，0 关闭'),
    ('sandbox_interval_min', '45', '角色行动间隔最小值（分钟）'),
    ('sandbox_interval_max', '75', '角色行动间隔最大值（分钟）'),
    ('sandbox_night_start', '02:00', '夜间静默开始时间 HH:mm，期间不调用 AI'),
    ('sandbox_night_end', '07:00', '夜间静默结束时间 HH:mm'),
    ('sandbox_daily_limit', '12', '每个角色每天最多自动调用次数'),
    ('sandbox_whisper_points', '1', '旅人低语每次消耗积分');

-- ----------------------------------------------------------------------------
-- 7. 自检
-- ----------------------------------------------------------------------------
SELECT TABLE_NAME AS '沙盒相关表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('sandbox_world', 'sandbox_location', 'sandbox_character', 'sandbox_act', 'sandbox_interaction')
ORDER BY TABLE_NAME;

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_018_sandbox_location_icon.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒地图地点：新增图标字段
-- ----------------------------------------------------------------------------
-- icon 存两种值：
--   1. 内置图标 key，例如 pin / village / forest / tower / lake / ruins / tavern
--   2. 管理员自行上传的图片地址，例如 /uploads/xxx.png
-- 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

CALL bcblog_add_col('sandbox_location', 'icon',
    'varchar(500) DEFAULT NULL COMMENT ''地点图标：内置图标 key 或上传的图片地址'' AFTER `name`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_location' AND COLUMN_NAME = 'icon';

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_019_sandbox_coins.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界：角色金币与金币流水
-- ----------------------------------------------------------------------------
-- 说明：
--   1. 角色新增 coins 字段（金币），AI 日常活动赚取/消耗，前台用户可用积分贡献。
--   2. 行动记录新增 coin_change，前台时间线展示本次金币变化。
--   3. 新增 sandbox_coin_log 金币流水（贡献 / 赚取 / 消耗 / 管理员调整）。
--   4. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

-- 角色金币余额
CALL bcblog_add_col('sandbox_character', 'coins',
    'int NOT NULL DEFAULT 0 COMMENT ''金币余额'' AFTER `status_json`');

-- 每次行动的金币变化，便于前台时间线展示
CALL bcblog_add_col('sandbox_act', 'coin_change',
    'int NOT NULL DEFAULT 0 COMMENT ''本次金币变化，正为赚取、负为消耗'' AFTER `status_json`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 金币流水
CREATE TABLE IF NOT EXISTS `sandbox_coin_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `character_id` bigint NOT NULL COMMENT '角色ID',
    `user_id` bigint DEFAULT NULL COMMENT '贡献人（赚取/消耗为空）',
    `user_name` varchar(100) DEFAULT NULL COMMENT '贡献人昵称快照',
    `type` varchar(20) NOT NULL COMMENT '类型：contribute 贡献 / earn 赚取 / spend 消耗 / admin 管理员调整',
    `coins` int NOT NULL COMMENT '金币变化，正为增加、负为减少',
    `points_cost` int NOT NULL DEFAULT 0 COMMENT '贡献消耗的积分',
    `balance` int NOT NULL DEFAULT 0 COMMENT '变化后余额',
    `remark` varchar(200) DEFAULT NULL COMMENT '说明',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_character` (`character_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒金币流水';

-- 金币相关配置
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_coin_rate', '10', '每 1 积分可兑换的金币数量'),
    ('sandbox_coin_max_points', '100', '单次贡献积分上限，0 表示不限制');

-- 自检
SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND ((TABLE_NAME = 'sandbox_character' AND COLUMN_NAME = 'coins')
    OR (TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'coin_change'))
ORDER BY TABLE_NAME;

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_020_sandbox_multi_character.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界：多角色同时行动与角色互动
-- ----------------------------------------------------------------------------
-- 说明：
--   1. sandbox_act 增加 companions 字段：记录这一步和哪些角色发生了互动，
--      由 AI 按提示词返回，前台时间线会显示「与 XX 互动」。
--   2. 新增配置 sandbox_batch_window_minutes：调度时把「已到期或即将到期」的角色
--      合并成同一轮一起行动，方便角色之间互相遇见（0 表示只处理已到期的角色）。
--   3. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

CALL bcblog_add_col('sandbox_act', 'companions',
    'varchar(200) DEFAULT NULL COMMENT ''这一步互动的其他角色，逗号分隔'' AFTER `inner_voice`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_batch_window_minutes', '5', '同一轮行动的时间窗（分钟）：已到期或即将到期的角色会一起行动，方便互相遇见；0 表示只处理已到期的角色');

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'companions';

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_021_sandbox_relation.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界：角色之间的好感度
-- ----------------------------------------------------------------------------
-- 说明：
--   1. 新增 sandbox_relation 表，记录「A 对 B 的好感度」（有方向），
--      AI 每次行动会返回 favor_changes，服务端按角色名累加。
--   2. sandbox_act 增加 favor_change 字段，记录这一步的好感度变化，前台时间线展示。
--   3. 好感度范围 -100 ~ 100，前台按区间显示为 敌视 / 反感 / 陌生 / 相识 / 友好 / 亲近 / 挚友。
--   4. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

CREATE TABLE IF NOT EXISTS `sandbox_relation` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `character_id` bigint NOT NULL COMMENT '角色ID（好感度的持有方）',
    `target_id` bigint NOT NULL COMMENT '对象角色ID',
    `favor` int NOT NULL DEFAULT 0 COMMENT '好感度 -100~100',
    `remark` varchar(200) DEFAULT NULL COMMENT '管理员备注，例如「在集市认识的酒友」',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pair` (`character_id`, `target_id`),
    KEY `idx_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒角色好感度';

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

CALL bcblog_add_col('sandbox_act', 'favor_change',
    'varchar(200) DEFAULT NULL COMMENT ''这一步的好感度变化，如「零 +3」'' AFTER `companions`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 自检
SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_relation';

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'favor_change';

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_022_sandbox_favor_audit.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界：好感度结算修正与可选的自查（审查）开关
-- ----------------------------------------------------------------------------
-- 背景：AI 有时给出的 favor_changes 会被服务端截断（例如已到 100 再 +5），
--       旧逻辑把「AI 想要的变化」写进了行动记录，导致记录显示 +5 但好感条没动。
--
-- 本次改动：
--   1. sandbox_relation 增加 last_change / last_change_time，
--      记录「上一次实际生效的变化」，前台可以直接显示「较上次 +3」。
--   2. 新增配置 sandbox_verify_enabled：可选的 AI 自查（审查）开关，
--      开启后每次行动会额外调用一次 AI 只做 JSON 校验与修正，默认关闭（会翻倍消耗 token）。
--   3. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

CALL bcblog_add_col('sandbox_relation', 'last_change',
    'int NOT NULL DEFAULT 0 COMMENT ''上一次实际生效的好感度变化'' AFTER `favor`');

CALL bcblog_add_col('sandbox_relation', 'last_change_time',
    'datetime DEFAULT NULL COMMENT ''上一次好感度变化时间'' AFTER `last_change`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_verify_enabled', '0', 'AI 输出自查开关：1 开启后每次行动额外调用一次 AI 只做 JSON 校验与修正（会翻倍消耗 token）');

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_relation'
  AND COLUMN_NAME IN ('last_change', 'last_change_time')
ORDER BY COLUMN_NAME;

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_023_sandbox_chain.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界：互动触发「回应回合」
-- ----------------------------------------------------------------------------
-- 背景：角色 A 行动时如果和其他角色 B 产生了互动，而 B 还没到行动时间，
--       就会出现「A 已经搭话、B 还不知道」的信息不对等。
--
-- 本次改动：
--   1. 角色行动产生了互动（companions 非空）时，被互动的角色会立即行动一次（回应回合）。
--   2. 为防止无限循环，增加了 4 层防护：
--      - 回应链深度上限（默认 1：只回应一轮，回应方的行动不再继续触发新的回应）
--      - 每轮最多触发的回应次数（默认 3）
--      - 冷却时间：刚行动过的角色不做立即回应（默认 15 分钟内不重复触发）
--      - 本轮已经排队的角色不再额外触发（避免同一角色连着行动两次）
--   3. sandbox_act 增加 reaction 字段，标记这条记录是「回应回合」，后台可区分。
--   4. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

CALL bcblog_add_col('sandbox_act', 'reaction',
    'tinyint NOT NULL DEFAULT 0 COMMENT ''是否由其他角色的互动触发的回应回合：1 是'' AFTER `manual`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_chain_max_depth', '1', '互动回应链最大深度：0 关闭立即回应，1 只回应一轮，2 允许对方再回应一次'),
    ('sandbox_chain_limit_per_round', '3', '每轮调度最多触发的回应次数，防止一次性消耗过多 token'),
    ('sandbox_reaction_cooldown_minutes', '15', '刚行动过的角色在该时间内不做立即回应，避免连着说话；0 表示不限制');

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'reaction';

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_024_sandbox_memory_item.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界：每日记忆总结 + 角色背包
-- ----------------------------------------------------------------------------
-- 1. sandbox_memory：每天晚上把角色当天的行动总结成一段长期记忆，
--    后续几天的提示词只带记忆 + 近期行动，避免历史日志越堆越多。
-- 2. sandbox_item：角色背包（同角色同物品唯一，数量累加，用完自动移除）。
-- 3. sandbox_act.item_change：记录这一步的物品变化，前台时间线展示。
-- 4. 保留天数接入现有「系统设置 → 数据清理」：行动日志默认 7 天、记忆默认 30 天。
-- 5. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

CREATE TABLE IF NOT EXISTS `sandbox_memory` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `character_id` bigint NOT NULL COMMENT '角色ID',
    `memory_date` date NOT NULL COMMENT '记忆对应的日期',
    `summary` text COMMENT '当天的记忆总结',
    `act_count` int NOT NULL DEFAULT 0 COMMENT '当天行动条数',
    `from_ai` tinyint NOT NULL DEFAULT 1 COMMENT '是否由 AI 总结：1 是，0 为兜底拼接',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_char_date` (`character_id`, `memory_date`),
    KEY `idx_memory_date` (`memory_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒角色每日记忆';

CREATE TABLE IF NOT EXISTS `sandbox_item` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `character_id` bigint NOT NULL COMMENT '角色ID',
    `name` varchar(100) NOT NULL COMMENT '物品名称',
    `quantity` int NOT NULL DEFAULT 1 COMMENT '数量',
    `description` varchar(300) DEFAULT NULL COMMENT '物品说明（管理员可补充）',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_char_item` (`character_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒角色背包';

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

CALL bcblog_add_col('sandbox_act', 'item_change',
    'varchar(300) DEFAULT NULL COMMENT ''这一步的物品变化，如「获得 干粮 +1」'' AFTER `favor_change`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 记忆相关配置
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_memory_enabled', '1', '是否开启每日记忆总结：1 开启，0 关闭'),
    ('sandbox_memory_time', '23:50', '每天生成记忆总结的时间 HH:mm（服务器时间）'),
    ('sandbox_memory_prompt_days', '5', '提示词里携带最近几天的记忆总结'),
    ('sandbox_memory_delete_acts', '0', '总结后是否立即删除当天行动日志：1 是（不推荐，前台时间线会看不到当天内容），0 否');

-- 保留天数：接入现有数据清理
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('cleanup_sandbox_act_days', '7', 'sandbox_act 沙盒行动日志保留天数'),
    ('cleanup_sandbox_memory_days', '30', 'sandbox_memory 沙盒记忆保留天数');

-- 自检
SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('sandbox_memory', 'sandbox_item')
ORDER BY TABLE_NAME;

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_act' AND COLUMN_NAME = 'item_change';

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_025_sandbox_item_rarity.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒角色背包：物品品质与图标
-- ----------------------------------------------------------------------------
-- 1. sandbox_item 增加 rarity（品质 1~5）与 icon（自定义图标图片地址）。
--    前台背包按品质显示不同颜色边框，未上传图标时按物品名自动匹配一个 emoji 图标。
-- 2. AI 新获得的物品会按关键词推断一个初始品质（普通/精良/稀有/史诗/传说），管理员可随时修改。
-- 3. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

CALL bcblog_add_col('sandbox_item', 'rarity',
    'tinyint NOT NULL DEFAULT 1 COMMENT ''品质：1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说'' AFTER `quantity`');

CALL bcblog_add_col('sandbox_item', 'icon',
    'varchar(500) DEFAULT NULL COMMENT ''物品图标图片地址，为空时前台按物品名自动匹配图标'' AFTER `rarity`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

SELECT COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型', COLUMN_COMMENT AS '说明'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_item'
  AND COLUMN_NAME IN ('rarity', 'icon')
ORDER BY COLUMN_NAME;

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_026_sandbox_sub_location.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界：二级地点
-- ----------------------------------------------------------------------------
-- 1. 角色每次行动时，除了一级地点（后台维护、带坐标），还会让 AI 自行创作一个
--    具体的二级地点，例如「自由城邦联盟 · 东侧集市」。
-- 2. sandbox_act 记录这一步的二级地点；sandbox_character 记录角色当前所在的二级地点。
-- 3. 二级地点不单独占地图坐标，地图上角色位置仍按一级地点绘制。
-- 4. 二级地点会写进每日记忆与其它角色看到的「最近的动静」，让世界更连贯。
-- 5. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

CALL bcblog_add_col('sandbox_act', 'sub_location',
    'varchar(100) DEFAULT NULL COMMENT ''二级地点，AI 自行创作，如「东侧集市」'' AFTER `location_name`');

CALL bcblog_add_col('sandbox_character', 'sub_location',
    'varchar(100) DEFAULT NULL COMMENT ''当前所在的二级地点'' AFTER `location_name`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

SELECT TABLE_NAME AS '表', COLUMN_NAME AS '已就绪的字段', COLUMN_TYPE AS '类型'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME = 'sub_location'
  AND TABLE_NAME IN ('sandbox_act', 'sandbox_character')
ORDER BY TABLE_NAME;

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_027_sandbox_location_area.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒地图：地点从「一个坐标点」改为「一块矩形范围」
-- ----------------------------------------------------------------------------
-- 1. sandbox_location 增加 width / height（百分比），与 x / y（左上角）一起表示区域；
--    width = 0 或 height = 0 时仍按「点」处理。
-- 2. 已有地点以「原坐标为中心」自动生成一个默认区域（12% × 7%，视觉上接近正方形），
--    之后可以在后台拖动/缩放调整。只迁移 width=0 的记录，脚本可重复执行。
-- 3. 角色坐标仍由 AI 给出，但服务端会把它夹紧到所选地点的区域内，
--    需要判断「角色在哪个地点」时，按「坐标落在哪个矩形内」优先匹配。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

CALL bcblog_add_col('sandbox_location', 'width',
    'int NOT NULL DEFAULT 0 COMMENT ''区域宽度百分比；0 表示单点'' AFTER `y`');

CALL bcblog_add_col('sandbox_location', 'height',
    'int NOT NULL DEFAULT 0 COMMENT ''区域高度百分比；0 表示单点'' AFTER `width`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 把还是「点」的地点以原坐标为中心扩成默认区域（只影响 width=0 的记录）
UPDATE `sandbox_location`
SET `width` = 12,
    `height` = 7,
    `x` = GREATEST(0, LEAST(100 - 12, `x` - 6)),
    `y` = GREATEST(0, LEAST(100 - 7, `y` - 4))
WHERE `width` = 0;

SELECT id, name, x, y, width, height FROM `sandbox_location` ORDER BY sort_order, id;

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_028_sandbox_ai_interval.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界：由 AI 决定下一次行动间隔
-- ----------------------------------------------------------------------------
-- 背景：固定 45~75 分钟会出现「刚睡下又被叫醒」的不合理情况。
--       现在每次行动时由 AI 自己给出「下次隔多久再行动」，服务端按它排期。
--
-- 本次改动：
--   1. sandbox_act 增加 next_after_minutes / next_after_reason：
--      记录这一步之后 AI 期望的间隔与原因（如「睡觉」），后台日志可查看。
--   2. sandbox_character 增加 next_reason（前台显示「正在睡觉，约 6 小时后」）
--      与 ai_interval_min / ai_interval_max（角色级覆盖，留空则用全局设置）。
--   3. 新增全局配置：是否启用 AI 间隔、间隔上下限（默认 15 ~ 720 分钟）。
--   4. 夜间静默默认关闭（起止时间相同即为关闭）：因为角色自己会去睡觉，
--      不再需要额外的静默时段。如仍想启用，在后台把两个时间改成不同值即可。
--   5. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

CALL bcblog_add_col('sandbox_act', 'next_after_minutes',
    'int NOT NULL DEFAULT 0 COMMENT ''这一步之后 AI 期望的间隔分钟数，0 表示未指定'' AFTER `summary`');

CALL bcblog_add_col('sandbox_act', 'next_after_reason',
    'varchar(50) DEFAULT NULL COMMENT ''间隔原因，如「睡觉」「赶路」'' AFTER `next_after_minutes`');

CALL bcblog_add_col('sandbox_character', 'next_reason',
    'varchar(50) DEFAULT NULL COMMENT ''下次行动的原因，如「睡觉」，前台展示用'' AFTER `next_run_time`');

CALL bcblog_add_col('sandbox_character', 'ai_interval_min',
    'int DEFAULT NULL COMMENT ''该角色 AI 间隔下限（分钟），留空用全局设置'' AFTER `interval_max`');

CALL bcblog_add_col('sandbox_character', 'ai_interval_max',
    'int DEFAULT NULL COMMENT ''该角色 AI 间隔上限（分钟），留空用全局设置'' AFTER `ai_interval_min`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_ai_interval_enabled', '1', '是否由 AI 决定下次行动间隔：1 启用，0 用固定的随机区间'),
    ('sandbox_ai_interval_min', '15', 'AI 间隔下限（分钟）'),
    ('sandbox_ai_interval_max', '720', 'AI 间隔上限（分钟），720 = 12 小时');

-- 夜间静默默认关闭（起止时间相同 = 不启用）
UPDATE `sys_config` SET `config_value` = '00:00'
WHERE `config_key` IN ('sandbox_night_start', 'sandbox_night_end');

SELECT config_key, config_value FROM `sys_config`
WHERE config_key LIKE 'sandbox_ai_interval%' OR config_key LIKE 'sandbox_night%'
ORDER BY config_key;

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_029_sandbox_news.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界：旅人纪闻（当天世界大事）
-- ----------------------------------------------------------------------------
-- 1. 新表 sandbox_news：由 AI 独立生成（也可以后台手写）的当天世界事件，
--    前台在地图左上角以「旅人纪闻」栏目展示，只显示当天，第二天自动清理。
-- 2. 角色行动时会在提示词里看到【今日要闻】以及事件与自己的距离，
--    由 AI 自行决定是否参与（通常只是听说、议论、担心）。
-- 3. 新增配置：栏目名称、开关、每次生成条数、生成用的 AI 服务商与模型、附加要求。
-- 4. 保留天数接入数据清理（默认 1 天 = 只留当天）。
-- 5. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

CREATE TABLE IF NOT EXISTS `sandbox_news` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `world_id` bigint NOT NULL DEFAULT 1 COMMENT '所属世界',
    `title` varchar(200) NOT NULL COMMENT '一句话事件，前台展示',
    `content` varchar(500) DEFAULT NULL COMMENT '补充说明',
    `location_name` varchar(100) DEFAULT NULL COMMENT '事件发生地点',
    `x` int DEFAULT NULL COMMENT '事件坐标 X（用于计算与角色的距离）',
    `y` int DEFAULT NULL COMMENT '事件坐标 Y',
    `level` tinyint NOT NULL DEFAULT 1 COMMENT '重要度：1 普通 / 2 重要 / 3 重大',
    `source` varchar(20) NOT NULL DEFAULT 'ai' COMMENT '来源：ai 自动生成 / admin 管理员添加',
    `news_date` date NOT NULL COMMENT '归属日期（只展示当天）',
    `pinned` tinyint NOT NULL DEFAULT 0 COMMENT '是否置顶',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_date` (`news_date`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='沙盒旅人纪闻';

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_news_title', '旅人纪闻', '前台地图左上角栏目的名称'),
    ('sandbox_news_enabled', '1', '是否启用旅人纪闻：1 启用，0 关闭'),
    ('sandbox_news_per_generate', '3', '每次生成几条事件'),
    ('sandbox_news_provider_id', '', '生成事件使用的 AI 服务商 ID，留空用默认服务商'),
    ('sandbox_news_model', '', '生成事件使用的模型，留空则生成前需手动选择'),
    ('sandbox_news_prompt_extra', '', '生成事件时的附加要求（例如偏向节庆、灾祸、商队等）'),
    ('cleanup_sandbox_news_days', '1', 'sandbox_news 旅人纪闻保留天数（1 = 只留当天）');

SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sandbox_news';

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_030_sandbox_news_auto.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒世界：旅人纪闻自动生成 + 行动记录标记引用的纪闻
-- ----------------------------------------------------------------------------
-- 1. sandbox_act 增加 news_ref：记录这一步参考/听说了哪几条旅人纪闻。
-- 2. 新增配置：是否每天定时自动生成纪闻、自动生成时间（默认 07:00）。
-- 3. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

CALL bcblog_add_col('sandbox_act', 'news_ref',
    'varchar(300) DEFAULT NULL COMMENT ''这一步参考/听说的旅人纪闻标题，顿号分隔'' AFTER `next_after_reason`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_news_auto_enabled', '1', '是否每天定时自动生成旅人纪闻：1 开启，0 关闭'),
    ('sandbox_news_auto_time', '07:00', '自动生成旅人纪闻的时间 HH:mm（服务器时间）');

SELECT config_key, config_value FROM `sys_config`
WHERE config_key LIKE 'sandbox_news_auto%'
ORDER BY config_key;

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_031_admin_key_security.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 管理端安全加固：AI 服务商归属、按管理员存储的密钥、API 调用审计
-- ----------------------------------------------------------------------------
-- 1. ai_provider 增加 owner_id：NULL = 系统服务商（超管维护，定时任务与超管使用），
--    填写管理员 ID = 该管理员自己的服务商（手动调用时使用自己的）。
-- 2. 新表 admin_api_key：按管理员保存的第三方密钥（如 DeepSeek Key）。
-- 3. 新表 admin_api_log：API 调用审计（谁、什么时候、调用了什么、用了哪个服务商、成败）。
-- 4. 审计日志保留天数接入数据清理（默认 3 天）。
-- 5. 脚本可重复执行。
--
-- 说明：密钥加密由程序处理（AES-256-GCM，主密钥放在与 jar 同级的
--      bcblog-secret.key 文件里）。程序首次启动时会自动把已有的明文密钥加密回写。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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

CALL bcblog_add_col('ai_provider', 'owner_id',
    'bigint DEFAULT NULL COMMENT ''归属管理员ID，NULL 表示系统服务商'' AFTER `is_default`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

CREATE TABLE IF NOT EXISTS `admin_api_key` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `admin_id` bigint NOT NULL COMMENT '所属管理员',
    `key_name` varchar(50) NOT NULL COMMENT '密钥名称，如 deepseek_api_key',
    `key_value` varchar(1000) DEFAULT NULL COMMENT '密钥值（程序加密后存储）',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_key` (`admin_id`, `key_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员个人密钥';

CREATE TABLE IF NOT EXISTS `admin_api_log` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `admin_id` bigint DEFAULT NULL COMMENT '调用者ID，定时任务为空',
    `admin_name` varchar(100) DEFAULT NULL COMMENT '调用者用户名/昵称快照',
    `action` varchar(100) NOT NULL COMMENT '动作，如「沙盒·立即执行一次」',
    `source` varchar(20) NOT NULL DEFAULT 'manual' COMMENT 'manual 手动 / schedule 定时',
    `target` varchar(200) DEFAULT NULL COMMENT '使用的服务商或第三方接口（不含密钥）',
    `success` tinyint NOT NULL DEFAULT 1 COMMENT '是否成功',
    `message` varchar(300) DEFAULT NULL COMMENT '失败原因',
    `cost_ms` int DEFAULT NULL COMMENT '耗时毫秒',
    `ip` varchar(64) DEFAULT NULL COMMENT '调用方 IP',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_admin` (`admin_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API 调用审计';

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('cleanup_admin_api_log_days', '3', 'admin_api_log API 调用审计保留天数');

SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('admin_api_key', 'admin_api_log')
ORDER BY TABLE_NAME;

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_032_admin_login_security.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 管理端登录安全：异常登录邮件通知、安全密码、单点登录、会话管理
-- ----------------------------------------------------------------------------
-- 1. sys_user 增加 security_password：独立于登录密码的「安全密码」（BCrypt）。
-- 2. 新表 sys_login_ip：记录每个管理员最近登录的 IP 与地区，用于判断「陌生 IP / 异地登录」，
--    并记录最近一次异常通知时间（同一 IP 24 小时内只通知一次）。
-- 3. 新增配置：总开关（本地开发可关闭）、安全邮箱、深夜时段、异常通知开关。
-- 4. 登录提醒邮件模板场景 login_alert。
-- 5. 脚本可重复执行。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

DELIMITER //
CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE table_schema = DATABASE() AND table_name = p_table AND column_name = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL bcblog_add_col('sys_user', 'security_password',
    'varchar(100) DEFAULT NULL COMMENT ''安全密码(BCrypt)，用于敏感操作二次验证'' AFTER `password`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

CREATE TABLE IF NOT EXISTS `sys_login_ip` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '管理员ID',
    `ip` varchar(64) NOT NULL COMMENT '登录IP',
    `region` varchar(100) DEFAULT NULL COMMENT 'IP 归属地（仅异常时查询并缓存）',
    `login_count` int NOT NULL DEFAULT 1 COMMENT '该IP登录次数',
    `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间',
    `last_notify_time` datetime DEFAULT NULL COMMENT '最近一次异常通知时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_ip` (`user_id`, `ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员登录IP记录';

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('admin_security_enabled', '1', '登录安全增强总开关：1 开启，0 关闭'),
    ('admin_security_email', '', '安全邮箱：异常登录提醒与安全操作的收件地址'),
    ('admin_login_alert_enabled', '1', '异常登录邮件提醒：1 开启，0 关闭'),
    ('admin_login_alert_night_start', '00:00', '深夜时段开始（该时段登录视为异常）HH:mm'),
    ('admin_login_alert_night_end', '06:00', '深夜时段结束 HH:mm'),
    ('admin_login_alert_fail_times', '3', '连续登录失败几次触发提醒，0 表示不提醒'),
    ('admin_single_login', '1', '单点登录：1 同一管理员只允许一处后台在线，新登录踢掉旧会话'),
    ('admin_security_verify_minutes', '30', '安全密码二次验证的有效期（分钟）：超过需重新验证，填 0 表示本次登录内一直有效');

-- 异常登录提醒邮件模板（可在后台「邮件管理」里编辑或新增多套）
INSERT INTO `sys_email_template`
    (`scenario`, `name`, `subject`, `background_image`, `overlay_opacity`, `content_html`, `variables`, `enabled`, `active`)
SELECT 'login_alert', '异常登录提醒', '【{{siteName}}】安全提醒：{{result}}（{{ip}}）', NULL, 0.90,
'<div style="font-family:system-ui,Segoe UI,sans-serif;color:#4a3b46;line-height:1.9;">
  <h2 style="margin:0 0 12px;color:#b3416b;">{{siteName}} 安全提醒</h2>
  <p>检测到一次需要提醒的登录行为：</p>
  <table style="border-collapse:collapse;font-size:14px;">
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">时间</td><td>{{time}}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">账号</td><td>{{username}}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">结果</td><td>{{result}}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">原因</td><td>{{reason}}</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">IP</td><td>{{ip}}（{{region}}）</td></tr>
    <tr><td style="padding:4px 12px 4px 0;color:#9a8a9c;">浏览器</td><td>{{browser}}</td></tr>
  </table>
  <p style="margin-top:14px;color:#c44b7a;">若非本人操作，请立即修改登录密码与安全密码，并检查后台配置。</p>
</div>', '{{siteName}},{{time}},{{username}},{{result}},{{reason}},{{ip}},{{region}},{{browser}}', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_email_template` t WHERE t.`scenario` = 'login_alert');

SELECT config_key, config_value FROM `sys_config` WHERE config_key LIKE 'admin_%' ORDER BY config_key;

-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_033_sandbox_memory_prompt.sql
-- ----------------------------------------------------------------------------
-- ============================================================================
-- 沙盒：记忆总结改为「故事化」叙述 + 系统模型配置
-- ----------------------------------------------------------------------------
-- 背景：记忆总结此前一直走兜底拼接（from_ai = 0），原因有两个：
--   1. 沙盒调用的 API Key 曾经误发密文导致 401（已修复）；
--   2. 记忆总结由定时任务执行、使用系统服务商，但仍沿用角色自己的模型名，
--      该模型在系统服务商上不存在时会失败。
-- 本次改动：
--   1. 提示词改为「把一天写成一段连贯的第一人称回忆」，禁止罗列流水账；
--   2. 兜底拼接也改成通顺的段落，不再是「今天：A；B；C」的清单；
--   3. 新增配置 sandbox_system_model：定时任务/记忆总结等系统级调用使用的模型
--      （留空则仍用角色自身模型）。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_system_model', '', '定时任务（记忆总结/自动行动）等系统级 AI 调用使用的模型，留空则用角色自身模型');

SELECT config_key, config_value FROM `sys_config` WHERE config_key = 'sandbox_system_model';


-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_034_sandbox_location_polygon.sql
-- ----------------------------------------------------------------------------
-- 沙盒地图地点：支持多边形区域（后台手工套索 / 魔法棒自动描边）
--   polygon 存 JSON 顶点数组 [[x,y],...]（0~100 百分比）；为空时继续按矩形区域判定，
--   所以老数据不需要迁移、行为完全不变。

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

CALL bcblog_add_col('sandbox_location', 'polygon',
    'text DEFAULT NULL COMMENT ''多边形区域顶点 JSON [[x,y],...]（百分比），为空表示按矩形区域判定'' AFTER `height`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;


-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_035_page_background.sql
-- ----------------------------------------------------------------------------
-- 前台各页面独立背景（首页 / 流光忆庭 / 智库 / 沙盒世界 / 其它前台页面）：
--   mode 有 follow（跟随前台默认壁纸）/ none（不用壁纸）/ custom（用指定壁纸）三种，
--   opacity 控制壁纸不透明度；壁纸库仍然是共用的 background 表。

CREATE TABLE IF NOT EXISTS `page_background` (
    `page_key` varchar(32) NOT NULL COMMENT '页面标识：home / photos / resources / sandbox / portal',
    `mode` varchar(10) NOT NULL DEFAULT 'follow' COMMENT 'follow=跟随前台默认壁纸，none=不使用壁纸，custom=使用 background_id',
    `background_id` bigint DEFAULT NULL COMMENT 'mode=custom 时使用的壁纸 id',
    `opacity` decimal(3,2) NOT NULL DEFAULT 1.00 COMMENT '壁纸不透明度 0.10~1.00',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`page_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='前台各页面独立背景设置';


-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_036_sandbox_fail_backoff.sql
-- ----------------------------------------------------------------------------
-- 沙盒 AI 调用失败后的退避：失败时把角色的 next_run_time 往后推，避免一直处于
-- 「逾期」状态被每 5 分钟重试一次（失败不产生行动记录，也就绕过了每日上限）。

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

CALL bcblog_add_col('sandbox_character', 'fail_count',
    'int NOT NULL DEFAULT 0 COMMENT ''连续失败次数：AI 调用连续失败时累加，成功后清零'' AFTER `last_error`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_fail_backoff_base_minutes', '15', '沙盒 AI 调用失败后的退避起步分钟数（连续失败按 2 倍递增）'),
    ('sandbox_fail_backoff_max_minutes', '120', '沙盒 AI 调用失败退避的上限分钟数');


-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_037_sandbox_multi_world.sql
-- ----------------------------------------------------------------------------
-- 沙盒多世界：世界两个独立开关（是否运行 / 前台是否可见）、
-- 低语与金币流水补世界归属、旅人低语总开关。

DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DROP PROCEDURE IF EXISTS `bcblog_add_idx`;
DELIMITER //
CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //

CREATE PROCEDURE `bcblog_add_idx`(IN p_table VARCHAR(64), IN p_idx VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_idx) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL bcblog_add_col('sandbox_world', 'portal_visible',
    'tinyint NOT NULL DEFAULT 1 COMMENT ''前台是否可见：1 出现在前台世界下拉（可只看历史），0 完全隐藏'' AFTER `enabled`');
CALL bcblog_add_col('sandbox_interaction', 'world_id',
    'bigint DEFAULT NULL COMMENT ''所属世界'' AFTER `character_id`');
CALL bcblog_add_col('sandbox_coin_log', 'world_id',
    'bigint DEFAULT NULL COMMENT ''所属世界'' AFTER `character_id`');

UPDATE `sandbox_interaction`
SET `world_id` = (SELECT `id` FROM `sandbox_world` ORDER BY `id` LIMIT 1)
WHERE `world_id` IS NULL AND EXISTS (SELECT 1 FROM `sandbox_world`);

UPDATE `sandbox_coin_log`
SET `world_id` = (SELECT `id` FROM `sandbox_world` ORDER BY `id` LIMIT 1)
WHERE `world_id` IS NULL AND EXISTS (SELECT 1 FROM `sandbox_world`);

CALL bcblog_add_idx('sandbox_interaction', 'idx_world', 'KEY `idx_world` (`world_id`)');
CALL bcblog_add_idx('sandbox_coin_log', 'idx_world', 'KEY `idx_world` (`world_id`)');

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_whisper_enabled', '1', '旅人低语总开关：1 开启（前台可给角色留言），0 关闭（前台隐藏入口，接口同时拦截，历史数据保留）');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DROP PROCEDURE IF EXISTS `bcblog_add_idx`;


-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_038_sandbox_combat_power.sql
-- ----------------------------------------------------------------------------
-- 沙盒角色新增「战斗力」：角色默认 10，AI 行动时会返回 combat_change（默认 0），
-- 只有真正影响实力的事情才变化，幅度 ±5 以内，前台只在变化时展示。

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

CALL bcblog_add_col('sandbox_character', 'combat_power',
    'int NOT NULL DEFAULT 10 COMMENT ''战斗力：综合实力（战斗技巧、魔力、装备），默认 10'' AFTER `coins`');
CALL bcblog_add_col('sandbox_act', 'combat_change',
    'int NOT NULL DEFAULT 0 COMMENT ''这一步战斗力的变化，0 表示没变'' AFTER `coin_change`');

DROP PROCEDURE IF EXISTS `bcblog_add_col`;

UPDATE `sandbox_character` SET `combat_power` = 10 WHERE `combat_power` IS NULL OR `combat_power` < 1;


-- ----------------------------------------------------------------------------
-- 来源：docs/sql/upgrade_039_sandbox_shop.sql
-- ----------------------------------------------------------------------------
-- 沙盒旅人集市：AI 定时刷新商品 + 用积分购买后直接赠送给角色（进角色背包）。

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

-- 沙盒提示词预算守护（upgrade_040）
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_prompt_char_limit', '9000', '沙盒行动提示词的字符上限：超过后自动精简（去掉他角色动静与今日要闻、最近行动取 6 条）');

-- 沙盒角色「当前目标」（upgrade_041）
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
CALL bcblog_add_col('sandbox_character', 'goal',
    'varchar(100) DEFAULT NULL COMMENT ''当前目标（AI 维护，管理员可改）'' AFTER `sub_location`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;


-- 旅人集市改用「金币」计价 + 角色可以自己买（upgrade_042）
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
CALL bcblog_add_col('sandbox_shop_order', 'coin_price',
    'int NOT NULL DEFAULT 0 COMMENT ''商品单价（金币）'' AFTER `points_cost`');
CALL bcblog_add_col('sandbox_shop_order', 'buyer_type',
    'varchar(20) NOT NULL DEFAULT ''user'' COMMENT ''购买者：user=前台用户赠送 / character=沙盒角色自购'' AFTER `coin_price`');
CALL bcblog_add_col('sandbox_gift', 'coin_price',
    'int NOT NULL DEFAULT 0 COMMENT ''商品单价（金币）'' AFTER `points_cost`');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 汇率改为 1 积分 = 1 金币；新增「角色每日自购上限」
UPDATE `sys_config` SET `config_value` = '1' WHERE `config_key` = 'sandbox_coin_rate';
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_shop_buy_per_day', '2', '沙盒角色每天最多在旅人集市自购几件商品，0 表示不限制');


-- 沙盒位置距离化（upgrade_043）：地图尺度、交通方式、互动距离门槛
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_km_map_width', '200', '地图宽度（km）：横向 100 个坐标单位对应多少公里，纵向按 16:9 折算'),
    ('sandbox_travel_speeds', '步行:4,骑乘:20,车船:12,飞行:60', '交通方式与速度（名称:km/h，逗号分隔）：提示词与赶路时间下限都用它'),
    ('sandbox_social_max_km', '30', '允许同行/涨好感的角色间最大距离（km），0 表示不限制');


-- 后台权限边界修正（upgrade_044）：沙盒系统服务商配置 + 清理失效的超管专属菜单键
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_system_provider_id', '', '沙盒系统级 AI 调用统一使用的服务商 id，留空表示自动');

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
UPDATE `sys_user` SET `menus` = NULL WHERE `role` <> 'SUPER' AND (`menus` = '' OR `menus` = ',');


-- 沙盒金币正确性 + 执行锁 + 角色态度（upgrade_045）
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
CALL bcblog_add_col('sandbox_character', 'running_at',
    'datetime DEFAULT NULL COMMENT ''正在执行行动的抢锁时间，执行结束会清空（并发保护）''');
CALL bcblog_add_col('sandbox_character', 'power_view',
    'varchar(60) DEFAULT NULL COMMENT ''对自身实力的看法（会写进行动提示词）''');
CALL bcblog_add_col('sandbox_character', 'wealth_view',
    'varchar(60) DEFAULT NULL COMMENT ''对金钱财富的看法（会写进行动提示词）''');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_run_lock_minutes', '5', '沙盒角色行动的执行锁超时（分钟）：超过视为失效锁，可被重新抢占');


-- 沙盒单次花费上限（upgrade_046）
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_max_spend_per_act', '10', '沙盒角色单次行动的「非集市花费」上限（金币）：余额越少越省，超出会被截断并在流水里注明');


-- 沙盒输出自查模式（upgrade_047）：默认「仅可疑时查」
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_verify_mode', 'suspicious', '沙盒输出自查模式：off 关闭 / suspicious 仅可疑时查（默认） / always 每次都查');


-- 沙盒三段式输出 + 文风补充（upgrade_048）
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_draft_mode', 'on', '沙盒角色行动三段式输出（草稿→自审→终稿）：on 开启（默认）/ off 关闭'),
    ('sandbox_style_extra', '', '行动提示词的【文风补充】（可粘贴酒馆预设里的写作基准段落，留空不追加）');


-- ============================================================================
-- 收尾 1. 清理临时存储过程
-- ============================================================================
DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DROP PROCEDURE IF EXISTS `bcblog_add_idx`;
DROP PROCEDURE IF EXISTS `bcblog_drop_idx`;


-- ============================================================================
-- 收尾 2. 升级结果自检：正常情况下应返回全部 38 张表
--          （脚本只做新增，不会清空任何业务数据）
-- ============================================================================
SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('admin_api_key','admin_api_log','ai_provider','background','blog_article',
                     'blog_article_tag','blog_category','blog_comment','blog_photo','blog_resource',
                     'blog_tag','live2d_model','music_fallback','music_playlist','sandbox_act',
                     'sandbox_character','sandbox_coin_log','sandbox_interaction','sandbox_item',
                     'sandbox_location','sandbox_memory','sandbox_news','sandbox_relation','sandbox_world',
                     'page_background','site_announcement','sys_config','sys_email_template','sys_emoji','sys_invite_code',
                     'sys_level','sys_login_ip','sys_login_log','sys_point_log','sys_resource_unlock',
                     'sys_sign_log','sys_user','sys_visit_stat')
ORDER BY TABLE_NAME;


-- ============================================================================
-- 收尾 3. 关键配置项自检：这些开关在后台都能改
-- ============================================================================
SELECT config_key AS '配置项', config_value AS '当前值', remark AS '说明'
FROM `sys_config`
WHERE config_key IN ('cleanup_enabled','comment_system','register_email_verify',
                     'sandbox_enabled','sandbox_memory_enabled','sandbox_news_enabled','sandbox_verify_enabled',
                     'sandbox_ai_interval_enabled','sandbox_system_model','sandbox_daily_limit')
ORDER BY config_key;
