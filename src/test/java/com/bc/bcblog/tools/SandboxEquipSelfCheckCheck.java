package com.bc.bcblog.tools;

import cn.hutool.json.JSONObject;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxItem;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxItemMapper;
import com.bc.bcblog.mapper.SandboxWorldMapper;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 「装备拿不动 → 让 AI 二次自检改写这一步」这条链路的**真实 AI** 验证（一到两次调用）。
 *
 * 背景：提示词里已经写明"加成不能超过自身实力"，实测模型基本会自己避开；
 * 但这条硬规则必须由服务端兜住——所以这里**故意绕过提示词**直接喂一个违规的 equip_change，
 * 验证服务端会不会：① 拒绝这次装备；② 再调一次 AI 让它把这一步改口；
 * ③ 把改写后的 actions / summary 落回行动记录；④ 不再递归调用（最多一次）。
 *
 * 手动运行： mvn test "-Dtest=SandboxEquipSelfCheckCheck"
 * 报告：target/probe/equip-selfcheck.md
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxEquipSelfCheckCheck {

    static {
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final Long PROVIDER_ID = 2L;
    private static final String MODEL = "假流式-gemini-3-flash-preview";
    private static final String HERO = "__自检·凯尔__";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private SandboxWorldMapper worldMapper;
    @Autowired
    private SandboxCharacterMapper characterMapper;
    @Autowired
    private SandboxItemMapper itemMapper;

    private final StringBuilder report = new StringBuilder();
    private int passed = 0;
    private int failed = 0;

    @Test
    void run() throws Exception {
        Long worldId = null;
        try {
            worldId = setUpWorld();
            SandboxCharacter hero = setUpCharacter(worldId);
            addItem(hero, "龙鳞重剑", "weapon", 90, 5, "剑身布满龙鳞纹路的古剑，重得吓人");

            JSONObject obj = new JSONObject("{\"equip_change\":{\"equip\":[\"龙鳞重剑\"]},"
                    + "\"actions\":[\"凯尔伸手抓住那把布满龙鳞的古剑\",\"他用力提了提，剑身纹丝不动，"
                    + "手臂的旧伤却先裂开了\",\"他索性把剑靠在墙边，甩了甩发麻的手腕\"],"
                    + "\"summary\":\"试了试那把重剑，没拿动\"}");
            SandboxAct act = new SandboxAct();
            act.setActions("（原始输出）");
            act.setSummary("（原始输出）");

            long start = System.currentTimeMillis();
            String note = applyWithSelfCheck(hero, obj, act);
            long cost = System.currentTimeMillis() - start;

            report.append("# 装备自检修正（真实 AI）\n\n");
            report.append("- 模型：").append(MODEL).append("；耗时 ").append(cost).append(" ms\n");
            report.append("- 服务端返回的说明：").append(note).append("\n");
            report.append("- 改写后的 actions：\n").append(act.getActions()).append("\n");
            report.append("- 改写后的 summary：").append(act.getSummary()).append("\n\n");

            SandboxItem sword = item(hero, "龙鳞重剑");
            check("违规的装备没有被穿上", sword.getEquipped() == 0);
            check("装备加成合计仍是 0", equipPower(hero) == 0);
            check("说明里点出了拿不动（含自身实力）",
                    note != null && (note.contains("拿不动") || note.contains("自身实力")));
            check("AI 的第二次输出把 actions 改写了（不再是原始占位）",
                    act.getActions() != null && !"（原始输出）".equals(act.getActions()));
            check("改写后的 actions 里没有「成功装备 / 穿上了」这类说法",
                    act.getActions() != null
                            && !act.getActions().contains("成功装备") && !act.getActions().contains("穿上"));
            check("改写后的 summary 不再是原始占位",
                    act.getSummary() != null && !"（原始输出）".equals(act.getSummary()));
            check("这一次只多花了一次调用（耗时在 1 次请求的量级内）", cost < 60000);

            report.append("\n结果：").append(failed == 0 ? "全部通过（" + passed + " 项）" : failed + " 项失败").append('\n');
            System.out.println("\n===== 装备自检修正：" + (failed == 0 ? "全部通过（" + passed + " 项）" : failed + " 项失败") + " =====");
        } finally {
            writeReport();
            if (worldId != null) {
                itemMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SandboxItem>()
                        .eq("world_id", worldId));
                characterMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SandboxCharacter>()
                        .eq("world_id", worldId));
                worldMapper.deleteById(worldId);
                System.out.println("临时世界已清理（world_id=" + worldId + "）");
            }
        }
        if (failed > 0) {
            throw new AssertionError("装备自检修正验证有 " + failed + " 项失败，详见 target/probe/equip-selfcheck.md");
        }
    }

    /** 调私有的 applyEquipChanges，allowSelfCheck=true（会真的再调一次 AI） */
    private String applyWithSelfCheck(SandboxCharacter hero, JSONObject obj, SandboxAct act) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod("applyEquipChanges",
                SandboxCharacter.class, JSONObject.class, SandboxAct.class, boolean.class, boolean.class);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        Object result = method.invoke(target == null ? service : target, hero, obj, act, false, true);
        if (result == null) {
            return null;
        }
        Field note = result.getClass().getDeclaredField("note");
        note.setAccessible(true);
        Object value = note.get(result);
        return value == null ? null : String.valueOf(value);
    }

    private void check(String what, boolean ok) {
        if (ok) {
            passed++;
            report.append("- ✅ ").append(what).append('\n');
            System.out.println("  ✅ " + what);
        } else {
            failed++;
            report.append("- ❌ ").append(what).append('\n');
            System.out.println("  ❌ " + what);
        }
    }

    private Integer equipPower(SandboxCharacter hero) {
        SandboxCharacter fresh = characterMapper.selectById(hero.getId());
        return fresh.getEquipPower() == null ? 0 : fresh.getEquipPower();
    }

    private SandboxItem item(SandboxCharacter hero, String name) {
        return itemMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SandboxItem>()
                .eq("character_id", hero.getId()).eq("name", name).last("limit 1"));
    }

    private Long setUpWorld() {
        SandboxWorld world = new SandboxWorld();
        world.setName("【探针】装备自检世界");
        world.setDescription("（探针自动创建，跑完即删）");
        world.setWorldPrompt("剑与魔法的世界。");
        world.setEnabled(0);
        world.setPortalVisible(0);
        world.setMapImage("");
        worldMapper.insert(world);
        return world.getId();
    }

    private SandboxCharacter setUpCharacter(Long worldId) {
        SandboxCharacter hero = new SandboxCharacter();
        hero.setWorldId(worldId);
        hero.setName(HERO);
        hero.setTitle("见习剑士");
        hero.setPersona("（探针角色）刚从乡下来到城邦，力气不大。");
        hero.setAppearance("");
        hero.setProviderId(PROVIDER_ID);
        hero.setModel(MODEL);
        hero.setX(20);
        hero.setY(20);
        hero.setLocationName("【探针】某地");
        hero.setSubLocation("旧兵器铺门口");
        hero.setStatusJson("{\"体力\":80,\"魔力\":40,\"饥饿度\":30,\"心情\":\"平静\",\"伤势\":\"无恙\"}");
        hero.setCoins(10);
        hero.setCombatPower(15);
        hero.setEquipPower(0);
        hero.setEnabled(1);
        hero.setIntervalMin(45);
        hero.setIntervalMax(75);
        characterMapper.insert(hero);
        return hero;
    }

    private void addItem(SandboxCharacter hero, String name, String slot, int bonus, int rarity, String description) {
        SandboxItem item = new SandboxItem();
        item.setWorldId(hero.getWorldId());
        item.setCharacterId(hero.getId());
        item.setName(name);
        item.setQuantity(1);
        item.setRarity(rarity);
        item.setSlot(slot);
        item.setPowerBonus(bonus);
        item.setEquipped(0);
        item.setBroken(0);
        item.setDescription(description);
        itemMapper.insert(item);
    }

    private void writeReport() {
        try {
            Path dir = Paths.get("target", "probe");
            Files.createDirectories(dir);
            Files.write(dir.resolve("equip-selfcheck.md"), report.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("报告：target/probe/equip-selfcheck.md");
        } catch (Exception ignored) {
            // 写报告失败不影响验证本身
        }
    }
}
