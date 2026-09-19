package com.bc.bcblog.entity;

import lombok.Data;

/**
 * 委托奖励里的一件物品。
 *
 * 存储时序列化成 JSON 数组放在 {@code sandbox_quest.reward_items} 里，
 * 接口返回时解析成列表给前端编辑（管理员在后台按"物品名 + 数量 + 说明"逐行填）。
 */
@Data
public class SandboxQuestReward {
    /** 物品名（会做规范化，保证"奖励名 = 背包名"） */
    private String name;
    /** 品质：1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说（与背包、集市同一套配色） */
    private Integer rarity;
    /** 数量，默认 1 */
    private Integer quantity;
    /** 一句说明，进背包时会写进物品描述 */
    private String description;
}
