-- Live2D 看板娘模型库：管理员可维护多个模型，并切换前台当前展示的模型
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

-- 移除旧模型，并接入用户指定的角色 / 主题款看板娘
DELETE FROM `live2d_model`
WHERE `model_key` IN ('pio', 'tia', 'haru', 'shizuku', 'wanko', 'cat-black');

INSERT INTO `live2d_model`
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
  ('bilibili-33', 'B 站看板娘 33', 'B 站看板娘 33', 'https://model.hacxy.cn/bilibili-33/index.json', 11, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `url` = VALUES(`url`);
