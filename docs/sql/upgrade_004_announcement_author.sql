-- 公告新增发布人字段
ALTER TABLE `site_announcement`
  ADD COLUMN `author` varchar(50) NOT NULL DEFAULT '管理员' COMMENT '发布人' AFTER `content`;
