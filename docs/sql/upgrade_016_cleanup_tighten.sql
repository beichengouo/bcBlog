-- 压缩数据清理默认保留天数，并增加可配置的执行时间

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('cleanup_time', '03:30', '定期清理执行时间 HH:mm'),
    ('cleanup_login_log_days', '7', '登录日志保留天数'),
    ('cleanup_visit_stat_days', '30', '访问统计保留天数'),
    ('cleanup_sign_log_days', '30', '签到记录保留天数'),
    ('cleanup_point_log_days', '30', '积分流水保留天数')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`), `remark` = VALUES(`remark`);
