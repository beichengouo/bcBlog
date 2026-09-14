-- ============================================================================
-- bcBlog 数据库增量升级脚本（2026-09-15）
-- ----------------------------------------------------------------------------
-- 用途：把「上一次部署的数据库」（对应 Git 提交 3f13623，2026-09-14 上线版本）
--       升级到当前版本，新增：用户体系、等级/签到/邀请码、QQ 邮箱与邮件模板、
--       积分机制、智库积分解锁、表情包、历史数据定期清理配置。
--
-- 设计原则：
--   1. 幂等：内部先查 information_schema，已存在的表 / 字段 / 索引自动跳过，
--      同一个脚本重复执行不会报错。
--   2. 只增不删：不会 DROP 业务表，不会 DELETE 业务数据；
--      配置项与默认数据使用 INSERT IGNORE / 存在性判断，不会覆盖后台已改好的配置。
--   3. 无敏感信息：数据库账号、邮箱授权码、各类 API Key 均为空值占位，
--      请部署后在「后台 → 接口管理 / 系统设置」里填写。
--
-- 执行方式（二选一）：
--   A. Navicat：连接本地/服务器数据库 → 选中 bc_blog → 右键「运行 SQL 文件」→ 选择本文件。
--   B. 命令行：mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_20260915_batch.sql
--
-- 注意：如果你的数据库名不是 bc_blog，请修改下面这行 USE 语句。
-- ============================================================================

SET NAMES utf8mb4;
USE `bc_blog`;

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
-- 13. 清理临时存储过程
-- ============================================================================
DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DROP PROCEDURE IF EXISTS `bcblog_add_idx`;
DROP PROCEDURE IF EXISTS `bcblog_drop_idx`;


-- ============================================================================
-- 14. 升级结果自检：正常情况下应返回下面 13 张表
--     （升级脚本不会清空数据，业务数据依旧保留）
-- ============================================================================
SELECT TABLE_NAME AS '已就绪的表', TABLE_COMMENT AS '说明'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('blog_photo','blog_resource','sys_visit_stat','music_fallback','sys_level',
                     'sys_sign_log','sys_invite_code','sys_email_template','sys_point_log',
                     'sys_resource_unlock','sys_emoji','live2d_model','site_announcement')
ORDER BY TABLE_NAME;
