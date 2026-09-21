-- ============================================================================
-- bcBlog 数据库升级脚本 · 服务器升到当前版本（2026-09-21）
-- ----------------------------------------------------------------------------
-- 来源：服务器 2026-09-21 导出的 bc_blog.sql（仍是「16 张表」的早期博客版本），
--       用 tools/schema-diff 与本地当前版本逐项比对后生成，差异如下：
--         · 表：新增 28 张
--             —— 用户体系与运营：sys_level / sys_sign_log / sys_point_log / sys_invite_code /
--                sys_email_template / sys_emoji / sys_resource_unlock / page_background /
--                music_fallback / sys_login_ip
--             —— 后台安全：admin_api_key / admin_api_log
--             —— 沙盒世界：sandbox_world / character / location / act / item / relation /
--                interaction / gift / coin_log / memory / news / quest / shop_item /
--                shop_order / area_lock / attitude_log
--         · 字段：已有表新增 16 个
--             —— sys_user 8 个（安全密码、邮箱、经验、积分、等级、邀请权限、签到…）
--             —— blog_comment 4 个（用户、头像、等级快照）、blog_resource 3 个（封面、积分、详情）
--             —— ai_provider 1 个（归属管理员）
--         · 索引：新增 1 个（sys_user.uk_email）
--         · 字段定义不同：0 个（不用改类型）
--
-- 设计原则
--   1. 幂等：表 / 字段 / 索引都先查 information_schema，已存在的自动跳过；配置与默认数据用
--      INSERT IGNORE——重复执行不报错，中途失败可以直接重跑。
--   2. 只增不删：不 DROP 业务表、不 DELETE 数据，不覆盖后台已经改好的配置。
--   3. 不带敏感信息：邮箱授权码、各类 API Key 全部留空，请部署后到后台自行填写。
--   4. 沙盒默认关闭（sandbox_enabled = 0），旅人低语默认关闭，需要时在后台开启。
--
-- 执行方式（二选一，执行前先备份！）
--   A. Navicat：连接服务器数据库 → 选中 bc_blog → 右键「运行 SQL 文件」→ 选本文件
--   B. 命令行：mysql --default-character-set=utf8mb4 -uroot -p bc_blog < upgrade_20260921_server_batch.sql
--   （数据库名不是 bc_blog 的话，改下面的 USE 语句）
--
-- ★ 部署后必须在后台补的配置（不填会影响功能）
--   · 邮箱授权码：系统设置 → 邮箱（不填则「注册邮箱验证码」与「异常登录提醒」发不出去，
--     而注册默认要求邮箱验证 → 会导致新用户注册失败！）
--   · AI 服务商与模型：接口管理里添加服务商、填 Key；沙盒的系统服务商与各生成器模型
--   · 备案号：系统设置 → 备案号（前台页脚会显示）
--   · 超管二次验证默认开启：未设置「安全密码」时会回退用登录密码验证，可在系统设置里关闭
-- ============================================================================
SET NAMES utf8mb4;
USE `bc_blog`;

-- 幂等辅助过程：已存在则跳过
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

-- ----------------------------------------------------------------------------
-- 1. 新增表（28 张）
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `admin_api_key` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint NOT NULL COMMENT '所属管理员',
  `key_name` varchar(50) NOT NULL COMMENT '密钥名称，如 deepseek_api_key',
  `key_value` varchar(1000) DEFAULT NULL COMMENT '密钥值（程序加密后存储）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_key` (`admin_id`,`key_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员个人密钥';

CREATE TABLE IF NOT EXISTS `admin_api_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint DEFAULT NULL COMMENT '调用者ID，定时任务为空',
  `admin_name` varchar(100) DEFAULT NULL COMMENT '调用者用户名/昵称快照',
  `action` varchar(100) NOT NULL COMMENT '动作，如「沙盒·立即执行一次」',
  `source` varchar(20) NOT NULL DEFAULT 'manual' COMMENT 'manual 手动 / schedule 定时',
  `target` varchar(200) DEFAULT NULL COMMENT '使用的服务商或第三方接口（不含密钥）',
  `success` tinyint NOT NULL DEFAULT '1' COMMENT '是否成功',
  `message` varchar(300) DEFAULT NULL COMMENT '失败原因',
  `cost_ms` int DEFAULT NULL COMMENT '耗时毫秒',
  `output_chars` int DEFAULT NULL COMMENT '模型返回内容的字符数（观察输出长度与耗时用）',
  `ip` varchar(64) DEFAULT NULL COMMENT '调用方 IP',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin` (`admin_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2173 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API 调用审计';

CREATE TABLE IF NOT EXISTS `music_fallback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '姝屾洸鍚嶇О',
  `artist` varchar(200) DEFAULT NULL COMMENT '姝屾墜',
  `url` varchar(500) NOT NULL COMMENT '姝屾洸鐩撮摼',
  `pic` varchar(500) DEFAULT NULL COMMENT '灏侀潰鍥',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='榛樿?姝屾洸';

CREATE TABLE IF NOT EXISTS `page_background` (
  `page_key` varchar(32) NOT NULL COMMENT '页面标识：home / photos / resources / sandbox',
  `mode` varchar(10) NOT NULL DEFAULT 'follow' COMMENT 'follow=跟随前台默认壁纸，none=不使用壁纸，custom=使用 background_id',
  `background_id` bigint DEFAULT NULL COMMENT 'mode=custom 时使用的壁纸 id',
  `opacity` decimal(3,2) NOT NULL DEFAULT '1.00' COMMENT '壁纸不透明度 0.10~1.00',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`page_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='前台各页面独立背景设置';

CREATE TABLE IF NOT EXISTS `sandbox_act` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `character_id` bigint NOT NULL COMMENT '角色ID',
  `location_name` varchar(100) DEFAULT NULL COMMENT '这一步所处地点',
  `sub_location` varchar(100) DEFAULT NULL COMMENT '二级地点，AI 自行创作，如「东侧集市」',
  `x` int DEFAULT NULL COMMENT '这一步的横向坐标',
  `y` int DEFAULT NULL COMMENT '这一步的纵向坐标',
  `actions` varchar(1000) DEFAULT NULL COMMENT '做了什么（多条用换行分隔）',
  `inner_voice` varchar(1000) DEFAULT NULL COMMENT '心声',
  `companions` varchar(200) DEFAULT NULL COMMENT '这一步互动的其他角色，逗号分隔',
  `favor_change` varchar(200) DEFAULT NULL COMMENT '这一步的好感度变化，如「零 +3」',
  `item_change` varchar(300) DEFAULT NULL COMMENT '这一步的物品变化，如「获得 干粮 +1」',
  `status_json` varchar(1000) DEFAULT NULL COMMENT '这一步结束后的状态',
  `coin_change` int NOT NULL DEFAULT '0' COMMENT '本次金币变化，正为赚取、负为消耗',
  `combat_change` int NOT NULL DEFAULT '0' COMMENT '这一步战斗力的变化，0 表示没变',
  `summary` varchar(300) DEFAULT NULL COMMENT '一句话概括',
  `next_after_minutes` int NOT NULL DEFAULT '0' COMMENT '这一步之后 AI 期望的间隔分钟数，0 表示未指定',
  `next_after_reason` varchar(50) DEFAULT NULL COMMENT '间隔原因，如「睡觉」「赶路」',
  `news_ref` varchar(300) DEFAULT NULL COMMENT '这一步参考/听说的旅人纪闻标题，顿号分隔',
  `encounter` varchar(300) DEFAULT NULL COMMENT '本步由服务端注入的遭遇（危险地区掷骰命中时才会有）',
  `encounter_foe` varchar(60) DEFAULT NULL COMMENT '本步遭遇的对手名字/种类（AI 判断，例如 雾隐豹）',
  `luck` int DEFAULT NULL COMMENT '本步运气值 -3 大凶 ~ +3 大吉（服务端生成，前台展示）',
  `look` varchar(200) DEFAULT NULL COMMENT '这一步结束时角色的样子（用于回溯外貌变化）',
  `quest_event` varchar(200) DEFAULT NULL COMMENT '这一步的委托事件，如「完成委托：XXX」「接取委托：XXX」',
  `raw_response` text COMMENT 'AI 原始返回，便于排查问题',
  `from_ai` tinyint NOT NULL DEFAULT '1' COMMENT '是否来自 AI：1 是，0 为兜底记录',
  `manual` tinyint NOT NULL DEFAULT '0' COMMENT '是否管理员手动执行：1 是（不占用每日额度）',
  `reaction` tinyint NOT NULL DEFAULT '0' COMMENT '是否由其他角色的互动触发的回应回合：1 是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_character_time` (`character_id`,`create_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1723 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒行动记录';

CREATE TABLE IF NOT EXISTS `sandbox_area_lock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL COMMENT '所属世界',
  `area_name` varchar(100) NOT NULL COMMENT '一级地区名（地点名）',
  `holder` varchar(100) DEFAULT NULL COMMENT '当前持有者（角色名，便于排查卡锁）',
  `locked_at` datetime NOT NULL COMMENT '最近一次抢锁/续期时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_area` (`world_id`,`area_name`)
) ENGINE=InnoDB AUTO_INCREMENT=913 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒地区执行锁：同一地区同一时刻只允许一个角色在行动';

CREATE TABLE IF NOT EXISTS `sandbox_attitude_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint DEFAULT NULL COMMENT '所属世界',
  `character_id` bigint NOT NULL COMMENT '角色 ID',
  `character_name` varchar(90) DEFAULT NULL COMMENT '角色名快照，角色删除后仍可追溯',
  `act_id` bigint DEFAULT NULL COMMENT '触发这次变化的那条行动记录 ID',
  `kind` varchar(16) NOT NULL COMMENT 'power=对实力的看法 / wealth=对财富的看法',
  `old_view` varchar(120) DEFAULT NULL COMMENT '变化前的说法',
  `new_view` varchar(120) NOT NULL COMMENT '变化后的说法',
  `reason` varchar(200) DEFAULT NULL COMMENT '为什么变（AI 给的一句话，前台展示用）',
  `major` tinyint NOT NULL DEFAULT '0' COMMENT '1=重大事件触发（可突破冷却）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_attitude_char` (`character_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=45 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒角色态度（对实力/财富的看法）变化记录';

CREATE TABLE IF NOT EXISTS `sandbox_character` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `name` varchar(100) NOT NULL COMMENT '角色名',
  `title` varchar(100) DEFAULT NULL COMMENT '称号',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像 / 立绘地址',
  `appearance` varchar(500) DEFAULT NULL COMMENT '外貌描述（前台展示）',
  `current_look` varchar(200) DEFAULT NULL COMMENT '此刻的样子：穿着、干净程度、伤势外观、发型神态等（随行动更新）',
  `persona` text COMMENT '人设提示词，写法参考酒馆角色卡',
  `provider_id` bigint DEFAULT NULL COMMENT '绑定 AI 服务商ID',
  `model` varchar(100) DEFAULT NULL COMMENT '使用的模型',
  `temperature` decimal(3,2) NOT NULL DEFAULT '0.90' COMMENT '采样温度 0~2',
  `x` int NOT NULL DEFAULT '50' COMMENT '当前横向坐标百分比',
  `y` int NOT NULL DEFAULT '50' COMMENT '当前纵向坐标百分比',
  `location_name` varchar(100) DEFAULT NULL COMMENT '当前位置名称',
  `sub_location` varchar(100) DEFAULT NULL COMMENT '当前所在的二级地点',
  `goal` varchar(100) DEFAULT NULL COMMENT '当前目标（AI 维护，管理员可改）：例如"去晨雾森林采药"',
  `status_json` varchar(1000) DEFAULT NULL COMMENT '当前状态（JSON，内容由 AI 生成）',
  `coins` int NOT NULL DEFAULT '0' COMMENT '金币余额',
  `combat_power` int NOT NULL DEFAULT '10' COMMENT '战斗力：综合实力（战斗技巧、魔力、装备），默认 10',
  `equip_power` int NOT NULL DEFAULT '0' COMMENT '装备加成合计（冗余列，装备变更时按装备栏重算）',
  `encounter_exempt_locations` varchar(300) DEFAULT NULL COMMENT '免遭遇地点（一级地点名，英文逗号分隔）：在这个角色身上这些地方不刷遭遇',
  `next_run_time` datetime DEFAULT NULL COMMENT '下次 AI 行动时间',
  `next_reason` varchar(50) DEFAULT NULL COMMENT '下次行动的原因，如「睡觉」，前台展示用',
  `last_run_time` datetime DEFAULT NULL COMMENT '上次 AI 行动时间',
  `interval_min` int NOT NULL DEFAULT '45' COMMENT '行动间隔最小值（分钟）',
  `interval_max` int NOT NULL DEFAULT '75' COMMENT '行动间隔最大值（分钟）',
  `ai_interval_min` int DEFAULT NULL COMMENT '该角色 AI 间隔下限（分钟），留空用全局设置',
  `ai_interval_max` int DEFAULT NULL COMMENT '该角色 AI 间隔上限（分钟），留空用全局设置',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最后一次调用失败原因',
  `fail_count` int NOT NULL DEFAULT '0' COMMENT '连续失败次数：AI 调用连续失败时累加，成功后清零',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1 启用，0 停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `running_at` datetime DEFAULT NULL COMMENT '正在执行行动的抢锁时间，执行结束会清空（并发保护）',
  `power_view` varchar(60) DEFAULT NULL COMMENT '对自身实力的看法（AI 生成/管理员可改，会写进行动提示词）',
  `wealth_view` varchar(60) DEFAULT NULL COMMENT '对金钱财富的看法（AI 生成/管理员可改，会写进行动提示词）',
  PRIMARY KEY (`id`),
  KEY `idx_world` (`world_id`)
) ENGINE=InnoDB AUTO_INCREMENT=260 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒角色';

CREATE TABLE IF NOT EXISTS `sandbox_coin_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `character_id` bigint NOT NULL COMMENT '角色ID',
  `world_id` bigint DEFAULT NULL COMMENT '所属世界',
  `user_id` bigint DEFAULT NULL COMMENT '贡献人（赚取/消耗为空）',
  `user_name` varchar(100) DEFAULT NULL COMMENT '贡献人昵称快照',
  `type` varchar(20) NOT NULL COMMENT '类型：contribute 贡献 / earn 赚取 / spend 消耗 / admin 管理员调整',
  `coins` int NOT NULL COMMENT '金币变化，正为增加、负为减少',
  `points_cost` int NOT NULL DEFAULT '0' COMMENT '贡献消耗的积分',
  `balance` int NOT NULL DEFAULT '0' COMMENT '变化后余额',
  `remark` varchar(200) DEFAULT NULL COMMENT '说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_character` (`character_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_world` (`world_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1509 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒金币流水';

CREATE TABLE IF NOT EXISTS `sandbox_gift` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `character_id` bigint NOT NULL COMMENT '收礼角色',
  `item_name` varchar(100) NOT NULL COMMENT '礼物名',
  `item_description` varchar(300) DEFAULT NULL COMMENT '礼物描述',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `points_cost` int NOT NULL DEFAULT '0' COMMENT '消耗积分',
  `coin_price` int NOT NULL DEFAULT '0' COMMENT '商品单价（金币）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_character_time` (`character_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒异世界礼物（写进角色提示词）';

CREATE TABLE IF NOT EXISTS `sandbox_interaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `character_id` bigint NOT NULL COMMENT '角色ID',
  `world_id` bigint DEFAULT NULL COMMENT '所属世界',
  `user_id` bigint NOT NULL COMMENT '留言用户ID',
  `user_name` varchar(100) DEFAULT NULL COMMENT '用户昵称快照',
  `user_avatar` varchar(500) DEFAULT NULL COMMENT '用户头像快照',
  `content` varchar(500) NOT NULL COMMENT '低语内容',
  `points_cost` int NOT NULL DEFAULT '0' COMMENT '消耗积分',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_character` (`character_id`),
  KEY `idx_world` (`world_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒旅人低语';

CREATE TABLE IF NOT EXISTS `sandbox_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `character_id` bigint NOT NULL COMMENT '角色ID',
  `name` varchar(100) NOT NULL COMMENT '物品名称',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `rarity` tinyint NOT NULL DEFAULT '1' COMMENT '品质：1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说',
  `slot` varchar(16) NOT NULL DEFAULT 'none' COMMENT '装备槽位：none 非装备 / weapon 武器 / offhand 副手 / armor 护具 / accessory 饰品',
  `power_bonus` int NOT NULL DEFAULT '0' COMMENT '装备加成（战斗力）；0 表示不是装备或没有加成',
  `equipped` tinyint NOT NULL DEFAULT '0' COMMENT '1 = 已装备在角色装备栏里',
  `broken` tinyint NOT NULL DEFAULT '0' COMMENT '1 = 已破损（不能再装备，加成按 0 算）',
  `icon` varchar(500) DEFAULT NULL COMMENT '物品图标图片地址，为空时前台按物品名自动匹配图标',
  `description` varchar(300) DEFAULT NULL COMMENT '物品说明（管理员可补充）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_char_item` (`character_id`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=1469 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒角色背包';

CREATE TABLE IF NOT EXISTS `sandbox_location` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `name` varchar(100) NOT NULL COMMENT '地点名称',
  `icon` varchar(500) DEFAULT NULL COMMENT '地点图标：内置图标 key 或上传的图片地址',
  `x` int NOT NULL DEFAULT '50' COMMENT '横向坐标百分比 0~100',
  `y` int NOT NULL DEFAULT '50' COMMENT '纵向坐标百分比 0~100',
  `width` int NOT NULL DEFAULT '0' COMMENT '区域宽度百分比；0 表示单点',
  `height` int NOT NULL DEFAULT '0' COMMENT '区域高度百分比；0 表示单点',
  `polygon` text COMMENT '多边形区域顶点 JSON [[x,y],...]（百分比），为空表示按矩形区域判定',
  `description` varchar(500) DEFAULT NULL COMMENT '地点描述，会作为 AI 行动参考',
  `danger_level` tinyint NOT NULL DEFAULT '1' COMMENT '危险度：0 安全 / 1 较低 / 2 较高 / 3 危险（影响 AI 是否遭遇战斗）',
  `power_min` int DEFAULT NULL COMMENT '该地点生物的战斗力下限（遭遇对手强度落在这个区间内）',
  `power_max` int DEFAULT NULL COMMENT '该地点生物的战斗力上限',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序，越小越靠前',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_world` (`world_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1011 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒地图地点';

CREATE TABLE IF NOT EXISTS `sandbox_memory` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `character_id` bigint NOT NULL COMMENT '角色ID',
  `memory_date` date NOT NULL COMMENT '记忆对应的日期',
  `summary` text COMMENT '当天的记忆总结',
  `act_count` int NOT NULL DEFAULT '0' COMMENT '当天行动条数',
  `from_ai` tinyint NOT NULL DEFAULT '1' COMMENT '是否由 AI 总结：1 是，0 为兜底拼接',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_char_date` (`character_id`,`memory_date`),
  KEY `idx_memory_date` (`memory_date`)
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒角色每日记忆';

CREATE TABLE IF NOT EXISTS `sandbox_news` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `title` varchar(200) NOT NULL COMMENT '一句话事件，前台展示',
  `content` varchar(500) DEFAULT NULL COMMENT '补充说明',
  `location_name` varchar(100) DEFAULT NULL COMMENT '事件发生地点',
  `x` int DEFAULT NULL COMMENT '事件坐标 X（用于计算与角色的距离）',
  `y` int DEFAULT NULL COMMENT '事件坐标 Y',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '重要度：1 普通 / 2 重要 / 3 重大',
  `source` varchar(20) NOT NULL DEFAULT 'ai' COMMENT '来源：ai 自动生成 / admin 管理员添加',
  `news_date` date NOT NULL COMMENT '归属日期（只展示当天）',
  `pinned` tinyint NOT NULL DEFAULT '0' COMMENT '是否置顶',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_date` (`news_date`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=174 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒旅人纪闻';

CREATE TABLE IF NOT EXISTS `sandbox_quest` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `batch_time` datetime NOT NULL COMMENT '所属批次（刷新时间）：前台可接只展示最新一批',
  `title` varchar(120) NOT NULL COMMENT '委托标题（角色按标题一字不差地接取）',
  `description` varchar(500) DEFAULT NULL COMMENT '委托详情（背景、注意事项）',
  `quest_type` varchar(20) NOT NULL DEFAULT 'other' COMMENT 'hunt 讨伐 / gather 采集 / escort 护送 / explore 探索 / chore 杂务 / other',
  `difficulty` tinyint NOT NULL DEFAULT '1' COMMENT '难度 1~5（前台按星展示）',
  `location_name` varchar(90) DEFAULT NULL COMMENT '目标地区（一级地点名）',
  `target` varchar(200) DEFAULT NULL COMMENT '目标与数量（一句话，如「清除 3 只影狼」）',
  `power` int DEFAULT NULL COMMENT '对手战斗力（讨伐类；服务端会夹到该地点的战力区间）',
  `reward_coins` int NOT NULL DEFAULT '0' COMMENT '奖励金币',
  `reward_items` varchar(500) DEFAULT NULL COMMENT '奖励物品 JSON：[{"name":"","quantity":1,"description":""}]',
  `progress` int NOT NULL DEFAULT '0' COMMENT '完成进度 0~100（服务端只增不减）',
  `progress_note` varchar(200) DEFAULT NULL COMMENT 'AI 最近一次对进度的判断（一句话）',
  `status` varchar(20) NOT NULL DEFAULT 'open' COMMENT 'open 可接 / taken 接取中 / completed 已完成',
  `taker_id` bigint DEFAULT NULL COMMENT '接取人角色 ID',
  `taker_name` varchar(90) DEFAULT NULL COMMENT '接取人角色名（快照）',
  `taken_at` datetime DEFAULT NULL COMMENT '接取时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `completion_note` varchar(300) DEFAULT NULL COMMENT '完成经过（一句话，前台展示）',
  `abandoned_by` varchar(200) DEFAULT NULL COMMENT '曾接取后放弃的角色名（顿号分隔，用于提示"你之前放弃过它"）',
  `source` varchar(20) NOT NULL DEFAULT 'admin' COMMENT 'ai / admin',
  `pinned` tinyint NOT NULL DEFAULT '0' COMMENT '管理员置顶',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否上板',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_world_status` (`world_id`,`status`),
  KEY `idx_world_batch` (`world_id`,`batch_time`),
  KEY `idx_taker` (`taker_id`)
) ENGINE=InnoDB AUTO_INCREMENT=674 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒旅人委托板';

CREATE TABLE IF NOT EXISTS `sandbox_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `character_id` bigint NOT NULL COMMENT '角色ID（好感度的持有方）',
  `target_id` bigint NOT NULL COMMENT '对象角色ID',
  `favor` int NOT NULL DEFAULT '0' COMMENT '好感度 -100~100',
  `last_change` int NOT NULL DEFAULT '0' COMMENT '上一次实际生效的好感度变化',
  `last_change_time` datetime DEFAULT NULL COMMENT '上一次好感度变化时间',
  `remark` varchar(200) DEFAULT NULL COMMENT '管理员备注，例如「在集市认识的酒友」',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pair` (`character_id`,`target_id`),
  KEY `idx_target` (`target_id`)
) ENGINE=InnoDB AUTO_INCREMENT=138 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒角色好感度';

CREATE TABLE IF NOT EXISTS `sandbox_shop_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `batch_time` datetime NOT NULL COMMENT '所属批次（刷新时间）：前台只展示最新一批',
  `name` varchar(100) NOT NULL COMMENT '商品名',
  `description` varchar(300) DEFAULT NULL COMMENT '描述（含一句来源小故事）',
  `icon` varchar(500) DEFAULT NULL COMMENT '自定义图标；为空时按名字匹配 emoji',
  `rarity` tinyint NOT NULL DEFAULT '1' COMMENT '品质 1 普通 ~ 5 传说',
  `slot` varchar(16) NOT NULL DEFAULT 'none' COMMENT '装备槽位：none 非装备 / weapon 武器 / offhand 副手 / armor 护具 / accessory 饰品',
  `power_bonus` int NOT NULL DEFAULT '0' COMMENT '装备加成（战斗力）；0 表示不是装备',
  `price` int NOT NULL DEFAULT '1' COMMENT '现价（积分）',
  `original_price` int DEFAULT NULL COMMENT '原价（打折时显示划线价）',
  `stock` int NOT NULL DEFAULT '1' COMMENT '剩余库存',
  `total_stock` int NOT NULL DEFAULT '1' COMMENT '本批总量',
  `source` varchar(20) NOT NULL DEFAULT 'ai' COMMENT 'ai / admin',
  `pinned` tinyint NOT NULL DEFAULT '0' COMMENT '管理员置顶',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否上架',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_world_batch` (`world_id`,`batch_time`),
  KEY `idx_world_enabled` (`world_id`,`enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=4595 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒旅人集市商品';

CREATE TABLE IF NOT EXISTS `sandbox_shop_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `item_id` bigint NOT NULL COMMENT '商品 id',
  `item_name` varchar(100) NOT NULL COMMENT '商品名（快照）',
  `user_id` bigint DEFAULT NULL COMMENT '购买者（前台可见，角色提示词里绝不出现）',
  `user_name` varchar(100) DEFAULT NULL COMMENT '购买者昵称快照',
  `character_id` bigint NOT NULL COMMENT '收礼角色',
  `character_name` varchar(100) DEFAULT NULL COMMENT '收礼角色名快照',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `points_cost` int NOT NULL DEFAULT '0' COMMENT '消耗积分（管理员为 0）',
  `coin_price` int NOT NULL DEFAULT '0' COMMENT '商品单价（金币）',
  `buyer_type` varchar(20) NOT NULL DEFAULT 'user' COMMENT '购买者：user=前台用户赠送 / character=沙盒角色自购',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_world_time` (`world_id`,`create_time`),
  KEY `idx_item` (`item_id`),
  KEY `idx_character` (`character_id`)
) ENGINE=InnoDB AUTO_INCREMENT=614 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒旅人集市购买记录';

CREATE TABLE IF NOT EXISTS `sandbox_world` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '' COMMENT '世界名称',
  `description` varchar(500) DEFAULT NULL COMMENT '世界简介（前台展示）',
  `map_image` varchar(500) DEFAULT NULL COMMENT '地图背景图地址',
  `world_prompt` text COMMENT '世界设定：写给 AI 的世界观、规则与文风',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1 启用，0 停用',
  `portal_visible` tinyint NOT NULL DEFAULT '1' COMMENT '前台是否可见：1 出现在前台世界下拉（可只看历史），0 完全隐藏',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `mana_label` varchar(20) DEFAULT NULL COMMENT '「魔力」条在本世界的叫法：留空=没有这条属性，NULL=按默认「魔力」',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1184 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒世界';

CREATE TABLE IF NOT EXISTS `sys_email_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `scenario` varchar(50) NOT NULL COMMENT '场景编码，如 register_code',
  `name` varchar(100) NOT NULL COMMENT '模板名称',
  `subject` varchar(200) NOT NULL COMMENT '邮件主题',
  `background_image` varchar(500) DEFAULT NULL COMMENT '背景图片地址',
  `overlay_opacity` decimal(3,2) NOT NULL DEFAULT '0.85' COMMENT '内容卡片背景透明度 0.1~1',
  `content_html` text COMMENT '正文 HTML，支持 {{变量}}',
  `variables` varchar(500) DEFAULT NULL COMMENT '可用变量说明',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `active` tinyint NOT NULL DEFAULT '0' COMMENT '是否为该场景当前启用模板',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_scenario` (`scenario`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邮件模板';

CREATE TABLE IF NOT EXISTS `sys_emoji` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pack` varchar(100) DEFAULT NULL COMMENT '表情包名称',
  `name` varchar(100) DEFAULT NULL COMMENT '表情名称',
  `url` varchar(500) NOT NULL COMMENT '表情图片地址',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pack` (`pack`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表情包';

CREATE TABLE IF NOT EXISTS `sys_invite_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL COMMENT '邀请码',
  `creator_id` bigint NOT NULL COMMENT '创建人ID',
  `creator_name` varchar(100) DEFAULT NULL COMMENT '创建人昵称',
  `use_count` int NOT NULL DEFAULT '0' COMMENT '使用次数',
  `last_used_at` datetime DEFAULT NULL COMMENT '最后使用时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  UNIQUE KEY `uk_creator` (`creator_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邀请码';

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
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='等级配置';

CREATE TABLE IF NOT EXISTS `sys_login_ip` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '管理员ID',
  `ip` varchar(64) NOT NULL COMMENT '登录IP',
  `region` varchar(100) DEFAULT NULL COMMENT 'IP 归属地（仅异常时查询并缓存）',
  `login_count` int NOT NULL DEFAULT '1' COMMENT '该IP登录次数',
  `last_login_time` datetime DEFAULT NULL COMMENT '最近登录时间',
  `last_notify_time` datetime DEFAULT NULL COMMENT '最近一次异常通知时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_ip` (`user_id`,`ip`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员登录IP记录';

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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分流水';

CREATE TABLE IF NOT EXISTS `sys_resource_unlock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `resource_id` bigint NOT NULL COMMENT '资源ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_resource` (`user_id`,`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源解锁记录';

CREATE TABLE IF NOT EXISTS `sys_sign_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `sign_date` date NOT NULL COMMENT '签到日期',
  `exp` int NOT NULL DEFAULT '0' COMMENT '获得经验',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`sign_date`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签到记录';

-- ----------------------------------------------------------------------------
-- 2. 新增字段（16 个）
-- ----------------------------------------------------------------------------
CALL bcblog_add_col('ai_provider', 'owner_id', 'bigint DEFAULT NULL COMMENT ''归属管理员ID，NULL 表示系统服务商'' AFTER `is_default`');
CALL bcblog_add_col('blog_comment', 'user_id', 'bigint DEFAULT NULL COMMENT ''登录用户ID'' AFTER `create_time`');
CALL bcblog_add_col('blog_comment', 'avatar', 'varchar(500) DEFAULT NULL COMMENT ''头像快照'' AFTER `user_id`');
CALL bcblog_add_col('blog_comment', 'level', 'int DEFAULT NULL COMMENT ''等级快照'' AFTER `avatar`');
CALL bcblog_add_col('blog_comment', 'level_name', 'varchar(50) DEFAULT NULL COMMENT ''等级名称快照'' AFTER `level`');
CALL bcblog_add_col('blog_resource', 'cover', 'varchar(500) DEFAULT NULL COMMENT ''封面图'' AFTER `description`');
CALL bcblog_add_col('blog_resource', 'points', 'int NOT NULL DEFAULT ''1'' COMMENT ''前往资源所需积分'' AFTER `cover`');
CALL bcblog_add_col('blog_resource', 'content', 'text COMMENT ''资源详情内容（HTML）'' AFTER `points`');
CALL bcblog_add_col('sys_user', 'security_password', 'varchar(100) DEFAULT NULL COMMENT ''安全密码(BCrypt)，用于敏感操作二次验证'' AFTER `password`');
CALL bcblog_add_col('sys_user', 'email', 'varchar(100) DEFAULT NULL COMMENT ''邮箱'' AFTER `menus`');
CALL bcblog_add_col('sys_user', 'exp', 'int NOT NULL DEFAULT ''0'' COMMENT ''经验值'' AFTER `email`');
CALL bcblog_add_col('sys_user', 'points', 'int NOT NULL DEFAULT ''0'' COMMENT ''积分'' AFTER `exp`');
CALL bcblog_add_col('sys_user', 'level', 'int NOT NULL DEFAULT ''1'' COMMENT ''等级'' AFTER `points`');
CALL bcblog_add_col('sys_user', 'can_invite', 'tinyint NOT NULL DEFAULT ''0'' COMMENT ''是否有邀请码权限'' AFTER `level`');
CALL bcblog_add_col('sys_user', 'sign_days', 'int NOT NULL DEFAULT ''0'' COMMENT ''累计签到天数'' AFTER `can_invite`');
CALL bcblog_add_col('sys_user', 'last_sign_date', 'date DEFAULT NULL COMMENT ''最后签到日期'' AFTER `sign_days`');

-- ----------------------------------------------------------------------------
-- 3. 新增索引（1 个）
-- ----------------------------------------------------------------------------
-- 注意：UNIQUE 索引要求数据里没有重复值，若服务器上存在重复数据会执行失败，请先清理
CALL bcblog_add_idx('sys_user', 'uk_email', '  UNIQUE KEY `uk_email` (`email`)');

-- 3.5 每个世界一套独立的沙盒参数（方案 C，见 upgrade_069）
--     没跑过 069 也没关系，这里会补上；跑过了则会自动跳过（过程里先查了 information_schema）
CALL bcblog_add_col('sandbox_world', 'settings_json',
    'TEXT NULL COMMENT ''这个世界的沙盒参数（JSON）；为空表示全部沿用全局默认''');
-- ----------------------------------------------------------------------------
-- 3.5 历史数据清理：把「只有超管能用」的菜单键从普通管理员身上摘掉
-- 这些菜单分配出去也只会让页面显示成「点进去 403」的标签，留着容易误判权限。
-- 服务器上现有的普通管理员（beicheng）本来就没有这些键，属于"跑一遍更干净"。
-- ----------------------------------------------------------------------------
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
-- 4. 配置项默认值
-- ----------------------------------------------------------------------------
-- 取值说明：凡是代码里写了默认值的，这里用代码默认值（不是本地调试时调过的值）；
--           清理天数用你之前定下来的口径（登录日志 7 天、积分流水 30 天…）；
--           沙盒总开关与旅人低语默认关闭，自检默认「命中才查」。
-- 全部是 INSERT IGNORE：服务器后台已经改过的配置不会被覆盖。
-- ----------------------------------------------------------------------------
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES    ('admin_login_alert_enabled', '1', '系统默认值（后台可改）'),
    ('admin_login_alert_fail_times', '3', '系统默认值（后台可改）'),
    ('admin_login_alert_night_end', '06:00', '系统默认值（后台可改）'),
    ('admin_login_alert_night_start', '00:00', '系统默认值（后台可改）'),
    ('admin_security_enabled', '1', '系统默认值（后台可改）'),
    ('admin_security_verify_minutes', '30', '系统默认值（后台可改）'),
    ('admin_single_login', '1', '系统默认值（后台可改）'),
    ('cleanup_admin_api_log_days', '3', '系统默认值（后台可改）'),
    ('cleanup_enabled', '1', '系统默认值（后台可改）'),
    ('cleanup_login_log_days', '7', '系统默认值（后台可改）'),
    ('cleanup_point_log_days', '30', '系统默认值（后台可改）'),
    ('cleanup_sandbox_act_days', '7', '系统默认值（后台可改）'),
    ('cleanup_sandbox_memory_days', '30', '系统默认值（后台可改）'),
    ('cleanup_sandbox_news_days', '1', '系统默认值（后台可改）'),
    ('cleanup_sandbox_quest_days', '3', '系统默认值（后台可改）'),
    ('cleanup_sandbox_shop_days', '3', '系统默认值（后台可改）'),
    ('cleanup_sign_log_days', '30', '系统默认值（后台可改）'),
    ('cleanup_time', '03:30', '系统默认值（后台可改）'),
    ('cleanup_visit_stat_days', '30', '系统默认值（后台可改）'),
    ('comment_exp', '3', '系统默认值（后台可改）'),
    ('comment_exp_limit', '3', '系统默认值（后台可改）'),
    ('email_host', 'smtp.qq.com', '系统默认值（后台可改）'),
    ('email_port', '465', '系统默认值（后台可改）'),
    ('email_ssl', '1', '系统默认值（后台可改）'),
    ('register_email_verify', '1', '系统默认值（后台可改）'),
    ('register_invite_required', '0', '系统默认值（后台可改）'),
    ('sandbox_ai_interval_enabled', '1', '系统默认值（后台可改）'),
    ('sandbox_ai_interval_max', '720', '系统默认值（后台可改）'),
    ('sandbox_ai_interval_min', '15', '系统默认值（后台可改）'),
    ('sandbox_area_lock_minutes', '15', '系统默认值（后台可改）'),
    ('sandbox_attitude_cooldown_hours', '12', '系统默认值（后台可改）'),
    ('sandbox_attitude_enabled', 'on', '系统默认值（后台可改）'),
    ('sandbox_batch_window_minutes', '5', '系统默认值（后台可改）'),
    ('sandbox_chain_limit_per_round', '3', '系统默认值（后台可改）'),
    ('sandbox_chain_max_depth', '1', '系统默认值（后台可改）'),
    ('sandbox_coin_max_points', '100', '系统默认值（后台可改）'),
    ('sandbox_coin_rate', '1', '系统默认值（后台可改）'),
    ('sandbox_daily_limit', '12', '系统默认值（后台可改）'),
    ('sandbox_draft_mode', 'on', '系统默认值（后台可改）'),
    ('sandbox_enabled', '0', '系统默认值（后台可改）'),
    ('sandbox_encounter_chance_0', '0', '系统默认值（后台可改）'),
    ('sandbox_encounter_chance_2', '30', '系统默认值（后台可改）'),
    ('sandbox_encounter_chance_3', '55', '系统默认值（后台可改）'),
    ('sandbox_encounter_daily_limit', '3', '系统默认值（后台可改）'),
    ('sandbox_encounter_enabled', 'on', '系统默认值（后台可改）'),
    ('sandbox_equip_bonus_by_rarity', 'SandboxEquip.DEFAULT_BONUS_TABLE', '系统默认值（后台可改）'),
    ('sandbox_equip_enabled', '1', '系统默认值（后台可改）'),
    ('sandbox_fail_backoff_base_minutes', '15', '系统默认值（后台可改）'),
    ('sandbox_fail_backoff_max_minutes', '120', '系统默认值（后台可改）'),
    ('sandbox_generator_flow', 'three', '系统默认值（后台可改）'),
    ('sandbox_idle_train_gain_max', '2', '系统默认值（后台可改）'),
    ('sandbox_injury_dying_minutes', '360', '系统默认值（后台可改）'),
    ('sandbox_injury_heavy_minutes', '180', '系统默认值（后台可改）'),
    ('sandbox_interaction_cooldown_hours', '4', '系统默认值（后台可改）'),
    ('sandbox_interval_max', '75', '系统默认值（后台可改）'),
    ('sandbox_interval_min', '45', '系统默认值（后台可改）'),
    ('sandbox_km_map_width', '200', '系统默认值（后台可改）'),
    ('sandbox_look_enabled', 'on', '系统默认值（后台可改）'),
    ('sandbox_luck_day_range', '2', '系统默认值（后台可改）'),
    ('sandbox_luck_enabled', 'on', '系统默认值（后台可改）'),
    ('sandbox_luck_step_jitter', '1', '系统默认值（后台可改）'),
    ('sandbox_max_earn_per_act', '30', '系统默认值（后台可改）'),
    ('sandbox_max_spend_per_act', '10', '系统默认值（后台可改）'),
    ('sandbox_memory_delete_acts', '0', '系统默认值（后台可改）'),
    ('sandbox_memory_enabled', '1', '系统默认值（后台可改）'),
    ('sandbox_memory_prompt_days', '5', '系统默认值（后台可改）'),
    ('sandbox_memory_time', '23:50', '系统默认值（后台可改）'),
    ('sandbox_news_auto_enabled', '1', '系统默认值（后台可改）'),
    ('sandbox_news_auto_time', '07:00', '系统默认值（后台可改）'),
    ('sandbox_news_enabled', '1', '系统默认值（后台可改）'),
    ('sandbox_news_interval_hours', '24', '系统默认值（后台可改）'),
    ('sandbox_news_per_generate', '3', '系统默认值（后台可改）'),
    ('sandbox_news_title', '旅人纪闻', '系统默认值（后台可改）'),
    ('sandbox_night_end', '07:00', '系统默认值（后台可改）'),
    ('sandbox_night_start', '02:00', '系统默认值（后台可改）'),
    ('sandbox_prompt_char_limit', '9000', '系统默认值（后台可改）'),
    ('sandbox_prompt_full_act_steps', '1', '系统默认值（后台可改）'),
    ('sandbox_quest_auto_enabled', '1', '系统默认值（后台可改）'),
    ('sandbox_quest_auto_time', '09:00', '系统默认值（后台可改）'),
    ('sandbox_quest_combat_gain', 'hunt:0-1,0-1,0-2,1-3,1-3|explore:0,0-1,0-2,0-2,1-3|escort:0,0,0-1,0-1,0-2|gather:0,0,0,0,0-1|chore:0,0,0,0,0', '系统默认值（后台可改）'),
    ('sandbox_quest_enabled', '1', '系统默认值（后台可改）'),
    ('sandbox_quest_interval_hours', '24', '系统默认值（后台可改）'),
    ('sandbox_quest_per_generate', '4', '系统默认值（后台可改）'),
    ('sandbox_quest_progress_step_max', '40', '系统默认值（后台可改）'),
    ('sandbox_quest_selfcheck', '1', '系统默认值（后台可改）'),
    ('sandbox_quest_step_max_by_difficulty', 'DEFAULT_QUEST_STEP_TABLE', '系统默认值（后台可改）'),
    ('sandbox_quest_title', '旅人委托板', '系统默认值（后台可改）'),
    ('sandbox_quest_visible_count', '8', '系统默认值（后台可改）'),
    ('sandbox_reaction_cooldown_minutes', '15', '系统默认值（后台可改）'),
    ('sandbox_run_lock_minutes', '5', '系统默认值（后台可改）'),
    ('sandbox_shop_auto_enabled', '1', '系统默认值（后台可改）'),
    ('sandbox_shop_auto_time', '08:00', '系统默认值（后台可改）'),
    ('sandbox_shop_buy_per_day', '2', '系统默认值（后台可改）'),
    ('sandbox_shop_enabled', '1', '系统默认值（后台可改）'),
    ('sandbox_shop_interval_hours', '24', '系统默认值（后台可改）'),
    ('sandbox_shop_limit_per_character', '1', '系统默认值（后台可改）'),
    ('sandbox_shop_per_generate', '3', '系统默认值（后台可改）'),
    ('sandbox_shop_title', '旅人集市', '系统默认值（后台可改）'),
    ('sandbox_social_max_km', '30', '系统默认值（后台可改）'),
    ('sandbox_social_same_area_only', '1', '系统默认值（后台可改）'),
    ('sandbox_step_selfcheck', '1', '系统默认值（后台可改）'),
    ('sandbox_step_selfcheck_mode', 'suspicious', '系统默认值（后台可改）'),
    ('sandbox_think_stage', 'on', '系统默认值（后台可改）'),
    ('sandbox_travel_speeds', 'DEFAULT_TRAVEL_SPEEDS', '系统默认值（后台可改）'),
    ('sandbox_verify_enabled', '1', '系统默认值（后台可改）'),
    ('sandbox_verify_mode', 'suspicious', '系统默认值（后台可改）'),
    ('sandbox_whisper_enabled', '0', '系统默认值（后台可改）'),
    ('sandbox_whisper_points', '1', '系统默认值（后台可改）'),
    ('sign_exp', '5', '系统默认值（后台可改）');

-- 邮箱的两项非敏感配置（服务器站点名是「圣樱」，与发送者名称一致）；
-- 邮箱授权码是敏感信息、且本地是加密存储的，必须在后台重新填一次
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('email_username', '1127090694@qq.com', '发件邮箱账号（授权码请在后台填写）'),
    ('email_sender_name', '圣樱', '发件人显示名，与站点名保持一致');
-- ============================================================================
-- 5. 默认数据：等级档位 / 兜底歌单 / 邮件模板（INSERT IGNORE，已存在则跳过）
-- ----------------------------------------------------------------------------
-- 邮件模板就是「注册验证码（二次元新风格）」与「异常登录提醒」这两个，
-- 带 {{siteName}} 之类的占位符，站点名按后台配置替换；里面没有写死域名。
-- ============================================================================
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `sys_level` WRITE;
/*!40000 ALTER TABLE `sys_level` DISABLE KEYS */;
INSERT  IGNORE INTO `sys_level` (`id`, `level`, `name`, `exp_required`, `icon`, `color`, `create_time`) VALUES (1,1,'初来乍到',0,NULL,NULL,'2026-09-14 22:26:39');
INSERT  IGNORE INTO `sys_level` (`id`, `level`, `name`, `exp_required`, `icon`, `color`, `create_time`) VALUES (2,2,'渐入佳境',100,NULL,NULL,'2026-09-14 22:26:39');
INSERT  IGNORE INTO `sys_level` (`id`, `level`, `name`, `exp_required`, `icon`, `color`, `create_time`) VALUES (3,3,'小有名气',300,NULL,NULL,'2026-09-14 22:26:39');
INSERT  IGNORE INTO `sys_level` (`id`, `level`, `name`, `exp_required`, `icon`, `color`, `create_time`) VALUES (4,4,'活跃之星',600,NULL,NULL,'2026-09-14 22:26:39');
INSERT  IGNORE INTO `sys_level` (`id`, `level`, `name`, `exp_required`, `icon`, `color`, `create_time`) VALUES (5,5,'资深常客',1000,NULL,NULL,'2026-09-14 22:26:39');
INSERT  IGNORE INTO `sys_level` (`id`, `level`, `name`, `exp_required`, `icon`, `color`, `create_time`) VALUES (6,6,'意见领袖',1600,NULL,NULL,'2026-09-14 22:26:39');
INSERT  IGNORE INTO `sys_level` (`id`, `level`, `name`, `exp_required`, `icon`, `color`, `create_time`) VALUES (7,7,'社区骨干',2400,NULL,NULL,'2026-09-14 22:26:39');
INSERT  IGNORE INTO `sys_level` (`id`, `level`, `name`, `exp_required`, `icon`, `color`, `create_time`) VALUES (8,8,'荣誉元老',3500,NULL,NULL,'2026-09-14 22:26:39');
INSERT  IGNORE INTO `sys_level` (`id`, `level`, `name`, `exp_required`, `icon`, `color`, `create_time`) VALUES (9,9,'传奇存在',5000,NULL,NULL,'2026-09-14 22:26:39');
INSERT  IGNORE INTO `sys_level` (`id`, `level`, `name`, `exp_required`, `icon`, `color`, `create_time`) VALUES (10,10,'永恒传说',7000,NULL,NULL,'2026-09-14 22:26:39');
/*!40000 ALTER TABLE `sys_level` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `music_fallback` WRITE;
/*!40000 ALTER TABLE `music_fallback` DISABLE KEYS */;
INSERT  IGNORE INTO `music_fallback` (`id`, `title`, `artist`, `url`, `pic`, `create_time`) VALUES (1,'起风了','买辣椒也用券','https://music.163.com/song/media/outer/url?id=1330348068.mp3',NULL,'2026-09-14 20:02:58');
INSERT  IGNORE INTO `music_fallback` (`id`, `title`, `artist`, `url`, `pic`, `create_time`) VALUES (2,'少年','Dave','https://music.163.com/song/media/outer/url?id=2614935159.mp3',NULL,'2026-09-14 20:02:58');
INSERT  IGNORE INTO `music_fallback` (`id`, `title`, `artist`, `url`, `pic`, `create_time`) VALUES (3,'卡农（经典钢琴版）','dylanf','https://music.163.com/song/media/outer/url?id=478507889.mp3',NULL,'2026-09-14 20:02:58');
INSERT  IGNORE INTO `music_fallback` (`id`, `title`, `artist`, `url`, `pic`, `create_time`) VALUES (4,'七点钟','齐豫','https://music.163.com/song/media/outer/url?id=108787.mp3',NULL,'2026-09-14 20:02:58');
/*!40000 ALTER TABLE `music_fallback` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `sys_email_template` WRITE;
/*!40000 ALTER TABLE `sys_email_template` DISABLE KEYS */;
INSERT  IGNORE INTO `sys_email_template` (`id`, `scenario`, `name`, `subject`, `background_image`, `overlay_opacity`, `content_html`, `variables`, `enabled`, `active`, `create_time`, `update_time`) VALUES (1,'register_code','注册验证码','【{{siteName}}】邮箱验证码',NULL,0.90,'<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n  <title>二次元邮箱验证码 · 新风格</title>\n</head>\n<body style=\"margin:0; padding:0; background-color:#f5eef8; font-family: \'Segoe UI\', \'Yu Gothic\', \'Hiragino Sans\', \'Meiryo\', system-ui, sans-serif;\">\n  <div style=\"max-width:600px; margin:0 auto; padding:28px 16px; background-color:#f5eef8;\">\n\n    <!-- 主卡片：圆角柔和 + 渐变边框效果 -->\n    <div style=\"background: linear-gradient(135deg, #ffffff, #fef7ff); border-radius:36px; box-shadow:0 20px 40px rgba(186, 140, 200, 0.18); border:1px solid #f0d9f0; padding:0; overflow:hidden; position:relative;\">\n\n      <!-- 顶部彩条：二次元渐变横幅 -->\n      <div style=\"height:10px; background: linear-gradient(90deg, #ffb6d9, #d9b8ff, #b8e0ff, #ffb6d9); background-size: 300% 100%;\"></div>\n\n      <!-- 内容区 -->\n      <div style=\"padding:28px 30px 26px;\">\n\n        <!-- 头部：左图标 + 标题（左右布局） -->\n        <div style=\"display:flex; align-items:center; gap:14px; margin-bottom:6px;\">\n          <div style=\"flex-shrink:0; width:52px; height:52px; border-radius:50%; background: linear-gradient(145deg, #ffe0f0, #ffd0e8); display:flex; align-items:center; justify-content:center; font-size:26px; box-shadow:0 4px 12px rgba(255, 150, 200, 0.35);\">\n            ✉️\n          </div>\n          <div>\n            <h2 style=\"margin:0; color:#8a4b6e; font-size:23px; font-weight:700; letter-spacing:0.3px;\">\n              {{siteName}} 邮箱验证\n            </h2>\n            <p style=\"margin:4px 0 0; color:#b98aa8; font-size:13px; letter-spacing:1px;\">\n              ✦ 一封来自二次元的小邮件 ✦\n            </p>\n          </div>\n        </div>\n\n        <!-- 分隔线 -->\n        <div style=\"height:1px; background: linear-gradient(90deg, transparent, #f0d0e0, transparent); margin:20px 0 22px;\"></div>\n\n        <!-- 问候语 -->\n        <p style=\"color:#5e4b56; line-height:1.9; font-size:15.5px; margin:0 0 6px;\">\n          你好呀～ <span style=\"color:#c95a82;\">(｡•̀ᴗ-)✧</span>\n        </p>\n        <p style=\"color:#5e4b56; line-height:1.9; font-size:15.5px; margin:0 0 20px;\">\n          你正在注册 <strong style=\"color:#b3416b;\">{{siteName}}</strong> 账号，本次的验证码是：\n        </p>\n\n        <!-- 验证码区域：左侧装饰条 + 大号验证码 -->\n        <div style=\"display:flex; align-items:stretch; background: linear-gradient(120deg, #fff5fb, #f8f0ff); border-radius:24px; border:1.5px solid #f0d5f0; overflow:hidden; margin-bottom:20px; box-shadow: inset 0 2px 12px #fce8f8;\">\n          <!-- 左侧竖向装饰条 -->\n          <div style=\"width:8px; background: linear-gradient(180deg, #ffb6d9, #d9b8ff, #ffb6d9); flex-shrink:0;\"></div>\n          <!-- 验证码内容 -->\n          <div style=\"flex:1; padding:18px 20px; text-align:center;\">\n            <div style=\"font-size:13px; color:#b98aa8; letter-spacing:2px; margin-bottom:6px;\">— 你的专属验证码 —</div>\n            <div style=\"font-size:34px; font-weight:800; letter-spacing:10px; color:#c44b7a; text-shadow: 0 0 14px #ffcce0, 2px 2px 0 #ffe8f2;\">\n              {{code}}\n            </div>\n          </div>\n          <!-- 右侧竖向装饰条 -->\n          <div style=\"width:8px; background: linear-gradient(180deg, #ffb6d9, #d9b8ff, #ffb6d9); flex-shrink:0;\"></div>\n        </div>\n\n        <!-- 有效期提示：淡底 + 小图标 -->\n        <div style=\"display:flex; align-items:center; justify-content:center; gap:8px; background:#faf3ff; border-radius:50px; padding:10px 16px; margin-bottom:22px; border:1px dashed #e5c8e5;\">\n          <span style=\"font-size:16px;\">⏳</span>\n          <span style=\"color:#9b7a8e; font-size:14px;\">验证码 5 分钟内有效，请勿泄露给他人。</span>\n        </div>\n\n        <!-- 小贴士区域：与整体风格统一 -->\n        <div style=\"background:#fff8fd; border-radius:20px; padding:14px 18px; border-left:4px solid #d9b8ff; margin-bottom:8px;\">\n          <p style=\"margin:0; color:#8a6b7e; font-size:13.5px; line-height:1.8;\">\n            <span style=\"color:#b34e74; font-weight:600;\">🎀 小贴士</span>\n            <span style=\"color:#c9a5bc; margin:0 6px;\">|</span>\n            如果没收到邮件，记得看看垃圾箱哦～ 祝你注册顺利！\n          </p>\n        </div>\n\n        <!-- 底部装饰：三个小圆点 -->\n        <div style=\"text-align:center; margin-top:22px; letter-spacing:6px; color:#e5c8e5; font-size:12px;\">\n          ● ● ●\n        </div>\n\n        <!-- 页脚 -->\n        <p style=\"color:#cbb0c4; font-size:11px; text-align:center; margin:18px 0 0; border-top:1px solid #f5e5f5; padding-top:14px; letter-spacing:0.5px;\">\n          ☆ {{siteName}} 二次元邮件站 ☆\n        </p>\n\n      </div>\n    </div>\n\n    <!-- 卡片下方小装饰 -->\n    <div style=\"text-align:center; margin-top:14px; color:#d9b8d9; font-size:13px; opacity:0.6; letter-spacing:3px;\">\n      ✿ ～ ✿\n    </div>\n  </div>\n</body>\n</html>\n','{{siteName}},{{code}},{{email}},{{nickname}}',1,1,'2026-09-14 22:49:28','2026-09-14 23:04:53');
INSERT  IGNORE INTO `sys_email_template` (`id`, `scenario`, `name`, `subject`, `background_image`, `overlay_opacity`, `content_html`, `variables`, `enabled`, `active`, `create_time`, `update_time`) VALUES (2,'login_alert','异常登录提醒','【{{siteName}}】安全提醒：{{result}}（{{ip}}）',NULL,0.90,'<div style=\"font-family:system-ui,Segoe UI,sans-serif;color:#4a3b46;line-height:1.9;\">\n  <h2 style=\"margin:0 0 12px;color:#b3416b;\">{{siteName}} 安全提醒</h2>\n  <p>检测到一次需要提醒的登录行为：</p>\n  <table style=\"border-collapse:collapse;font-size:14px;\">\n    <tr><td style=\"padding:4px 12px 4px 0;color:#9a8a9c;\">时间</td><td>{{time}}</td></tr>\n    <tr><td style=\"padding:4px 12px 4px 0;color:#9a8a9c;\">账号</td><td>{{username}}</td></tr>\n    <tr><td style=\"padding:4px 12px 4px 0;color:#9a8a9c;\">结果</td><td>{{result}}</td></tr>\n    <tr><td style=\"padding:4px 12px 4px 0;color:#9a8a9c;\">原因</td><td>{{reason}}</td></tr>\n    <tr><td style=\"padding:4px 12px 4px 0;color:#9a8a9c;\">IP</td><td>{{ip}}（{{region}}）</td></tr>\n    <tr><td style=\"padding:4px 12px 4px 0;color:#9a8a9c;\">浏览器</td><td>{{browser}}</td></tr>\n  </table>\n  <p style=\"margin-top:14px;color:#c44b7a;\">若非本人操作，请立即修改登录密码与安全密码，并检查后台配置。</p>\n</div>','{{siteName}},{{time}},{{username}},{{result}},{{reason}},{{ip}},{{region}},{{browser}}',1,1,'2026-09-15 23:36:12','2026-09-15 23:36:12');
/*!40000 ALTER TABLE `sys_email_template` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- ============================================================================
-- 6. 执行结果自检（对照期望值看一眼就行）
-- ============================================================================
SELECT '新增表数量（期望 28）' AS item, COUNT(*) AS value
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('admin_api_key','admin_api_log','music_fallback','page_background',
    'sandbox_act','sandbox_area_lock','sandbox_attitude_log','sandbox_character','sandbox_coin_log',
    'sandbox_gift','sandbox_interaction','sandbox_item','sandbox_location','sandbox_memory',
    'sandbox_news','sandbox_quest','sandbox_relation','sandbox_shop_item','sandbox_shop_order',
    'sandbox_world','sys_email_template','sys_emoji','sys_invite_code','sys_level','sys_login_ip',
    'sys_point_log','sys_resource_unlock','sys_sign_log')
UNION ALL
SELECT 'sys_user 新增字段（期望 8）', COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
    AND COLUMN_NAME IN ('security_password','email','exp','points','level','can_invite','sign_days','last_sign_date')
UNION ALL
SELECT 'sys_user.uk_email 索引（期望 1）', COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_email'
UNION ALL
SELECT '等级档位（期望 10）', COUNT(*) FROM `sys_level`
UNION ALL
SELECT '兜底歌单（期望 4）', COUNT(*) FROM `music_fallback`
UNION ALL
SELECT '邮件模板（期望 2）', COUNT(*) FROM `sys_email_template`
UNION ALL
SELECT '配置项总数（期望 120 以上）', COUNT(*) FROM `sys_config`;

-- 收尾：临时存储过程用完就删（脚本可重复执行，删了也没关系）
DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DROP PROCEDURE IF EXISTS `bcblog_add_idx`;
