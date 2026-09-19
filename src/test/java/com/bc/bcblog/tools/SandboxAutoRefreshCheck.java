package com.bc.bcblog.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bc.bcblog.entity.SandboxNews;
import com.bc.bcblog.entity.SandboxQuest;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.mapper.SandboxNewsMapper;
import com.bc.bcblog.mapper.SandboxQuestMapper;
import com.bc.bcblog.mapper.SandboxWorldMapper;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 自动刷新「到期判定」探针（不调用 AI）。
 *
 * 集市 / 委托板 / 纪闻三处用的是同一套口径：
 *   间隔 ≥ 24 小时 → 按天对齐（24 = 明天这个点刷新，48 = 后天，写 30 这种当作 1 天）；
 *   间隔 &lt; 24 小时 → 距上一批满 N 小时就刷。
 * 这些判定藏在私有方法里，一旦写错只会表现为"该刷的时候不刷 / 一直刷"，很难从界面看出来，
 * 所以这里用「改配置 + 改上一批时间」的方式把各种组合穷举一遍。
 *
 * 全程在自己的临时世界里跑，跑完删干净。手动运行： mvn test "-Dtest=SandboxAutoRefreshCheck"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxAutoRefreshCheck {

    static {
        // 测试进程里禁用定时任务（见 tools/README.md）
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final String WORLD_NAME = "【探针】自动刷新测试世界";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private ConfigService configService;
    @Autowired
    private SandboxWorldMapper worldMapper;
    @Autowired
    private SandboxQuestMapper questMapper;
    @Autowired
    private SandboxNewsMapper newsMapper;

    private int passed = 0;
    private final java.util.List<String> report = new java.util.ArrayList<>();

    @Test
    void run() throws Exception {
        Long worldId = null;
        // 这些键是全局配置：跑之前先备份，跑完原样还回去
        String[] keys = {
                "sandbox_quest_enabled", "sandbox_quest_auto_enabled",
                "sandbox_quest_interval_hours", "sandbox_quest_auto_time",
                "sandbox_news_enabled", "sandbox_news_auto_enabled",
                "sandbox_news_interval_hours", "sandbox_news_auto_time"};
        String[] backup = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            backup[i] = configService.getConfigValue(keys[i], "");
        }
        try {
            worldId = setUpWorld();
            Long questId = insertQuest(worldId);
            Long newsId = insertNews(worldId);

            testQuestDue(worldId, questId);
            testNewsDue(worldId, newsId);
            testSwitchesAndCooldown(worldId);

            writeReport("全部通过（" + passed + " 项）");
            System.out.println("\n===== 自动刷新探针：全部通过（" + passed + " 项）=====");
        } catch (Throwable e) {
            writeReport("失败：" + e.getMessage());
            throw e;
        } finally {
            for (int i = 0; i < keys.length; i++) {
                if (!backup[i].isEmpty()) {
                    configService.setConfigValue(keys[i], backup[i]);
                }
            }
            if (worldId != null) {
                questMapper.delete(new QueryWrapper<SandboxQuest>().eq("world_id", worldId));
                newsMapper.delete(new QueryWrapper<SandboxNews>().eq("world_id", worldId));
                worldMapper.deleteById(worldId);
                System.out.println("临时世界已清理（world_id=" + worldId + "）");
            }
        }
    }

    /** 委托板：间隔 / 首次时间 / 上一批时间 的各种组合 */
    private void testQuestDue(Long worldId, Long questId) throws Exception {
        // 上一批 = 今天 10:00，每天 09:00 刷 → 下一次是明天 09:00，现在不该刷
        configService.setConfigValue("sandbox_quest_interval_hours", "24");
        configService.setConfigValue("sandbox_quest_auto_time", "09:00");
        setQuestBatchTime(questId, LocalDate.now().atTime(10, 0));
        check("委托板：今天 10:00 刷过（次日 09:00）→ 不再刷", !questDue(worldId));

        // 上一批 = 昨天 10:00 → 今天 09:00 到期
        setQuestBatchTime(questId, LocalDate.now().minusDays(1).atTime(10, 0));
        check("委托板：昨天刷过（今天 09:00 到期）→ 按点判断",
                questDue(worldId) == !LocalDateTime.now().isBefore(LocalDate.now().atTime(9, 0)));

        // 间隔 6 小时：上一批 7 小时前 → 该刷；5 小时前 → 还不该刷
        configService.setConfigValue("sandbox_quest_interval_hours", "6");
        setQuestBatchTime(questId, LocalDateTime.now().minusHours(7));
        check("委托板：间隔 6 小时、上一批 7 小时前 → 该刷", questDue(worldId));
        setQuestBatchTime(questId, LocalDateTime.now().minusHours(5));
        check("委托板：间隔 6 小时、上一批 5 小时前 → 还不该刷", !questDue(worldId));

        // 间隔 48 小时（两天一次）：昨天刷过 → 明天才刷；三天前刷过 → 今天该刷
        configService.setConfigValue("sandbox_quest_interval_hours", "48");
        setQuestBatchTime(questId, LocalDate.now().minusDays(1).atTime(10, 0));
        check("委托板：间隔 48 小时、昨天刷过 → 明天才刷", !questDue(worldId));
        setQuestBatchTime(questId, LocalDate.now().minusDays(3).atTime(10, 0));
        check("委托板：间隔 48 小时、三天前刷过 → 已经到期", questDue(worldId));

        // 板上一条都没有（新世界 / 全被删光）→ 到「当天首次时间」就补一批
        configService.setConfigValue("sandbox_quest_interval_hours", "24");
        configService.setConfigValue("sandbox_quest_auto_time", "09:00");
        questMapper.deleteById(questId);
        check("委托板：一条都没有时按「当天首次时间」判断",
                questDue(worldId) == !LocalDateTime.now().isBefore(LocalDate.now().atTime(9, 0)));
    }

    /** 纪闻：同一套口径，只是"上一批"看最新一条纪闻的生成时间 */
    private void testNewsDue(Long worldId, Long newsId) throws Exception {
        configService.setConfigValue("sandbox_news_interval_hours", "24");
        configService.setConfigValue("sandbox_news_auto_time", "07:00");
        setNewsCreateTime(newsId, LocalDate.now().atTime(8, 0));
        check("纪闻：今天 08:00 生成过（次日 07:00）→ 不再生成", !newsDue(worldId));

        setNewsCreateTime(newsId, LocalDate.now().minusDays(1).atTime(8, 0));
        check("纪闻：昨天生成过（今天 07:00 到期）→ 按点判断",
                newsDue(worldId) == !LocalDateTime.now().isBefore(LocalDate.now().atTime(7, 0)));

        configService.setConfigValue("sandbox_news_interval_hours", "6");
        setNewsCreateTime(newsId, LocalDateTime.now().minusHours(7));
        check("纪闻：间隔 6 小时、上一批 7 小时前 → 该生成", newsDue(worldId));
        setNewsCreateTime(newsId, LocalDateTime.now().minusHours(5));
        check("纪闻：间隔 6 小时、上一批 5 小时前 → 还不该生成", !newsDue(worldId));
    }

    /** 开关与冷却：关掉开关时定时入口应该什么都不做，也绝不调 AI；失败重试有 10 分钟冷却 */
    private void testSwitchesAndCooldown(Long worldId) throws Exception {
        configService.setConfigValue("sandbox_quest_enabled", "1");
        configService.setConfigValue("sandbox_quest_auto_enabled", "0");
        check("委托板：自动刷新开关关掉时定时入口直接返回 0", service.autoRefreshQuests() == 0);

        configService.setConfigValue("sandbox_quest_auto_enabled", "1");
        configService.setConfigValue("sandbox_quest_enabled", "0");
        check("委托板：委托板整体关掉时定时入口直接返回 0", service.autoRefreshQuests() == 0);
        configService.setConfigValue("sandbox_quest_enabled", "1");

        configService.setConfigValue("sandbox_news_enabled", "1");
        configService.setConfigValue("sandbox_news_auto_enabled", "0");
        check("纪闻：自动生成开关关掉时定时入口直接返回 0", service.autoRefreshNews() == 0);
        configService.setConfigValue("sandbox_news_auto_enabled", "1");
        configService.setConfigValue("sandbox_news_enabled", "0");
        check("纪闻：栏目整体关掉时定时入口直接返回 0", service.autoRefreshNews() == 0);
        configService.setConfigValue("sandbox_news_enabled", "1");

        // 冷却：同一个「刷新源 + 世界」短时间内只能放行一次（AI 挂了也不会每分钟重试）
        check("冷却：同一世界第一次放行", Boolean.TRUE.equals(call("autoRefreshAllowed",
                new Class<?>[]{String.class, Long.class}, "probe", worldId)));
        check("冷却：立刻再来一次被挡住", Boolean.FALSE.equals(call("autoRefreshAllowed",
                new Class<?>[]{String.class, Long.class}, "probe", worldId)));
        check("冷却：不同世界互不影响", Boolean.TRUE.equals(call("autoRefreshAllowed",
                new Class<?>[]{String.class, Long.class}, "probe", worldId + 1)));
    }

    // ---------------- 反射与脚手架 ----------------

    private boolean questDue(Long worldId) throws Exception {
        return Boolean.TRUE.equals(call("questRefreshDue", new Class<?>[]{Long.class}, worldId));
    }

    private boolean newsDue(Long worldId) throws Exception {
        return Boolean.TRUE.equals(call("newsRefreshDue", new Class<?>[]{Long.class}, worldId));
    }

    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        return method.invoke(target == null ? service : target, args);
    }

    private void setQuestBatchTime(Long questId, LocalDateTime time) {
        questMapper.update(null, new UpdateWrapper<SandboxQuest>()
                .eq("id", questId).set("batch_time", time));
    }

    private void setNewsCreateTime(Long newsId, LocalDateTime time) {
        newsMapper.update(null, new UpdateWrapper<SandboxNews>()
                .eq("id", newsId).set("create_time", time));
    }

    private Long setUpWorld() {
        SandboxWorld world = new SandboxWorld();
        world.setName(WORLD_NAME);
        world.setDescription("（探针自动创建，跑完即删）");
        world.setWorldPrompt("剑与魔法的世界。");
        world.setEnabled(1);
        world.setPortalVisible(0);
        world.setMapImage("");
        worldMapper.insert(world);
        return world.getId();
    }

    private Long insertQuest(Long worldId) {
        SandboxQuest quest = new SandboxQuest();
        quest.setWorldId(worldId);
        quest.setBatchTime(LocalDateTime.now());
        quest.setTitle("【探针】自动刷新用委托");
        quest.setDescription("（探针委托）");
        quest.setQuestType("gather");
        quest.setDifficulty(1);
        quest.setLocationName("【探针】某地");
        quest.setTarget("（探针目标）");
        quest.setRewardCoins(1);
        quest.setProgress(0);
        quest.setStatus("open");
        quest.setSource("admin");
        quest.setPinned(0);
        quest.setEnabled(1);
        questMapper.insert(quest);
        return quest.getId();
    }

    private Long insertNews(Long worldId) {
        SandboxNews news = new SandboxNews();
        news.setWorldId(worldId);
        news.setTitle("【探针】自动生成用纪闻");
        news.setContent("（探针纪闻）");
        news.setLocationName("【探针】某地");
        news.setNewsDate(LocalDate.now());
        news.setLevel(1);
        news.setSource("admin");
        news.setPinned(0);
        news.setEnabled(1);
        news.setCreateTime(LocalDateTime.now());
        newsMapper.insert(news);
        return news.getId();
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
            java.nio.file.Path dir = java.nio.file.Paths.get("target", "probe");
            java.nio.file.Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder("# 沙盒自动刷新探针报告\n\n");
            sb.append("结果：").append(title).append("\n\n");
            sb.append("当前时间：").append(LocalDateTime.now()).append("（判定跟真实时钟无关，见每条的说明）\n\n");
            for (String line : report) {
                sb.append(line).append("\n");
            }
            java.nio.file.Files.write(dir.resolve("auto-refresh.md"),
                    sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("报告：target/probe/auto-refresh.md");
        } catch (Exception ignored) {
            // 写报告失败不影响探针本身
        }
    }
}
