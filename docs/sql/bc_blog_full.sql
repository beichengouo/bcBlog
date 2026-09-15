-- bcBlog 数据库结构导出（仅结构，不含数据，共 37 张表）
-- 用法：在 Navicat 中先创建 bc_blog 数据库（utf8mb4），选中该库后再执行本文件。
-- 全新安装执行完本文件后，首次启动后端会自动创建默认管理员 admin / Admin@123456。
-- 已经有数据的旧库请执行 docs/sql/upgrade_20260916_batch.sql 增量升级，不要执行本文件。

-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: bc_blog
-- ------------------------------------------------------
-- Server version	8.0.46

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

--
-- Table structure for table `admin_api_key`
--

DROP TABLE IF EXISTS `admin_api_key`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_api_key` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint NOT NULL COMMENT '所属管理员',
  `key_name` varchar(50) NOT NULL COMMENT '密钥名称，如 deepseek_api_key',
  `key_value` varchar(1000) DEFAULT NULL COMMENT '密钥值（程序加密后存储）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_key` (`admin_id`,`key_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员个人密钥';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `admin_api_log`
--

DROP TABLE IF EXISTS `admin_api_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_api_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint DEFAULT NULL COMMENT '调用者ID，定时任务为空',
  `admin_name` varchar(100) DEFAULT NULL COMMENT '调用者用户名/昵称快照',
  `action` varchar(100) NOT NULL COMMENT '动作，如「沙盒·立即执行一次」',
  `source` varchar(20) NOT NULL DEFAULT 'manual' COMMENT 'manual 手动 / schedule 定时',
  `target` varchar(200) DEFAULT NULL COMMENT '使用的服务商或第三方接口（不含密钥）',
  `success` tinyint NOT NULL DEFAULT '1' COMMENT '是否成功',
  `message` varchar(300) DEFAULT NULL COMMENT '失败原因',
  `cost_ms` int DEFAULT NULL COMMENT '耗时毫秒',
  `ip` varchar(64) DEFAULT NULL COMMENT '调用方 IP',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin` (`admin_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API 调用审计';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ai_provider`
--

DROP TABLE IF EXISTS `ai_provider`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `base_url` varchar(300) NOT NULL,
  `api_key` varchar(500) DEFAULT NULL,
  `is_default` tinyint NOT NULL DEFAULT '0',
  `owner_id` bigint DEFAULT NULL COMMENT '归属管理员ID，NULL 表示系统服务商',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI服务商配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `background`
--

DROP TABLE IF EXISTS `background`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `background` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL DEFAULT '',
  `type` varchar(20) NOT NULL,
  `url` varchar(500) NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `portal_active` tinyint NOT NULL DEFAULT '0',
  `admin_active` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='页面背景壁纸';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blog_article`
--

DROP TABLE IF EXISTS `blog_article`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_article` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '标题',
  `summary` varchar(500) DEFAULT NULL COMMENT '摘要',
  `content` longtext COMMENT '正文',
  `cover` varchar(255) DEFAULT NULL COMMENT '封面',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0草稿 1发布',
  `is_top` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1置顶',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `author_id` bigint DEFAULT NULL,
  `author_name` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status_time` (`status`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blog_article_tag`
--

DROP TABLE IF EXISTS `blog_article_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_article_tag` (
  `article_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`article_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章标签关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blog_category`
--

DROP TABLE IF EXISTS `blog_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '分类名',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父分类ID，0为顶级',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blog_comment`
--

DROP TABLE IF EXISTS `blog_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `article_id` bigint NOT NULL COMMENT '文章ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父评论ID，0为顶层',
  `nickname` varchar(50) NOT NULL COMMENT '昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `content` varchar(1000) NOT NULL COMMENT '内容',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审核 1通过 2拒绝',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `user_id` bigint DEFAULT NULL COMMENT '登录用户ID',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像快照',
  `level` int DEFAULT NULL COMMENT '等级快照',
  `level_name` varchar(50) DEFAULT NULL COMMENT '等级名称快照',
  PRIMARY KEY (`id`),
  KEY `idx_article` (`article_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blog_photo`
--

DROP TABLE IF EXISTS `blog_photo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_photo` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) DEFAULT NULL COMMENT '鐓х墖鏍囬?',
  `description` varchar(500) DEFAULT NULL COMMENT '绠?崟浠嬬粛',
  `url` varchar(500) NOT NULL COMMENT '鍥剧墖鍦板潃',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='娴佸厜蹇嗗涵鐓х墖';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blog_resource`
--

DROP TABLE IF EXISTS `blog_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '璧勬簮鍚嶇О',
  `description` varchar(500) DEFAULT NULL COMMENT '璧勬簮璇存槑',
  `cover` varchar(500) DEFAULT NULL COMMENT '封面图',
  `points` int NOT NULL DEFAULT '1' COMMENT '前往资源所需积分',
  `content` text COMMENT '资源详情内容（HTML）',
  `url` varchar(500) NOT NULL COMMENT '璧勬簮閾炬帴',
  `password` varchar(100) DEFAULT NULL COMMENT '鎻愬彇瀵嗙爜',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鏅哄簱璧勬簮';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `blog_tag`
--

DROP TABLE IF EXISTS `blog_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL COMMENT '标签名',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `live2d_model`
--

DROP TABLE IF EXISTS `live2d_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `live2d_model` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_key` varchar(100) NOT NULL COMMENT '模型唯一标识',
  `name` varchar(100) NOT NULL COMMENT '中文名称，用于后台标注',
  `description` varchar(255) DEFAULT '' COMMENT '角色说明',
  `url` varchar(500) NOT NULL COMMENT '模型 model.json 地址',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值，越小越靠前',
  `active` tinyint NOT NULL DEFAULT '0' COMMENT '是否当前展示：1 是，0 否',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_key` (`model_key`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Live2D 看板娘模型库';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `music_fallback`
--

DROP TABLE IF EXISTS `music_fallback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `music_fallback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) NOT NULL COMMENT '姝屾洸鍚嶇О',
  `artist` varchar(200) DEFAULT NULL COMMENT '姝屾墜',
  `url` varchar(500) NOT NULL COMMENT '姝屾洸鐩撮摼',
  `pic` varchar(500) DEFAULT NULL COMMENT '灏侀潰鍥',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='榛樿?姝屾洸';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `music_playlist`
--

DROP TABLE IF EXISTS `music_playlist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `music_playlist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '',
  `playlist_id` varchar(100) NOT NULL,
  `active` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_playlist_id` (`playlist_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='音乐歌单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sandbox_act`
--

DROP TABLE IF EXISTS `sandbox_act`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_act` (
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
  `summary` varchar(300) DEFAULT NULL COMMENT '一句话概括',
  `next_after_minutes` int NOT NULL DEFAULT '0' COMMENT '这一步之后 AI 期望的间隔分钟数，0 表示未指定',
  `next_after_reason` varchar(50) DEFAULT NULL COMMENT '间隔原因，如「睡觉」「赶路」',
  `news_ref` varchar(300) DEFAULT NULL COMMENT '这一步参考/听说的旅人纪闻标题，顿号分隔',
  `raw_response` text COMMENT 'AI 原始返回，便于排查问题',
  `from_ai` tinyint NOT NULL DEFAULT '1' COMMENT '是否来自 AI：1 是，0 为兜底记录',
  `manual` tinyint NOT NULL DEFAULT '0' COMMENT '是否管理员手动执行：1 是（不占用每日额度）',
  `reaction` tinyint NOT NULL DEFAULT '0' COMMENT '是否由其他角色的互动触发的回应回合：1 是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_character_time` (`character_id`,`create_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=80 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒行动记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sandbox_character`
--

DROP TABLE IF EXISTS `sandbox_character`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_character` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `name` varchar(100) NOT NULL COMMENT '角色名',
  `title` varchar(100) DEFAULT NULL COMMENT '称号',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像 / 立绘地址',
  `appearance` varchar(500) DEFAULT NULL COMMENT '外貌描述（前台展示）',
  `persona` text COMMENT '人设提示词，写法参考酒馆角色卡',
  `provider_id` bigint DEFAULT NULL COMMENT '绑定 AI 服务商ID',
  `model` varchar(100) DEFAULT NULL COMMENT '使用的模型',
  `temperature` decimal(3,2) NOT NULL DEFAULT '0.90' COMMENT '采样温度 0~2',
  `x` int NOT NULL DEFAULT '50' COMMENT '当前横向坐标百分比',
  `y` int NOT NULL DEFAULT '50' COMMENT '当前纵向坐标百分比',
  `location_name` varchar(100) DEFAULT NULL COMMENT '当前位置名称',
  `sub_location` varchar(100) DEFAULT NULL COMMENT '当前所在的二级地点',
  `status_json` varchar(1000) DEFAULT NULL COMMENT '当前状态（JSON，内容由 AI 生成）',
  `coins` int NOT NULL DEFAULT '0' COMMENT '金币余额',
  `next_run_time` datetime DEFAULT NULL COMMENT '下次 AI 行动时间',
  `next_reason` varchar(50) DEFAULT NULL COMMENT '下次行动的原因，如「睡觉」，前台展示用',
  `last_run_time` datetime DEFAULT NULL COMMENT '上次 AI 行动时间',
  `interval_min` int NOT NULL DEFAULT '45' COMMENT '行动间隔最小值（分钟）',
  `interval_max` int NOT NULL DEFAULT '75' COMMENT '行动间隔最大值（分钟）',
  `ai_interval_min` int DEFAULT NULL COMMENT '该角色 AI 间隔下限（分钟），留空用全局设置',
  `ai_interval_max` int DEFAULT NULL COMMENT '该角色 AI 间隔上限（分钟），留空用全局设置',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最后一次调用失败原因',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1 启用，0 停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_world` (`world_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒角色';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sandbox_coin_log`
--

DROP TABLE IF EXISTS `sandbox_coin_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_coin_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `character_id` bigint NOT NULL COMMENT '角色ID',
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
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒金币流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sandbox_interaction`
--

DROP TABLE IF EXISTS `sandbox_interaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_interaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `character_id` bigint NOT NULL COMMENT '角色ID',
  `user_id` bigint NOT NULL COMMENT '留言用户ID',
  `user_name` varchar(100) DEFAULT NULL COMMENT '用户昵称快照',
  `user_avatar` varchar(500) DEFAULT NULL COMMENT '用户头像快照',
  `content` varchar(500) NOT NULL COMMENT '低语内容',
  `points_cost` int NOT NULL DEFAULT '0' COMMENT '消耗积分',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_character` (`character_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒旅人低语';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sandbox_item`
--

DROP TABLE IF EXISTS `sandbox_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `character_id` bigint NOT NULL COMMENT '角色ID',
  `name` varchar(100) NOT NULL COMMENT '物品名称',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '数量',
  `rarity` tinyint NOT NULL DEFAULT '1' COMMENT '品质：1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说',
  `icon` varchar(500) DEFAULT NULL COMMENT '物品图标图片地址，为空时前台按物品名自动匹配图标',
  `description` varchar(300) DEFAULT NULL COMMENT '物品说明（管理员可补充）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_char_item` (`character_id`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒角色背包';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sandbox_location`
--

DROP TABLE IF EXISTS `sandbox_location`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_location` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `world_id` bigint NOT NULL DEFAULT '1' COMMENT '所属世界',
  `name` varchar(100) NOT NULL COMMENT '地点名称',
  `icon` varchar(500) DEFAULT NULL COMMENT '地点图标：内置图标 key 或上传的图片地址',
  `x` int NOT NULL DEFAULT '50' COMMENT '横向坐标百分比 0~100',
  `y` int NOT NULL DEFAULT '50' COMMENT '纵向坐标百分比 0~100',
  `width` int NOT NULL DEFAULT '0' COMMENT '区域宽度百分比；0 表示单点',
  `height` int NOT NULL DEFAULT '0' COMMENT '区域高度百分比；0 表示单点',
  `description` varchar(500) DEFAULT NULL COMMENT '地点描述，会作为 AI 行动参考',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序，越小越靠前',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_world` (`world_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒地图地点';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sandbox_memory`
--

DROP TABLE IF EXISTS `sandbox_memory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_memory` (
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒角色每日记忆';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sandbox_news`
--

DROP TABLE IF EXISTS `sandbox_news`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_news` (
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒旅人纪闻';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sandbox_relation`
--

DROP TABLE IF EXISTS `sandbox_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_relation` (
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒角色好感度';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sandbox_world`
--

DROP TABLE IF EXISTS `sandbox_world`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sandbox_world` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL DEFAULT '' COMMENT '世界名称',
  `description` varchar(500) DEFAULT NULL COMMENT '世界简介（前台展示）',
  `map_image` varchar(500) DEFAULT NULL COMMENT '地图背景图地址',
  `world_prompt` text COMMENT '世界设定：写给 AI 的世界观、规则与文风',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1 启用，0 停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='沙盒世界';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `site_announcement`
--

DROP TABLE IF EXISTS `site_announcement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `site_announcement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(1000) NOT NULL DEFAULT '' COMMENT '公告内容',
  `author` varchar(50) NOT NULL DEFAULT '管理员' COMMENT '发布人',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1 启用，0 停用',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序值，越小越靠前',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点公告';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) DEFAULT NULL COMMENT '配置值',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=83 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_email_template`
--

DROP TABLE IF EXISTS `sys_email_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_email_template` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_emoji`
--

DROP TABLE IF EXISTS `sys_emoji`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_emoji` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_invite_code`
--

DROP TABLE IF EXISTS `sys_invite_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_invite_code` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_level`
--

DROP TABLE IF EXISTS `sys_level`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_level` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_login_ip`
--

DROP TABLE IF EXISTS `sys_login_ip`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_ip` (
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
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) DEFAULT NULL COMMENT '用户名',
  `ip` varchar(50) DEFAULT NULL COMMENT 'IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'UA',
  `success` tinyint NOT NULL DEFAULT '0' COMMENT '0失败 1成功',
  `message` varchar(200) DEFAULT NULL COMMENT '结果说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_point_log`
--

DROP TABLE IF EXISTS `sys_point_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_point_log` (
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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_resource_unlock`
--

DROP TABLE IF EXISTS `sys_resource_unlock`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_resource_unlock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `resource_id` bigint NOT NULL COMMENT '资源ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_resource` (`user_id`,`resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资源解锁记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_sign_log`
--

DROP TABLE IF EXISTS `sys_sign_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_sign_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `sign_date` date NOT NULL COMMENT '签到日期',
  `exp` int NOT NULL DEFAULT '0' COMMENT '获得经验',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`sign_date`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签到记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) NOT NULL COMMENT '登录名',
  `password` varchar(100) NOT NULL COMMENT '密码(BCrypt)',
  `security_password` varchar(100) DEFAULT NULL COMMENT '安全密码(BCrypt)，用于敏感操作二次验证',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `role` varchar(20) NOT NULL DEFAULT 'ADMIN1',
  `menus` varchar(500) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `exp` int NOT NULL DEFAULT '0' COMMENT '经验值',
  `points` int NOT NULL DEFAULT '0' COMMENT '积分',
  `level` int NOT NULL DEFAULT '1' COMMENT '等级',
  `can_invite` tinyint NOT NULL DEFAULT '0' COMMENT '是否有邀请码权限',
  `sign_days` int NOT NULL DEFAULT '0' COMMENT '累计签到天数',
  `last_sign_date` date DEFAULT NULL COMMENT '最后签到日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_visit_stat`
--

DROP TABLE IF EXISTS `sys_visit_stat`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_visit_stat` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_date` date NOT NULL COMMENT '缁熻?鏃ユ湡',
  `pv` bigint NOT NULL DEFAULT '0' COMMENT '褰撴棩璁块棶閲',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`)
) ENGINE=InnoDB AUTO_INCREMENT=178 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='姣忔棩璁块棶閲忕粺璁';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-16  0:59:14
