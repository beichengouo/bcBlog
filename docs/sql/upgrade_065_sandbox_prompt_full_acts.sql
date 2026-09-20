-- ============================================================
-- 065 沙盒：【最近行动】里最近几步给"完整动作 + 心声"
--
-- 背景（用户实测）：角色自己的【最近行动】过去只给一句话摘要，
-- 而同一个提示词里「世界里的其他居民」给的是**完整的最近一步**（逐条动作 + 概括）。
-- 于是出现"AI 对别人比对自己更了解"的怪现象：下一步不知道上一步具体做到哪一步，
-- 故事前后容易接不上（例如上一步已经动身出发，这一步又写一遍"我动身前往某地"）。
--
-- 现在：最近 N 步（默认 1）给完整动作（逐条列出、**不按字数截断**）+ 当时的心里话 + 一句话概括，
-- 更早的仍然只给一行摘要。整体体量由 sandbox_prompt_char_limit（提示词预算）兜底。
--
-- 脚本幂等，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
USE `bc_blog`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_prompt_full_act_steps', '1',
     '【最近行动】里最近几步给完整动作+心声（默认 1；0 = 全部只给一句话摘要；建议 1~2）');

SELECT `config_key`, `config_value` FROM `sys_config`
WHERE `config_key` IN ('sandbox_prompt_full_act_steps', 'sandbox_prompt_char_limit');
