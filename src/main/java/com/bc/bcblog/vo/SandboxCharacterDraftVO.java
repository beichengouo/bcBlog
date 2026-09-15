package com.bc.bcblog.vo;

import com.bc.bcblog.entity.SandboxItem;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI 生成的新角色草稿，用于一键填充「新增角色」表单。 */
@Data
public class SandboxCharacterDraftVO {
    private String name;
    private String title;
    private String appearance;
    /** 人设（酒馆风格，多行） */
    private String persona;
    /** 初始地点（一级） */
    private String locationName;
    /** 初始的小地方（二级） */
    private String subLocation;
    private Integer x;
    private Integer y;
    /** 初始状态：体力 / 魔力 / 饥饿度 / 心情 等 */
    private Map<String, Object> status = new LinkedHashMap<>();
    /** 初始金币 */
    private Integer coins;
    /** 初始背包物品 */
    private List<SandboxItem> items;
    /** AI 原始返回，便于排查 */
    private String raw;
}
