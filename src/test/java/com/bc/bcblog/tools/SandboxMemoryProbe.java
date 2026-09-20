package com.bc.bcblog.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxMemory;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.mapper.SandboxActMapper;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxMemoryMapper;
import com.bc.bcblog.mapper.SandboxWorldMapper;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 「每日记忆按日期补生成」的探针（**不调用 AI**，也不会碰到真实角色的数据）。
 *
 * 覆盖：
 *   1. 能只为**指定日期**生成记忆（昨天的行动算昨天，不会混进今天）；
 *   2. 那一天没有行动的角色会被跳过、整体返回 0；
 *   3. 同一天重复生成是**覆盖**，不会写出两条；
 *   4. 记忆列表能按日期过滤（后台"只看这一天"用的就是它）；
 *   5. 日期格式写错会明确报错。
 *
 * 说明：AI 总结这一步故意用一个不存在的模型名，让它快速失败并走兜底拼接，
 * 这样探针既不花额度、也不依赖网络。
 *
 * 手动运行： mvn test "-Dtest=SandboxMemoryProbe"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxMemoryProbe {

    static {
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final String WORLD_NAME = "【探针】记忆日期测试世界";
    private static final String HERO = "【探针】记忆测试者";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private SandboxWorldMapper worldMapper;
    @Autowired
    private SandboxCharacterMapper characterMapper;
    @Autowired
    private SandboxActMapper actMapper;
    @Autowired
    private SandboxMemoryMapper memoryMapper;

    private int passed = 0;
    private final java.util.List<String> report = new java.util.ArrayList<>();

    @Test
    void run() throws Exception {
        Long worldId = null;
        try {
            worldId = setUpWorld();
            SandboxCharacter hero = setUpCharacter(worldId);
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDate today = LocalDate.now();

            // 昨天两条行动、今天一条
            addAct(hero, yesterday.atTime(10, 0), "在集市卖掉了昨天采的药草", 3);
            addAct(hero, yesterday.atTime(18, 30), "在旅店吃了顿热饭，早早睡下", -1);
            addAct(hero, today.atTime(9, 0), "一早起来去协会看委托板", 0);

            // ① 只生成昨天的
            check("昨天有行动的日期能生成", summarizeOne(hero, yesterday));
            SandboxMemory mem = memoryOf(hero, yesterday);
            check("写出来的记忆日期是昨天", mem != null && yesterday.equals(mem.getMemoryDate()));
            check("只统计了昨天的行动（2 条）", mem != null && Integer.valueOf(2).equals(mem.getActCount()));
            check("今天的行动没有被算进昨天", mem != null && mem.getSummary() != null
                    && !mem.getSummary().contains("委托板"));
            check("今天还没有记忆", memoryOf(hero, today) == null);

            // ② 重复生成 = 覆盖，不会多出一条
            check("同一天再生成一次仍然写入成功", summarizeOne(hero, yesterday));
            check("同一天只有一条记忆（覆盖，不是新增）", countMemories(hero, yesterday) == 1);
            check("今天的记忆也能单独生成", summarizeOne(hero, today));
            check("今天写出来的是 1 条行动", Integer.valueOf(1).equals(memoryOf(hero, today).getActCount()));

            // ③ 没有行动的日期：跳过 + 整体计数为 0
            check("没有行动的日期不写记忆", !summarizeOne(hero, yesterday.minusDays(10)));
            check("全库范围生成一个没人行动的日期 → 返回 0",
                    service.summarizeOn("2000-01-01") == 0);

            // ④ 记忆列表按日期过滤
            PageResult<SandboxMemory> onlyYesterday = service.memoryPage(hero.getId(),
                    yesterday.toString(), 1, 20);
            check("列表能只看昨天（1 条）", onlyYesterday.getTotal() == 1);
            check("列表能只看今天（1 条）", service.memoryPage(hero.getId(), today.toString(), 1, 20).getTotal() == 1);
            check("不看日期时两条都在", service.memoryPage(hero.getId(), null, 1, 20).getTotal() == 2);
            check("查一个没有记忆的日期是空列表",
                    service.memoryPage(hero.getId(), yesterday.minusDays(10).toString(), 1, 20).getTotal() == 0);

            // ⑤ 日期格式写错要报错（而不是默默生成今天）
            check("日期格式写错会报错", throwsBusiness(() -> service.summarizeOn("2026/09/19")));

            writeReport("全部通过（" + passed + " 项）");
            System.out.println("\n===== 每日记忆探针：全部通过（" + passed + " 项）=====");
        } catch (Throwable e) {
            writeReport("失败：" + e.getMessage());
            throw e;
        } finally {
            if (worldId != null) {
                actMapper.delete(new QueryWrapper<SandboxAct>().eq("world_id", worldId));
                memoryMapper.delete(new QueryWrapper<SandboxMemory>().eq("world_id", worldId));
                characterMapper.delete(new QueryWrapper<SandboxCharacter>().eq("world_id", worldId));
                worldMapper.deleteById(worldId);
                System.out.println("临时世界已清理（world_id=" + worldId + "）");
            }
        }
    }

    /** 只对这一个角色跑"生成指定日期记忆"，返回是否真的写了 */
    private boolean summarizeOne(SandboxCharacter hero, LocalDate date) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod("summarize",
                SandboxCharacter.class, LocalDate.class);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        return Boolean.TRUE.equals(method.invoke(target == null ? service : target, hero, date));
    }

    private boolean throwsBusiness(Runnable action) {
        try {
            action.run();
            return false;
        } catch (BusinessException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private SandboxMemory memoryOf(SandboxCharacter hero, LocalDate date) {
        return memoryMapper.selectOne(new QueryWrapper<SandboxMemory>()
                .eq("character_id", hero.getId()).eq("memory_date", date).last("limit 1"));
    }

    private int countMemories(SandboxCharacter hero, LocalDate date) {
        return memoryMapper.selectCount(new QueryWrapper<SandboxMemory>()
                .eq("character_id", hero.getId()).eq("memory_date", date)).intValue();
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
        return world.getId();
    }

    private SandboxCharacter setUpCharacter(Long worldId) {
        SandboxCharacter hero = new SandboxCharacter();
        hero.setWorldId(worldId);
        hero.setName(HERO);
        hero.setTitle("测试者");
        hero.setPersona("（探针角色）");
        hero.setAppearance("");
        hero.setX(20);
        hero.setY(20);
        hero.setLocationName("【探针】某地");
        hero.setStatusJson("{\"体力\":90,\"魔力\":60,\"饥饿度\":20,\"心情\":\"平静\",\"伤势\":\"无恙\"}");
        hero.setCoins(0);
        hero.setCombatPower(10);
        hero.setEnabled(1);
        hero.setIntervalMin(45);
        hero.setIntervalMax(75);
        // 故意用一个不存在的模型：AI 总结会立刻失败 → 走兜底拼接，探针不花额度
        hero.setModel("【探针】不存在的模型");
        characterMapper.insert(hero);
        return hero;
    }

    private void addAct(SandboxCharacter hero, LocalDateTime time, String summary, int coinChange) {
        SandboxAct act = new SandboxAct();
        act.setWorldId(hero.getWorldId());
        act.setCharacterId(hero.getId());
        act.setCreateTime(time);
        act.setLocationName("【探针】某地");
        act.setSubLocation("测试点");
        act.setActions(summary);
        act.setSummary(summary);
        act.setCoinChange(coinChange);
        act.setFromAi(1);
        act.setInnerVoice("（探针）");
        actMapper.insert(act);
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
            StringBuilder sb = new StringBuilder("# 每日记忆探针报告\n\n");
            sb.append("结果：").append(title).append("\n\n");
            for (String line : report) {
                sb.append(line).append("\n");
            }
            Files.write(dir.resolve("memory-probe.md"), sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("报告：target/probe/memory-probe.md");
        } catch (Exception ignored) {
            // 写报告失败不影响探针本身
        }
    }
}
