-- 增量升级脚本：给已存在的 blog_category 表增加 parent_id 字段（支持多级分类）
-- 适用于已经导入过旧版 bc_blog.sql 的数据库

USE `bc_blog`;

ALTER TABLE `blog_category`
  ADD COLUMN `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父分类ID，0为顶级' AFTER `name`,
  ADD KEY `idx_parent` (`parent_id`);
