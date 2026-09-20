package com.bc.bcblog.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 装备栏的纯规则：槽位、加成区间夹取、名字兜底猜槽位、能不能穿。
 *
 * 抽出来单独放，是因为这些规则错了不会报错、只会"悄悄变得不好玩"：
 * 加成夹取写歪了 AI 一件破木盾就能加 200 战力；能不能穿的判断写歪了，
 * 角色会拿着远超自己的神兵到处走。这些都要能脱离 Spring 直接写单测。
 */
public final class SandboxEquip {

    private SandboxEquip() {
    }

    /** 槽位（顺序就是前台装备栏的排列顺序） */
    public static final List<String> SLOTS =
            Collections.unmodifiableList(Arrays.asList("weapon", "offhand", "armor", "accessory"));
    public static final String SLOT_NONE = "none";

    /** 加成区间默认值：普通 +1~10 / 精良 +10~20 / 稀有 +20~30 / 史诗 +30~60 / 传说 +60~120 */
    public static final String DEFAULT_BONUS_TABLE = "1-10,10-20,20-30,30-60,60-120";

    /** 破损的前缀：事件式破损时改名保留在背包，修复时去掉这个前缀 */
    private static final String BROKEN_PREFIX = "破损的";

    /** 名字里出现这些词就当成对应槽位的装备（AI 没给槽位时的兜底） */
    private static final Map<String, List<String>> SLOT_WORDS = new HashMap<>();

    static {
        SLOT_WORDS.put("weapon", Arrays.asList(
                "剑", "刀", "匕首", "短刃", "长枪", "矛", "弓", "弩", "法杖", "魔杖", "权杖", "锤", "斧",
                "鞭", "拳套", "爪", "镰", "枪械", "短棍", "长剑", "阔剑", "弯刀", "刺剑"));
        SLOT_WORDS.put("offhand", Arrays.asList(
                "盾", "副手", "书籍", "法典", "魔典", "卷轴袋", "小刀", "短匕", "灯笼", "提灯", "法器"));
        SLOT_WORDS.put("armor", Arrays.asList(
                "甲", "铠", "铠衣", "护胸", "胸甲", "皮甲", "锁子甲", "板甲", "袍", "法袍", "斗篷", "披风",
                "外衣", "上衣", "短衫", "护腕", "臂铠", "头盔", "兜帽", "靴", "长靴", "战靴", "护腿", "裙甲"));
        SLOT_WORDS.put("accessory", Arrays.asList(
                "戒指", "指环", "项链", "吊坠", "护符", "耳环", "耳坠", "手镯", "臂环", "徽章", "勋章",
                "发饰", "胸针", "腰带", "披肩", "面纱", "眼罩", "药囊", "香囊"));
    }

    /** 不是装备类的东西（先判这些，免得「护心镜药水」这种名字被误判成装备） */
    private static final List<String> NON_EQUIP_WORDS = Arrays.asList(
            "药水", "药剂", "药膏", "草药", "药草", "干粮", "面包", "饼干", "烤肉", "热汤", "麦酒",
            "果酒", "浆果", "蘑菇", "种子", "羽毛", "矿石", "木材", "绳子", "火把", "蜡烛", "卷轴",
            "纸张", "信件", "地图", "钥匙", "钱包", "饲料", "鱼饵", "骨哨", "磨刀石", "除锈膏",
            "干酪", "腌肉", "蜜饯", "绷带", "香薰",
            // 资料与材料：这些名字里常常带「剑」「甲」这类字（「风羽剑技残页」「冰龙残鳞」），
            // 先判掉它们，免得被当成装备
            "残页", "残卷", "书页", "图纸", "图样", "笔记", "手稿", "拓本", "拓片",
            "配方", "药方", "晶石", "符文石", "鳞片", "碎片", "残片", "粉尘", "粉末",
            "木料", "石料", "皮毛", "兽皮", "碎石", "齿轮", "残骸");

    /** 槽位是否合法（none 也算合法，意思是"这不是装备"） */
    public static boolean isValidSlot(String slot) {
        if (slot == null) {
            return false;
        }
        String value = slot.trim().toLowerCase();
        return SLOT_NONE.equals(value) || SLOTS.contains(value);
    }

    /** 规范化槽位：认不出的一律当 none（AI 偶尔会写中文或奇奇怪怪的值） */
    public static String normalizeSlot(String slot) {
        if (slot == null) {
            return SLOT_NONE;
        }
        String value = slot.trim().toLowerCase();
        if (SLOTS.contains(value)) {
            return value;
        }
        if (SLOT_NONE.equals(value) || value.isEmpty()) {
            return SLOT_NONE;
        }
        // 中文兜底：AI 有时直接写「武器」
        for (int i = 0; i < SLOTS.size(); i++) {
            if (slotLabel(SLOTS.get(i)).equals(slot.trim())) {
                return SLOTS.get(i);
            }
        }
        return SLOT_NONE;
    }

    /** 槽位的中文名（提示词与前台展示共用） */
    public static String slotLabel(String slot) {
        switch (normalizeSlot(slot)) {
            case "weapon":
                return "武器";
            case "offhand":
                return "副手";
            case "armor":
                return "护具";
            case "accessory":
                return "饰品";
            default:
                return "非装备";
        }
    }

    /**
     * 按物品名猜槽位（AI 没标槽位时的兜底）。
     * 先看是不是明显不能装备的东西（吃的、材料），再看武器/防具关键词。
     */
    public static String guessSlot(String name) {
        if (name == null || name.trim().isEmpty()) {
            return SLOT_NONE;
        }
        String text = name.trim();
        for (String word : NON_EQUIP_WORDS) {
            if (text.contains(word)) {
                return SLOT_NONE;
            }
        }
        for (String slot : SLOTS) {
            for (String word : SLOT_WORDS.get(slot)) {
                if (text.contains(word)) {
                    return slot;
                }
            }
        }
        return SLOT_NONE;
    }

    /** 是不是能装备的东西 */
    public static boolean wearable(String slot) {
        return SLOTS.contains(normalizeSlot(slot));
    }

    /**
     * 解析加成区间配置："1-10,10-20,20-30,30-60,60-120"（品质 1~5）。
     * 写坏的那一档单独退回默认值，整串都坏就整串用默认。
     */
    public static int[][] parseBonusTable(String config) {
        int[][] table = defaultTable();
        if (config == null || config.trim().isEmpty()) {
            return table;
        }
        String[] parts = config.split(",");
        for (int i = 0; i < parts.length && i < table.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            String[] range = part.split("[-~～]");
            try {
                int low = Integer.parseInt(range[0].trim());
                int high = range.length > 1 ? Integer.parseInt(range[1].trim()) : low;
                if (low > high) {
                    int tmp = low;
                    low = high;
                    high = tmp;
                }
                if (low < 0) {
                    low = 0;
                }
                table[i][0] = low;
                table[i][1] = high;
            } catch (Exception ignored) {
                // 这一档写坏了：保留默认值
            }
        }
        return table;
    }

    private static int[][] defaultTable() {
        String[] parts = DEFAULT_BONUS_TABLE.split(",");
        int[][] table = new int[parts.length][2];
        for (int i = 0; i < parts.length; i++) {
            String[] range = parts[i].split("-");
            table[i][0] = Integer.parseInt(range[0]);
            table[i][1] = Integer.parseInt(range[1]);
        }
        return table;
    }

    /** 该品质的加成区间 [下限, 上限] */
    public static int[] bonusRange(String config, int rarity) {
        int[][] table = parseBonusTable(config);
        int index = Math.max(1, Math.min(table.length, rarity)) - 1;
        return table[index];
    }

    /**
     * 把 AI 给的加成夹进该品质的区间里。
     * AI 没给（<= 0）时取区间中点——历史的背包物品没有加成数据，取中点比给 0 更合理。
     */
    public static int clampBonus(String config, int rarity, Integer bonus) {
        int[] range = bonusRange(config, rarity);
        if (bonus == null || bonus <= 0) {
            return Math.max(1, (range[0] + range[1]) / 2);
        }
        return Math.max(range[0], Math.min(range[1], bonus));
    }

    /**
     * 能不能穿：装备加成不能超过角色**自身实力**（不叠加已有装备）。
     * 拿不动远超自己的东西，顺便也把"一件装备直接让战力翻几倍"堵死。
     */
    public static boolean canWear(int bonus, int ownPower) {
        return bonus <= Math.max(1, ownPower);
    }

    /** 事件式破损：改名保留在背包（"精钢长剑" → "破损的精钢长剑"） */
    public static String brokenName(String name) {
        if (name == null || name.trim().isEmpty() || name.startsWith(BROKEN_PREFIX)) {
            return name;
        }
        return BROKEN_PREFIX + name;
    }

    /** 修复：去掉破损前缀 */
    public static String repairedName(String name) {
        if (name == null) {
            return null;
        }
        return name.startsWith(BROKEN_PREFIX) ? name.substring(BROKEN_PREFIX.length()) : name;
    }

    /** 是不是破损坏了的名字 */
    public static boolean looksBroken(String name) {
        return name != null && name.startsWith(BROKEN_PREFIX);
    }
}
