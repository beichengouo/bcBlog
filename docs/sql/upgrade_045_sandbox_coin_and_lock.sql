-- ============================================================
-- 045 沙盒金币正确性 + 角色执行锁 + 角色对实力/财富的看法
--
-- 背景（本次修的 bug）：
--   1. 金币是"整值回写"：runOnce 开头读到角色快照，等 AI 跑完（几十秒）再整值写回，
--      期间用户贡献金币、集市购买、另一个行动改过的余额会被静默覆盖；
--      实测库里 晴/羽/灵 三个角色的余额都正好比"流水合计"少一笔收入。
--      → 代码已改成数据库原子增减（coins = coins ± delta），本脚本提供"按流水重算余额"的入口字段。
--   2. 同一个角色可能被同时执行（手动点两次 / 手动碰上定时任务），既会互相覆盖金币，也白烧 token。
--      → 新增 running_at 抢锁字段，跑的时候占位、跑完清空；超过 sandbox_run_lock_minutes 分钟视为失效锁。
--   3. 新建/生成角色时如果带初始金币，过去不写流水，导致"流水合计 = 余额"不成立。
--      → 代码改为写一条 init 流水（本脚本无需处理历史数据）。
--   4. 角色卡新增「对实力的看法 / 对财富的看法」两个短文本，AI 生成角色时自动填充，
--      行动提示词里会带上它们，角色就会按自己的性格去追求或舍弃战斗力与金钱。
-- ============================================================

-- 1. 角色表：执行锁 + 两个态度字段
DROP PROCEDURE IF EXISTS `bcblog_add_col`;
DELIMITER //
CREATE PROCEDURE `bcblog_add_col`(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_col, '` ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;
CALL bcblog_add_col('sandbox_character', 'running_at',
    'datetime DEFAULT NULL COMMENT ''正在执行行动的抢锁时间，执行结束会清空（并发保护）''');
CALL bcblog_add_col('sandbox_character', 'power_view',
    'varchar(60) DEFAULT NULL COMMENT ''对自身实力的看法（AI 生成/管理员可改，会写进行动提示词）''');
CALL bcblog_add_col('sandbox_character', 'wealth_view',
    'varchar(60) DEFAULT NULL COMMENT ''对金钱财富的看法（AI 生成/管理员可改，会写进行动提示词）''');
DROP PROCEDURE IF EXISTS `bcblog_add_col`;

-- 2. 执行锁的超时时间：超过这么久还占着锁，就当成"上次执行崩了"，允许重新执行
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_run_lock_minutes', '5', '沙盒角色行动的执行锁超时（分钟）：超过视为失效锁，可被重新抢占');

-- 3. 历史余额修复：由后台「沙盒角色 → 金币对账」按钮完成（会同时重算流水里的"当时余额"）。
--    这里不直接 UPDATE，是为了让管理员先在界面上看到"修了谁、从多少改到多少"再决定。
