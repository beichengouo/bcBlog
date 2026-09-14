-- 定期清理配置

INSERT INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('cleanup_enabled', '1', '是否启用定期清理'),
    ('cleanup_login_log_days', '90', '登录日志保留天数'),
    ('cleanup_visit_stat_days', '730', '访问统计保留天数'),
    ('cleanup_sign_log_days', '365', '签到记录保留天数'),
    ('cleanup_point_log_days', '365', '积分流水保留天数')
ON DUPLICATE KEY UPDATE `config_value` = `config_value`;
