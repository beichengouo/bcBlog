-- ============================================================
-- 060 四个生成器的三段式输出（角色行动保持五段式）
--
-- 背景：角色行动一直用五段式（回看 → 思考 → 草稿 → 自审 → 终稿），因为行动要权衡的东西多；
-- 但四个**生成器**（AI 创作角色 / 旅人纪闻 / 旅人集市 / 旅人委托）原来是一次调用直接吐 JSON，
-- 模型常常"不多想"就编，内容与地图、世界观脱节。
--
-- 现在给这四个生成器加上三段式：<think>（结合世界观与地图地点含描述、危险度权衡）
--   → <draft>（把要生成的东西先写一遍）→ <final>（只输出 JSON）。
-- 角色行动不受此配置影响，仍然是五段式。
--
-- 另外：这一版顺手修掉一个老 bug——角色行动的"预填充"以前写的是 <think>，
-- 而提示词里第一段是 <recap>（回看），模型被预填充带着走，导致「回看」那段从来没真正执行过。
-- 现在预填充改成 <recap>，五段式才名副其实。
--
-- 脚本幂等，可重复执行。
-- ============================================================

SET NAMES utf8mb4;
USE `bc_blog`;

INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `remark`) VALUES
    ('sandbox_generator_flow', 'three',
     '四个生成器（AI创作角色/旅人纪闻/旅人集市/旅人委托）是否走三段式 think→draft→final：three 开启（默认）/ off 关闭。角色行动不适用（它保持五段式）');

SELECT `config_key`, `config_value` FROM `sys_config`
WHERE `config_key` IN ('sandbox_generator_flow', 'sandbox_draft_mode', 'sandbox_think_stage');
