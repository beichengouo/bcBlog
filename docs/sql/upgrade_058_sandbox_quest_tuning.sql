-- ============================================================
-- 058 沙盒调优：收入上限 / 委托自检 / 纪闻引用收紧 / 提示词上限
--
-- 背景（都是两天双角色联调实测出来的）：
--   1. 支出一直有单次上限（sandbox_max_spend_per_act，默认 10 金币），**收入却完全没有**。
--      实测出现"护送委托还没完成，AI 在叙述里写商队付了报酬，顺手把整笔 80 金币写进 coins_change"，
--      造成"钱到账了、委托还在进行中"。现在给**非委托收入**加单次上限（委托报酬由服务端在真正完成时发放，
--      不经过这个额度）。
--   2. 完成校验没过时（人还没到目标地区 / 采集类还没拿到东西），AI 的叙述往往已经写着"完成、拿到报酬"，
--      服务端却把进度压回 99%，剧情和状态就对不上。现在会再调一次 AI 让它自检改口
--      （硬约束：不许改地点、不许写已拿到报酬、进度不许比之前更高）。
--   3. 纪闻引用过密：两天 44 步里有 27 步挂着同样两条要闻、连文字都一样。现在提示词改成
--      "没有新进展就别重复填"，服务端也会丢掉最近三步已经记过的引用。
--   4. 提示词上限从 9000 提到 20000：实测完整提示词约 16187 字符，9000 会一直触发精简
--      （砍掉今日要闻与其他居民动静）；提到 20000 后能完整保留，代价是每次请求更长、更慢。
--
-- 脚本幂等，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
USE `bc_blog`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_max_earn_per_act', '30',
     '沙盒单次行动的【非委托】收入上限（金币）：防止 AI 一句话让角色进账一大笔；委托报酬走服务端结算不受此限'),
    ('sandbox_quest_selfcheck', '1',
     '委托完成校验没过时，是否再调一次 AI 自检修正（1 开 / 0 关）：会重写该步叙述与进度，不许改地点、不许写已拿报酬');

-- 提示词上限提到 20000（只在还是旧默认值 9000 时改，避免覆盖管理员自己调过的值）
UPDATE `sys_config`
SET `config_value` = '20000'
WHERE `config_key` = 'sandbox_prompt_char_limit' AND `config_value` = '9000';

SELECT `config_key`, `config_value` FROM `sys_config`
WHERE `config_key` IN ('sandbox_max_earn_per_act', 'sandbox_quest_selfcheck',
                       'sandbox_prompt_char_limit', 'sandbox_max_spend_per_act')
ORDER BY `config_key`;
