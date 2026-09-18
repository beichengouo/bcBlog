-- ============================================================
-- 051 沙盒「角色态度会变」+ 态度变化记录
--
-- 背景：角色对「实力 / 财富」的看法（sandbox_character.power_view / wealth_view）
--   过去只在 AI 生成角色时写一次，之后无论经历什么都不变，等于价值观是死的。
--
-- 现在改成：行动里真的发生了影响认知的事，态度才可能微调——
--   · 缺钱、欠债、被抢、饿肚子 → 对财富的看法可能变；
--   · 惨败、受伤、濒死、被强者压制 → 对实力的看法可能变；
--   · 暴富、被人舍命相救、长期安稳度日也算触发。
--   服务端会校验「确实发生了相关事件 + 过了冷却 + 新旧不同 + 长度合法」才写回角色，
--   并把每一次变化记到下面的表里，前台角色档案展示最近几条「想法变化」。
--
-- 默认冷却 12 小时；濒死、破产、暴富这类重大事件（AI 标记 major）可以突破冷却。
-- 表数据量很小（每个角色最多每 12 小时一条），属于角色成长痕迹，不做定期清理。
-- ============================================================

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_attitude_enabled', 'on', '沙盒角色「对实力/财富的看法」是否允许随经历变化：on 开启（默认）/ off 关闭，关闭后只在管理员编辑角色时可改'),
    ('sandbox_attitude_cooldown_hours', '12', '沙盒角色两次态度变化之间的最小间隔（小时），默认 12；濒死、破产、暴富这类重大事件可以突破冷却');

CREATE TABLE IF NOT EXISTS `sandbox_attitude_log` (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `world_id`      bigint       DEFAULT NULL COMMENT '所属世界',
    `character_id`  bigint       NOT NULL COMMENT '角色 ID',
    `character_name` varchar(90) DEFAULT NULL COMMENT '角色名快照，角色删除后仍可追溯',
    `act_id`        bigint       DEFAULT NULL COMMENT '触发这次变化的那条行动记录 ID',
    `kind`          varchar(16)  NOT NULL COMMENT 'power=对实力的看法 / wealth=对财富的看法',
    `old_view`      varchar(120) DEFAULT NULL COMMENT '变化前的说法',
    `new_view`      varchar(120) NOT NULL COMMENT '变化后的说法',
    `reason`        varchar(200) DEFAULT NULL COMMENT '为什么变（AI 给的一句话，前台展示用）',
    `major`         tinyint      NOT NULL DEFAULT 0 COMMENT '1=重大事件触发（可突破冷却）',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_attitude_char` (`character_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '沙盒角色态度（对实力/财富的看法）变化记录';
