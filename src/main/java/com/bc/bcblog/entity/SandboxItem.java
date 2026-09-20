package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 沙盒角色背包物品。 */
@Data
@TableName("sandbox_item")
public class SandboxItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 角色 ID */
    private Long characterId;
    /** 物品名称 */
    private String name;
    /** 数量 */
    private Integer quantity;
    /** 品质：1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说 */
    private Integer rarity;
    /** 装备槽位：none 非装备 / weapon 武器 / offhand 副手 / armor 护具 / accessory 饰品 */
    private String slot;
    /** 装备加成（战斗力）：0 表示不是装备或这件装备没有加成 */
    private Integer powerBonus;
    /** 1 = 已经装备在角色装备栏里 */
    private Integer equipped;
    /** 1 = 已破损（不能再装备，加成按 0 算） */
    private Integer broken;
    /** 物品图标图片地址，为空时前台按物品名自动匹配图标 */
    private String icon;
    /** 物品说明 */
    private String description;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
