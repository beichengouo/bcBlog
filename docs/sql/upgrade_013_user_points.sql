-- 普通用户积分机制：积分字段 + 积分流水

ALTER TABLE `sys_user`
    ADD COLUMN `points` int NOT NULL DEFAULT 0 COMMENT '积分' AFTER `exp`;

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
