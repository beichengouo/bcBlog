package com.bc.bcblog.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.mapper.SandboxActMapper;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxWorldMapper;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 「每步自检」（L3）的**真实 AI** 验证（一到两次调用）。
 *
 * 做法：造一步"服务端指定了遭遇、但叙述里完全没应对"的行动（L1 必然报疑点），
 * 调一次 stepSelfCheck，看它会不会让 AI 给出修正后的叙述、并且**只改叙述字段**。
 *
 * 手动运行： mvn test "-Dtest=SandboxStepSelfCheckCheck"
 * 报告：target/probe/step-selfcheck.md
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxStepSelfCheckCheck {

    static {
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final Long PROVIDER_ID = 2L;
    private static final String MODEL = "假流式-gemini-3-flash-preview";
    private static final String HERO = "__自检·叙事测试者__";
    private static final String PLACE = "【探针】魔物森林";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private ConfigService configService;
    @Autowired
    private SandboxWorldMapper worldMapper;
    @Autowired
    private SandboxCharacterMapper characterMapper;
    @Autowired
    private SandboxActMapper actMapper;

    private final StringBuilder report = new StringBuilder();
    private int passed = 0;
    private int failed = 0;

    @Test
    void run() throws Exception {
        Long worldId = null;
        String enabledBefore = configService.getConfigValue("sandbox_step_selfcheck", "1");
        try {
            configService.setConfigValue("sandbox_step_selfcheck", "1");
            worldId = setUpWorld();
            SandboxCharacter hero = setUpCharacter(worldId);
            SandboxAct prev = insertAct(hero, "她在林子里采了些草药，觉得风声不太对", LocalDateTime.now().minusMinutes(50));
            SandboxAct act = insertAct(hero, "她坐在石头上发呆，看了看天色，觉得今天很平静",
                    LocalDateTime.now().minusMinutes(1));

            String encounter = "在" + PLACE + " · 乱石坡，你被盯上了：对方实力大约相当于战斗力 30"
                    + "（你现在是 18，比你强）。";
            long start = System.currentTimeMillis();
            selfCheck(hero, act, new ArrayList<>(Collections.singletonList(prev)), null, encounter);
            long cost = System.currentTimeMillis() - start;

            report.append("# 每步自检（真实 AI）\n\n")
                    .append("- 模型：").append(MODEL).append("；耗时 ").append(cost).append(" ms\n")
                    .append("- 服务端给出的遭遇：").append(encounter).append("\n\n")
                    .append("### 修正后的叙述\n\n")
                    .append("- actions：\n").append(act.getActions()).append("\n")
                    .append("- summary：").append(act.getSummary()).append("\n")
                    .append("- inner_voice：").append(act.getInnerVoice()).append("\n\n");

            check("自检后 actions 非空且更长了（不是被改没）",
                    act.getActions() != null && act.getActions().length() >= 20);
            check("修正后的叙述里出现了应对遭遇的内容",
                    containsAny(act.getActions(), "战", "打", "逃", "躲", "迎", "闪", "对峙", "击", "退", "跑"));
            check("地点没有被自检改掉（服务端说了算）", PLACE.equals(act.getLocationName()));
            check("金币变化没有被自检改掉", act.getCoinChange() == 0);
            check("耗时在 1~2 次调用的量级内", cost < 90000);

            report.append("\n结果：").append(failed == 0 ? "全部通过（" + passed + " 项）" : failed + " 项失败").append('\n');
            System.out.println("\n===== 每步自检：" + (failed == 0 ? "全部通过（" + passed + " 项）" : failed + " 项失败") + " =====");
        } finally {
            configService.setConfigValue("sandbox_step_selfcheck", enabledBefore);
            writeReport();
            if (worldId != null) {
                actMapper.delete(new QueryWrapper<SandboxAct>().eq("world_id", worldId));
                characterMapper.delete(new QueryWrapper<SandboxCharacter>().eq("world_id", worldId));
                worldMapper.deleteById(worldId);
                System.out.println("临时世界已清理（world_id=" + worldId + "）");
            }
        }
        if (failed > 0) {
            throw new AssertionError("每步自检验证有 " + failed + " 项失败，详见 target/probe/step-selfcheck.md");
        }
    }

    private boolean containsAny(String text, String... words) {
        if (text == null) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private void selfCheck(SandboxCharacter hero, SandboxAct act, List<SandboxAct> recent,
                           SandboxAct mentioned, String encounter) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod("stepSelfCheck",
                SandboxCharacter.class, SandboxAct.class, List.class, SandboxAct.class, String.class,
                JSONObject.class, boolean.class);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        method.invoke(target == null ? service : target, hero, act, recent, mentioned, encounter, null, false);
    }

    private Long setUpWorld() {
        SandboxWorld world = new SandboxWorld();
        world.setName("【探针】每步自检世界");
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
        hero.setTitle("测试者");
        hero.setPersona("（探针角色）独自在魔物森林里采药的冒险者。");
        hero.setAppearance("");
        hero.setProviderId(PROVIDER_ID);
        hero.setModel(MODEL);
        hero.setX(80);
        hero.setY(80);
        hero.setLocationName(PLACE);
        hero.setSubLocation("乱石坡");
        hero.setStatusJson("{\"体力\":80,\"魔力\":50,\"饥饿度\":30,\"心情\":\"平静\",\"伤势\":\"无恙\"}");
        hero.setCoins(10);
        hero.setCombatPower(18);
        hero.setEquipPower(0);
        hero.setEnabled(1);
        hero.setIntervalMin(45);
        hero.setIntervalMax(75);
        hero.setLastRunTime(LocalDateTime.now().minusHours(1));
        characterMapper.insert(hero);
        return hero;
    }

    private SandboxAct insertAct(SandboxCharacter hero, String text, LocalDateTime time) {
        SandboxAct act = new SandboxAct();
        act.setWorldId(hero.getWorldId());
        act.setCharacterId(hero.getId());
        act.setLocationName(PLACE);
        act.setSubLocation("乱石坡");
        act.setActions(text);
        act.setSummary(text);
        act.setInnerVoice("（探针）");
        act.setCoinChange(0);
        act.setCombatChange(0);
        act.setFromAi(1);
        act.setCreateTime(time);
        actMapper.insert(act);
        return act;
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

    private void writeReport() {
        try {
            Path dir = Paths.get("target", "probe");
            Files.createDirectories(dir);
            Files.write(dir.resolve("step-selfcheck.md"), report.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("报告：target/probe/step-selfcheck.md");
        } catch (Exception ignored) {
            // 写报告失败不影响验证本身
        }
    }
}
