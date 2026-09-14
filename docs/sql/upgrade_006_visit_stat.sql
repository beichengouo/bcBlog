-- 访问量统计表

CREATE TABLE IF NOT EXISTS `sys_visit_stat` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `stat_date` date NOT NULL COMMENT '统计日期',
    `pv` bigint NOT NULL DEFAULT 0 COMMENT '当日访问量',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日访问量统计';
