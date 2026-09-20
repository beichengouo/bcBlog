package com.bc.bcblog.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxLocation;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.mapper.SandboxActMapper;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxLocationMapper;
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
import java.util.List;

/**
 * 「免遭遇 / 遭遇信息 / 叙事衔接自检」的探针（**不调用 AI**）。
 *
 * 覆盖四件事：
 *   1. 危险度 0（安全）地点默认不刷遭遇；把概率配成 100 才会刷（管理员可控）；
 *   2. 角色的「免遭遇地点」名单生效：名单内的地区不刷、名单外照常；
 *   3. 名单会被规范化（只保留真实地点、去重）；
 *   4. L1 叙事检查能挑出"同伴位置对不上 / 遭遇被无视 / 金币变化没有叙述支撑 / 原地踏步"，
 *      以及"刚刚有人把你写进 TA 的行动"能被找出来（叙事衔接用的那段）。
 *
 * 手动运行： mvn test "-Dtest=SandboxEncounterProbe"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxEncounterProbe {

    static {
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final String WORLD_NAME = "【探针】遭遇规则测试世界";
    private static final String SAFE_PLACE = "【探针】安全城";
    private static final String DANGER_PLACE = "【探针】魔物森林";
    private static final String HERO = "【探针】遭遇测试者";
    private static final String OTHER = "【探针】同行者";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private ConfigService configService;
    @Autowired
    private SandboxWorldMapper worldMapper;
    @Autowired
    private SandboxLocationMapper locationMapper;
    @Autowired
    private SandboxCharacterMapper characterMapper;
    @Autowired
    private SandboxActMapper actMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private int passed = 0;
    private final java.util.List<String> report = new java.util.ArrayList<>();

    @Test
    void run() throws Exception {
        Long worldId = null;
        String chance0Before = configService.getConfigValue("sandbox_encounter_chance_0", "0");
        String chance3Before = configService.getConfigValue("sandbox_encounter_chance_3", "55");
        try {
            worldId = setUpWorld();
            SandboxCharacter hero = setUpCharacter(worldId, HERO, DANGER_PLACE, 40);
            SandboxCharacter other = setUpCharacter(worldId, OTHER, SAFE_PLACE, 40);

            testDangerZeroSafe(hero);
            testExemptLocations(worldId, hero);
            testNormalizeExempt(worldId);
            testNarrativeIssues(hero, other);
            testMentionedMe(hero, other);
            testSelfCheckModes(hero);

            writeReport("全部通过（" + passed + " 项）");
            System.out.println("\n===== 遭遇规则探针：全部通过（" + passed + " 项）=====");
        } catch (Throwable e) {
            writeReport("失败：" + e.getMessage());
            throw e;
        } finally {
            configService.setConfigValue("sandbox_encounter_chance_0", chance0Before);
            configService.setConfigValue("sandbox_encounter_chance_3", chance3Before);
            if (worldId != null) {
                actMapper.delete(new QueryWrapper<SandboxAct>().eq("world_id", worldId));
                characterMapper.delete(new QueryWrapper<SandboxCharacter>().eq("world_id", worldId));
                locationMapper.delete(new QueryWrapper<SandboxLocation>().eq("world_id", worldId));
                worldMapper.deleteById(worldId);
                System.out.println("临时世界已清理（world_id=" + worldId + "）");
            }
        }
    }

    /** 危险度 0 的地方默认完全安全；管理员把概率调上去才会刷 */
    private void testDangerZeroSafe(SandboxCharacter hero) throws Exception {
        // 注意：buildEncounter 读的是传进去的角色对象，改完库要重新取一份
        SandboxCharacter atSafe = moveTo(hero, SAFE_PLACE, 20, 20);
        configService.setConfigValue("sandbox_encounter_chance_0", "0");
        check("危险度 0（安全）地点：默认不刷遭遇", encounterOf(atSafe) == null);
        configService.setConfigValue("sandbox_encounter_chance_0", "100");
        check("把安全地点概率配成 100 后，遭遇会触发（管理员可控）", encounterOf(atSafe) != null);
        configService.setConfigValue("sandbox_encounter_chance_0", "0");
    }

    /** 角色的免遭遇名单：名单内不刷，名单外照常 */
    private void testExemptLocations(Long worldId, SandboxCharacter hero) throws Exception {
        SandboxCharacter atDanger = moveTo(hero, DANGER_PLACE, 80, 80);
        configService.setConfigValue("sandbox_encounter_chance_3", "100");
        check("危险地点（未设免遭遇）：会刷遭遇", encounterOf(atDanger) != null);

        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, hero.getId())
                .set(SandboxCharacter::getEncounterExemptLocations, DANGER_PLACE));
        check("把它加入免遭遇名单后：同地点不再刷遭遇", encounterOf(fresh(hero)) == null);

        // 名单里写别的地区，仍然照常刷
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, hero.getId())
                .set(SandboxCharacter::getEncounterExemptLocations, SAFE_PLACE));
        check("免遭遇名单里是别的地区时，照常刷遭遇", encounterOf(fresh(hero)) != null);
    }

    /** 名单规范化：只保留这个世界真实存在的地点、去重 */
    private void testNormalizeExempt(Long worldId) throws Exception {
        String normalized = (String) call("normalizeExemptLocations",
                new Class[]{String.class, Long.class},
                SAFE_PLACE + "、" + SAFE_PLACE + ",不存在的地方," + DANGER_PLACE, worldId);
        check("名单只保留真实地点并去重", normalized != null
                && normalized.contains(SAFE_PLACE) && normalized.contains(DANGER_PLACE)
                && !normalized.contains("不存在的地方")
                && normalized.split(",").length == 2);
        check("空名单 → null", call("normalizeExemptLocations",
                new Class[]{String.class, Long.class}, "", worldId) == null);
    }

    /** L1：叙事检查能挑出该挑的问题，也不误报正常的一步 */
    @SuppressWarnings("unchecked")
    private void testNarrativeIssues(SandboxCharacter hero, SandboxCharacter other) throws Exception {
        // 上一步：在安全城吃饭
        SandboxAct prev = newAct(hero, SAFE_PLACE, "在安全城吃了碗热汤面", -2, LocalDateTime.now().minusMinutes(60));

        // ① 遭遇被无视：服务端给了遭遇，但叙述里什么都没发生
        SandboxAct act = newAct(hero, DANGER_PLACE, "她坐在石头上发呆，看了看天色", 0, LocalDateTime.now());
        List<String> issues = issuesOf(hero, act, java.util.Collections.singletonList(prev), null, "在魔物森林，你被盯上了：对方实力大约相当于战斗力 30。");
        check("L1：遭遇被无视会被挑出来", issues.stream().anyMatch(s -> s.contains("遭遇")));

        // ② 同伴位置对不上：把同行者写进来，但对方最新一步在别处
        newAct(other, SAFE_PLACE, "在安全城的摊位前挑东西", 0, LocalDateTime.now().minusMinutes(5));
        SandboxAct together = newAct(hero, DANGER_PLACE, "她和【探针】同行者并肩走在林子里", 0, LocalDateTime.now());
        together.setCompanions(OTHER);
        List<String> issues2 = issuesOf(hero, together, java.util.Collections.singletonList(prev), null, null);
        check("L1：同伴最新一步在别处会被挑出来",
                issues2.stream().anyMatch(s -> s.contains("同行") || s.contains("最新一步")));

        // ③ 金币变化但没有收支叙述
        SandboxAct rich = newAct(hero, DANGER_PLACE, "她靠着树干打了个盹", 12, LocalDateTime.now());
        List<String> issues3 = issuesOf(hero, rich, java.util.Collections.singletonList(prev), null, null);
        check("L1：金币涨了但没提钱会被挑出来",
                issues3.stream().anyMatch(s -> s.contains("金币变化")));

        // ④ 原地踏步：概括与上一步完全相同
        SandboxAct same = newAct(hero, SAFE_PLACE, "和上一步一样", 0, LocalDateTime.now());
        same.setSummary(prev.getSummary());
        List<String> issues4 = issuesOf(hero, same, java.util.Collections.singletonList(prev), null, null);
        check("L1：概括与上一步完全相同会被挑出来",
                issues4.stream().anyMatch(s -> s.contains("什么都没推进")));

        // ⑤ 正常的一步不该被误报
        SandboxAct fine = newAct(hero, SAFE_PLACE, "她在安全城的面摊花 2 金币吃了碗热汤面，心情放松下来", -2,
                LocalDateTime.now());
        fine.setSummary("在安全城吃了碗面");
        check("L1：正常的一步不会误报", issuesOf(hero, fine, java.util.Collections.singletonList(prev), null, null).isEmpty());

        // ⑥ 与上一步的叙述重复（连续 12 字以上逐字重合）
        SandboxAct repeatPrev = newAct(hero, SAFE_PLACE,
                "她扶着石墙慢慢走下瞭望塔的螺旋台阶，在木箱旁坐下吃了半块面包后睡去", 0, LocalDateTime.now().minusMinutes(60));
        // 状态要在对象上给出（规则 ⑨ 要拿上一步的体力/饥饿度做对比）
        repeatPrev.setStatusJson("{\"体力\":60,\"魔力\":80,\"饥饿度\":40,\"心情\":\"平静\",\"伤势\":\"无恙\"}");
        SandboxAct repeat = newAct(hero, SAFE_PLACE,
                "她扶着石墙慢慢走下瞭望塔的螺旋台阶，然后在台阶口停下发了会儿呆", 0, LocalDateTime.now());
        List<String> issues6 = issuesOf(hero, repeat, java.util.Collections.singletonList(repeatPrev), null, null);
        check("L1：与上一步的叙述重复会被挑出来",
                issues6.stream().anyMatch(s -> s.contains("重复")));

        // ⑦ 地点变了但没写赶路
        SandboxAct jump = newAct(hero, DANGER_PLACE, "她坐在树下整理药草，顺便把斗篷掸了掸", 0, LocalDateTime.now());
        List<String> issues7 = issuesOf(hero, jump, java.util.Collections.singletonList(repeatPrev), null, null);
        check("L1：地点变了却没写赶路会被挑出来",
                issues7.stream().anyMatch(s -> s.contains("赶路") || s.contains("抵达")));

        // ⑧ 物品变化没在叙述里出现
        SandboxAct itemStep = newAct(hero, SAFE_PLACE, "她在城里闲逛了一会儿，然后回旅店睡下", 0, LocalDateTime.now());
        JSONObject withItems = new JSONObject("{\"items_change\":{\"银叶草\":{\"delta\":1}}}");
        List<String> issues8 = issuesOf(hero, itemStep, java.util.Collections.singletonList(repeatPrev),
                null, null, withItems);
        check("L1：物品变化没在叙述里出现会被挑出来",
                issues8.stream().anyMatch(s -> s.contains("银叶草")));

        // ⑨ 状态突变 + 饥饿度与叙述矛盾
        SandboxAct jumpStatus = newAct(hero, SAFE_PLACE, "她在旅店睡了一觉，醒来吃了顿丰盛的早餐", -1, LocalDateTime.now());
        jumpStatus.setStatusJson("{\"体力\":95,\"魔力\":90,\"饥饿度\":85,\"心情\":\"满足\",\"伤势\":\"无恙\"}");
        List<String> issues9 = issuesOf(hero, jumpStatus, java.util.Collections.singletonList(repeatPrev), null, null);
        System.out.println("  [debug] 状态用例挑出的问题：" + issues9);
        check("L1：状态一步跳太多会被挑出来",
                issues9.stream().anyMatch(s -> s.contains("变化太大")));
        check("L1：饥饿度与叙述矛盾会被挑出来",
                issues9.stream().anyMatch(s -> s.contains("饥饿度")));
    }

    /** 「刚刚有人把你写进 TA 的行动」能被找出来 */
    private void testMentionedMe(SandboxCharacter hero, SandboxCharacter other) throws Exception {
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, hero.getId())
                .set(SandboxCharacter::getLastRunTime, LocalDateTime.now().minusMinutes(30)));
        SandboxAct mine = newAct(hero, SAFE_PLACE, "她自己先走了一步", 0, LocalDateTime.now().minusMinutes(30));
        SandboxAct hers = newAct(other, DANGER_PLACE, "两人一起回到了集市", 0, LocalDateTime.now().minusMinutes(10));
        hers.setCompanions(HERO);
        actMapper.update(null, new LambdaUpdateWrapper<SandboxAct>()
                .eq(SandboxAct::getId, hers.getId()).set(SandboxAct::getCompanions, HERO));

        SandboxAct found = (SandboxAct) call("actThatMentionedMe",
                new Class[]{SandboxCharacter.class}, hero);
        check("能找到「刚刚有人把你写进了 TA 的行动」那一条", found != null && found.getId().equals(hers.getId()));
        check("找出来的确实是别人写的那条（不是自己的）", found != null && !found.getCharacterId().equals(hero.getId()));

        // 把自己的行动时间推到更晚：那条就不该再算"刚刚"
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, hero.getId())
                .set(SandboxCharacter::getLastRunTime, LocalDateTime.now().plusMinutes(1)));
        check("自己之后已经行动过时，不再翻旧账",
                call("actThatMentionedMe", new Class[]{SandboxCharacter.class}, fresh(hero)) == null);
    }

    // ---------------- 脚手架 ----------------

    /**
     * 自检模式：off / suspicious（命中才查，默认）/ always（每步都查），
     * 以及老配置 sandbox_step_selfcheck=0 → off 的兜底。
     * 顺便验证「命中才查」模式下，干净的一步**不会**真的发起模型调用（审计日志不新增）。
     */
    private void testSelfCheckModes(SandboxCharacter hero) throws Exception {
        String modeBefore = configService.getConfigValue("sandbox_step_selfcheck_mode", "");
        String legacyBefore = configService.getConfigValue("sandbox_step_selfcheck", "1");
        try {
            configService.setConfigValue("sandbox_step_selfcheck_mode", "always");
            check("模式 always 能读出来", "always".equals(call("stepSelfCheckMode", new Class[]{})));
            configService.setConfigValue("sandbox_step_selfcheck_mode", "off");
            check("模式 off 能读出来", "off".equals(call("stepSelfCheckMode", new Class[]{})));
            // 模式键写坏 → 按老开关兜底
            configService.setConfigValue("sandbox_step_selfcheck_mode", "乱写的");
            configService.setConfigValue("sandbox_step_selfcheck", "0");
            check("模式写坏 + 老开关=0 → off", "off".equals(call("stepSelfCheckMode", new Class[]{})));
            configService.setConfigValue("sandbox_step_selfcheck", "1");
            check("模式写坏 + 老开关=1 → suspicious（默认）",
                    "suspicious".equals(call("stepSelfCheckMode", new Class[]{})));

            // 命中才查：干净的一步不发起调用
            configService.setConfigValue("sandbox_step_selfcheck_mode", "suspicious");
            SandboxAct prev = newAct(hero, SAFE_PLACE, "在安全城吃了碗面", -2, LocalDateTime.now().minusMinutes(40));
            SandboxAct clean = newAct(hero, SAFE_PLACE, "她在安全城的面摊花 2 金币吃了碗热汤面，心情放松下来", -2,
                    LocalDateTime.now());
            clean.setSummary("又吃了碗面");
            String before = actMapper.selectById(clean.getId()).getActions();
            int logsBefore = countApiLogs();
            call("stepSelfCheck", new Class[]{SandboxCharacter.class, SandboxAct.class, List.class,
                            SandboxAct.class, String.class, cn.hutool.json.JSONObject.class, boolean.class},
                    fresh(hero), clean, java.util.Collections.singletonList(prev), null, null, null, false);
            check("命中才查：没有疑点的一步不会发起额外调用", countApiLogs() == logsBefore);
            check("命中才查：没有疑点的一步原文不变",
                    before != null && before.equals(actMapper.selectById(clean.getId()).getActions()));
        } finally {
            configService.setConfigValue("sandbox_step_selfcheck_mode", modeBefore);
            configService.setConfigValue("sandbox_step_selfcheck", legacyBefore);
        }
    }

    private int countApiLogs() {
        return jdbc.queryForObject("select count(*) from admin_api_log", Integer.class);
    }

    @SuppressWarnings("unchecked")
    private List<String> issuesOf(SandboxCharacter hero, SandboxAct act, List<SandboxAct> recent,
                                  SandboxAct mentioned, String encounter) throws Exception {
        return issuesOf(hero, act, recent, mentioned, encounter, null);
    }

    private List<String> issuesOf(SandboxCharacter hero, SandboxAct act, List<SandboxAct> recent,
                                  SandboxAct mentioned, String encounter, cn.hutool.json.JSONObject obj) throws Exception {
        return (List<String>) call("narrativeIssues",
                new Class[]{SandboxCharacter.class, SandboxAct.class, List.class, SandboxAct.class, String.class, cn.hutool.json.JSONObject.class},
                hero, act, recent, mentioned, encounter, obj);
    }

    private String encounterOf(SandboxCharacter hero) throws Exception {
        Object value = call("buildEncounter", new Class[]{SandboxCharacter.class}, hero);
        return value == null ? null : String.valueOf(value);
    }

    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        return method.invoke(target == null ? service : target, args);
    }

    private SandboxCharacter fresh(SandboxCharacter character) {
        return characterMapper.selectById(character.getId());
    }

    private SandboxCharacter moveTo(SandboxCharacter hero, String place, int x, int y) {
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, hero.getId())
                .set(SandboxCharacter::getLocationName, place)
                .set(SandboxCharacter::getSubLocation, "测试点")
                .set(SandboxCharacter::getX, x)
                .set(SandboxCharacter::getY, y));
        return fresh(hero);
    }

    private Long setUpWorld() {
        SandboxWorld world = new SandboxWorld();
        world.setName(WORLD_NAME);
        world.setDescription("（探针自动创建，跑完即删）");
        world.setWorldPrompt("剑与魔法的世界。");
        world.setEnabled(0);
        world.setPortalVisible(0);
        world.setMapImage("");
        worldMapper.insert(world);
        insertLocation(world.getId(), SAFE_PLACE, 20, 20, 0, 1, 5);
        insertLocation(world.getId(), DANGER_PLACE, 80, 80, 3, 25, 55);
        return world.getId();
    }

    private void insertLocation(Long worldId, String name, int x, int y, int danger, int min, int max) {
        SandboxLocation location = new SandboxLocation();
        location.setWorldId(worldId);
        location.setName(name);
        location.setX(x);
        location.setY(y);
        location.setWidth(10);
        location.setHeight(10);
        location.setDangerLevel(danger);
        location.setPowerMin(min);
        location.setPowerMax(max);
        location.setSortOrder(0);
        locationMapper.insert(location);
    }

    private SandboxCharacter setUpCharacter(Long worldId, String name, String place, int power) {
        SandboxCharacter character = new SandboxCharacter();
        character.setWorldId(worldId);
        character.setName(name);
        character.setTitle("测试者");
        character.setPersona("（探针角色）");
        character.setAppearance("");
        character.setX(80);
        character.setY(80);
        character.setLocationName(place);
        character.setSubLocation("测试点");
        character.setStatusJson("{\"体力\":90,\"魔力\":60,\"饥饿度\":20,\"心情\":\"平静\",\"伤势\":\"无恙\"}");
        character.setCoins(50);
        character.setCombatPower(power);
        character.setEquipPower(0);
        character.setEnabled(1);
        character.setIntervalMin(45);
        character.setIntervalMax(75);
        character.setLastRunTime(LocalDateTime.now().minusMinutes(60));
        characterMapper.insert(character);
        return character;
    }

    private SandboxAct newAct(SandboxCharacter character, String place, String text, int coinChange,
                              LocalDateTime time) {
        SandboxAct act = new SandboxAct();
        act.setWorldId(character.getWorldId());
        act.setCharacterId(character.getId());
        act.setLocationName(place);
        act.setSubLocation("测试点");
        act.setActions(text);
        act.setSummary(text);
        act.setCoinChange(coinChange);
        act.setInnerVoice("（探针）");
        act.setCreateTime(time);
        act.setFromAi(1);
        actMapper.insert(act);
        return act;
    }

    private void check(String what, boolean ok) {
        if (ok) {
            passed++;
            report.add("- ✅ " + what);
            System.out.println("  ✅ " + what);
        } else {
            report.add("- ❌ " + what);
            writeReport("失败：" + what);
            throw new AssertionError("探针失败：" + what);
        }
    }

    private void writeReport(String title) {
        try {
            Path dir = Paths.get("target", "probe");
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder("# 遭遇规则探针报告\n\n");
            sb.append("结果：").append(title).append("\n\n");
            for (String line : report) {
                sb.append(line).append("\n");
            }
            Files.write(dir.resolve("encounter-probe.md"), sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("报告：target/probe/encounter-probe.md");
        } catch (Exception ignored) {
            // 写报告失败不影响探针本身
        }
    }
}
