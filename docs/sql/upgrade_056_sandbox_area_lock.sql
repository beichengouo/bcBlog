-- ============================================================
-- 056 沙盒「区域执行锁」：同一片地区同时只允许一个角色行动
--
-- 背景：以前是"一个角色一把锁"，防不住"两个角色同时行动、又刚好互相触发互动"；
--   后来讨论要不要整世界串行，结论是**按地区加锁**最合适——
--   并发的风险只来自"可能互动的角色"，而他们必然在同一片地区（互动上限默认 30km）。
--
-- 现在的规则：
--   · 角色开始行动前，先锁住自己所在的**一级地区**（例如「自由城邦联盟」）；
--   · 同一地区内的角色**依次**行动（谁先抢到谁跑，别人这一轮跳过或稍后再试）；
--   · **不同地区可以并行**——所以角色分散时不会互相拖慢（这也是不整世界串行的原因）；
--   · 角色级锁（sandbox_character.running_at）保留，作为"同一角色不被并发执行"的双保险。
--
-- 锁的存储放在数据库里（而不是内存），这样进程重启、多个实例同时跑都能正确互斥；
-- 锁超过 sandbox_area_lock_minutes 分钟未刷新视为失效（进程崩过），允许别人抢占。
-- ============================================================

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_area_lock_minutes', '15', '沙盒地区执行锁的超时时间（分钟）：同一地区同一时刻只允许一个角色在行动，超过这个时间未释放视为失效锁、可被抢占');

CREATE TABLE IF NOT EXISTS `sandbox_area_lock` (
    `id`         bigint       NOT NULL AUTO_INCREMENT,
    `world_id`   bigint       NOT NULL COMMENT '所属世界',
    `area_name`  varchar(100) NOT NULL COMMENT '一级地区名（地点名）',
    `holder`     varchar(100) DEFAULT NULL COMMENT '当前持有者（角色名，便于排查卡锁）',
    `locked_at`  datetime     NOT NULL COMMENT '最近一次抢锁/续期时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_area` (`world_id`, `area_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '沙盒地区执行锁：同一地区同一时刻只允许一个角色在行动';
