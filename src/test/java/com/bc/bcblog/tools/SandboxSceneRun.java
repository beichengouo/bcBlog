package com.bc.bcblog.tools;

import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【保留工具】沙盒通用场景模拟器 —— 用来整段跑剧情、检查各项机制是否符合预期。
 *
 * 它是"临时世界 + 模拟时钟 + 多角色轮转"的组合：跑完会把临时世界里的数据全都删掉，
 * 不碰真实世界。所有参数都用系统属性传入，所以以后要测什么，改参数就行，不必再写新类。
 *
 * 用法（PowerShell，注意参数要加引号）：
 *   mvn test "-Dtest=SandboxSceneRun" "-Dsandbox.scene.chars=3" "-Dsandbox.scene.minutes=1440" ^
 *            "-Dsandbox.scene.style=danger" "-Dsandbox.scene.location=自由城邦联盟"
 *
 * 可调参数：
 *   sandbox.scene.chars     角色人数（1~4，默认 3）
 *   sandbox.scene.minutes   模拟时长（分钟，默认 1440 = 一天）
 *   sandbox.scene.location  起始一级地点（默认 自由城邦联盟）
 *   sandbox.scene.style     mix（混合，默认）/ danger（爱冒险、往危险地方跑）/ daily（日常）
 *   sandbox.scene.news      是否插入纪闻（true/false，默认 true）
 *   sandbox.scene.keep      跑完是否保留临时世界（true/false，默认 false；调试用）
 *   sandbox.scene.out       报告文件名（默认 target/probe/scene.md）
 *
 * 报告里能看到：每步的地点、时长、摘要、动作、运气、遭遇、伤势、金币、物品、纪闻引用与回应行动，
 * 以及汇总统计（遭遇次数、带伤步数、集市订单、想法变化等）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxSceneRun {

    static {
        // 在 Spring 启动之前就把定时任务关掉：测试进程会设"模拟时钟偏移"，
        // 如果定时任务跟着跑，它会拿模拟时钟判断"谁到期了"，把**真实世界**里
        // 本该明天才行动的角色提前执行掉（曾经真的污染过一次正式数据）。
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final Long SOURCE_WORLD = 2L;
    private static final Long PROVIDER_ID = 2L;
    private static final String MODEL = "gemini-3-flash-preview";
    private static final String OFFSET_KEY = "bcblog.sandbox.time-offset-minutes";
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    /** 单次最多跑多少步（默认 100；两天双角色大约 70~90 步，可用 sandbox.scene.maxSteps 调整） */
    private static final int DEFAULT_MAX_STEPS = 100;

    private static final String[][] CAST = {
            {"__场景·艾拉__", "见习冒险者", "接委托赚钱，给乡下的妹妹攒学费", "乐观、话多、对钱很上心"},
            {"__场景·卡恩__", "老练佣兵", "找报酬丰厚的活儿，越危险越愿意接", "胆大、好斗、不爱废话"},
            {"__场景·迪雅__", "魔法学徒", "采集药草练习魔法，顺便查清奇怪传闻", "好奇心重、看到怪东西就想凑近"},
            {"__场景·诺兰__", "游商", "在各个城邦之间跑货，攒钱盘个铺面", "精打细算、说话滴水不漏"}
    };

    private static final String[] DANGER_GOALS = {
            "去魔物森林深处碰碰运气，听说那边值钱的东西多",
            "去最危险的地方找值钱的战利品",
            "深入危险地区调查，顺便赚一笔"
    };

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private SandboxCharacterMapper characterMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private final StringBuilder report = new StringBuilder();
    private final Map<Long, LocalDateTime> simTimes = new LinkedHashMap<>();
    private final Map<Long, String> names = new LinkedHashMap<>();
    private final List<Long> castIds = new ArrayList<>();
    private int requests = 0;
    private LocalDateTime start;
    private int encounterSteps = 0;
    private int injuredSteps = 0;
    /** 各档伤势出现多少步（无恙 / 轻伤 / 重伤 / 濒死），用来看"AI 是不是在平白受伤" */
    private final Map<String, Integer> injuryCounts = new LinkedHashMap<>();
    /** 每条行动的耗时（毫秒），用来算"一次行动接口平均要等多久" */
    private final List<Long> stepMillis = new ArrayList<>();
    /** 每条行动触发了多少次模型请求（主调用 + 补全/自查） */
    private final List<Integer> stepRequests = new ArrayList<>();
    /** 各角色开跑前的提示词字符数（token 量的近似） */
    private final Map<Long, Integer> promptChars = new LinkedHashMap<>();
    /** 跑之前就摆到委托板上的委托 id */
    private final List<Long> questIds = new ArrayList<>();
    /** 已经做过记忆总结的日子（跨天时补一次，模拟真实世界的"每天定时总结"） */
    private final java.util.Set<LocalDate> summarizedDays = new java.util.LinkedHashSet<>();
    /** 记忆总结的耗时，单独统计（它也走 AI，但不属于角色行动） */
    private final List<Long> memoryMillis = new ArrayList<>();

    @Test
    void run() throws Exception {
        int chars = Math.max(1, Math.min(4, intProp("sandbox.scene.chars", 3)));
        int minutes = Math.max(60, intProp("sandbox.scene.minutes", 1440));
        String location = strProp("sandbox.scene.location", "自由城邦联盟");
        String style = strProp("sandbox.scene.style", "mix");
        boolean withNews = boolProp("sandbox.scene.news", true);
        boolean withQuests = boolProp("sandbox.scene.quests", true);
        boolean keep = boolProp("sandbox.scene.keep", false);
        String out = strProp("sandbox.scene.out", "scene.md");
        // 把每一次 AI 调用的完整请求与返回都落盘（默认开；不想要就 -Dsandbox.scene.logAi=false）
        boolean logAi = boolProp("sandbox.scene.logAi", true);
        if (logAi) {
            System.setProperty("bcblog.ai.log-file",
                    Paths.get("target", "probe", "ai-log-" + out.replace(".md", "") + ".md").toString());
        }

        Long worldId = null;
        try {
            worldId = createWorld(withNews);
            if (withQuests) {
                seedQuests(worldId);
            }
            start = LocalDate.now().atTime(12, 0);
            // 模拟时间整体挪到两年前：记忆总结（summarizeOn）会遍历**所有**世界，
            // 放在"肯定没有正式数据"的日期上，就不会动到真实世界的记忆。
            start = start.minusYears(2);
            report.append("# 沙盒场景模拟（").append(style).append("）\n\n")
                    .append("- 角色 ").append(chars).append(" 人；起始地点 ").append(location)
                    .append("；模拟 ").append(minutes).append(" 分钟（")
                    .append(String.format("%.2f", minutes / 1440.0)).append(" 天）\n")
                    .append("- 模型 ").append(MODEL).append("；纪闻 ").append(withNews ? "有" : "无")
                    .append("；委托板 ").append(withQuests ? (questIds.size() + " 条") : "关闭")
                    .append("；临时世界 id=").append(worldId).append('\n');
            if (logAi) {
                report.append("- 每一次 AI 调用的完整请求与返回已记录到 ")
                        .append("target/probe/ai-log-").append(out.replace(".md", "")).append(".md\n");
            }
            for (int i = 0; i < chars; i++) {
                String[] c = CAST[i];
                String goal = "danger".equals(style) ? DANGER_GOALS[i % DANGER_GOALS.length] : c[2];
                Long id = create(worldId, c[0], c[1], goal, c[3], location);
                castIds.add(id);
                simTimes.put(id, start);
                names.put(id, c[0]);
            }
            for (Long id : castIds) {
                promptChars.put(id, promptChars(id));
            }
            selfCheckSocialGuard(worldId);
            report.append('\n');

            int maxSteps = Math.max(1, intProp("sandbox.scene.maxSteps", DEFAULT_MAX_STEPS));
            int steps = 0;
            while (steps < maxSteps) {
                Long next = earliest();
                if (next == null || !simTimes.get(next).isBefore(start.plusMinutes(minutes))) {
                    break;
                }
                steps++;
                runStep(steps, next, worldId);
            }
            summary(worldId);
        } finally {
            System.clearProperty(OFFSET_KEY);
            if (worldId != null && !keep) {
                cleanup(worldId);
            } else if (worldId != null) {
                report.append("\n（按 sandbox.scene.keep=true 保留了临时世界 id=").append(worldId).append("）\n");
            }
            Path dir = Paths.get("target", "probe");
            Files.createDirectories(dir);
            Path file = dir.resolve(out);
            Files.write(file, report.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("scene report: " + file.toAbsolutePath());
        }
    }

    private void runStep(int step, Long id, Long worldId) {
        LocalDateTime sim = simTimes.get(id);
        System.setProperty(OFFSET_KEY, String.valueOf(Duration.between(LocalDateTime.now(), sim).toMinutes()));
        Integer maxIdBefore = jdbc.queryForObject(
                "select ifnull(max(id), 0) from sandbox_act where world_id = ?", Integer.class, worldId);
        SandboxCharacter before = characterMapper.selectById(id);
        int callsBefore = apiCalls();
        SandboxAct act = null;
        String error = null;
        long t0 = System.nanoTime();
        try {
            act = service.runOnce(id, true);
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        long millis = (System.nanoTime() - t0) / 1_000_000L;
        int calls = apiCalls() - callsBefore;
        requests += calls;
        stepMillis.add(millis);
        stepRequests.add(calls);
        if (act == null) {
            report.append("- [").append(sim.format(HM)).append("] ").append(names.get(id))
                    .append(" **失败**（").append(millis).append(" ms）：").append(error).append('\n');
            simTimes.put(id, sim.plusMinutes(60));
            return;
        }
        SandboxCharacter after = characterMapper.selectById(id);
        String injury = injuryOf(after.getStatusJson());
        if (!"无恙".equals(injury)) {
            injuredSteps++;
        }
        injuryCounts.merge(injury, 1, Integer::sum);
        if (act.getEncounter() != null && !act.getEncounter().trim().isEmpty()) {
            encounterSteps++;
        }
        report.append("- [").append(sim.format(HM)).append("] **").append(names.get(id)).append("** ｜ ")
                .append(safe(act.getLocationName())).append(" · ").append(safe(act.getSubLocation()))
                .append(" ｜ ").append(act.getNextAfterMinutes()).append(" 分钟（")
                .append(safe(act.getNextAfterReason())).append("）")
                .append(" ｜ 耗时 ").append(millis).append(" ms / ").append(calls).append(" 次调用\n")
                .append("    - ").append(safe(act.getSummary())).append('\n')
                .append("    - 运气：").append(luckText(act.getLuck()))
                .append("；遭遇：").append(act.getEncounter() == null ? "无" : act.getEncounter())
                .append('\n')
                .append("    - 伤势：").append(injuryOf(before.getStatusJson())).append(" → ").append(injury)
                .append("；体力 ").append(value(before.getStatusJson(), "体力")).append(" → ")
                .append(value(after.getStatusJson(), "体力"))
                .append("；金币变化 ").append(act.getCoinChange())
                .append("；战斗力变化 ").append(act.getCombatChange())
                .append(act.getCompanions() == null ? "" : "；同行：" + act.getCompanions()).append('\n');
        if (act.getQuestEvent() != null) {
            report.append("    - 委托：").append(act.getQuestEvent()).append('\n');
        }
        String questLine = questStateLine();
        if (!questLine.isEmpty()) {
            report.append("    - 委托板：").append(questLine).append('\n');
        }
        if (act.getItemChange() != null) {
            report.append("    - 物品：").append(act.getItemChange()).append('\n');
        }
        if (act.getNewsRef() != null) {
            report.append("    - 纪闻：").append(act.getNewsRef()).append('\n');
        }
        // 服务端自动触发的回应行动
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select c.name, a.location_name, a.sub_location, a.next_after_minutes, a.luck, left(a.summary,60) as summary"
                        + " from sandbox_act a join sandbox_character c on c.id = a.character_id"
                        + " where a.world_id = ? and a.reaction = 1 and a.id > ? order by a.id", worldId, maxIdBefore);
        for (Map<String, Object> row : rows) {
            report.append("    - ↳ **回应**：").append(row.get("name")).append(" ｜ ")
                    .append(row.get("location_name")).append(" · ").append(row.get("sub_location"))
                    .append("（").append(row.get("next_after_minutes")).append(" 分钟，运气 ")
                    .append(luckText((Integer) row.get("luck"))).append("）\n")
                    .append("      ").append(row.get("summary")).append('\n');
        }
        report.append('\n');
        simTimes.put(id, sim.plusMinutes(Math.max(act.getNextAfterMinutes() == null ? 0 : act.getNextAfterMinutes(), 15)));
        // 跨天就补一次"每日记忆总结"：这是真实世界里每天定时跑的那一步，
        // 不跑的话第二天行动时提示词里就不会有【最近的记忆】，等于漏测了一整块
        summarizeCrossedDays(simTimes.get(id), worldId);
    }

    private void summary(Long worldId) {
        report.append("## 汇总\n\n### 行动总表\n\n");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select a.id, c.name, a.reaction, a.location_name, a.sub_location, a.next_after_minutes,"
                        + " a.next_after_reason, a.companions, a.coin_change, a.combat_change, a.luck, a.encounter,"
                        + " a.news_ref, a.item_change, a.favor_change, a.status_json, left(a.summary,50) as summary,"
                        + " a.create_time, a.raw_response"
                        + " from sandbox_act a join sandbox_character c on c.id = a.character_id"
                        + " where a.world_id = ? order by a.id", worldId);
        int reactions = 0;
        int newsRefs = 0;
        int purchases = 0;
        int luckSum = 0;
        int luckCount = 0;
        for (Map<String, Object> row : rows) {
            if (((Number) row.get("reaction")).intValue() == 1) {
                reactions++;
            }
            if (row.get("news_ref") != null) {
                newsRefs++;
            }
            String items = row.get("item_change") == null ? "" : String.valueOf(row.get("item_change"));
            if (items.contains("旅人集市买下")) {
                purchases++;
            }
            Integer luck = (Integer) row.get("luck");
            if (luck != null) {
                luckSum += luck;
                luckCount++;
            }
            report.append("- #").append(row.get("id")).append(" [").append(row.get("create_time")).append("] ")
                    .append(row.get("name"))
                    .append(((Number) row.get("reaction")).intValue() == 1 ? "（回应）" : "")
                    .append(" ｜ ").append(row.get("location_name")).append(" · ").append(row.get("sub_location"))
                    .append(" ｜ ").append(row.get("next_after_minutes")).append(" 分钟")
                    .append(" ｜ 运气 ").append(luckText(luck))
                    .append(" ｜ 金币 ").append(row.get("coin_change"))
                    .append(" ｜ 战斗力 ").append(row.get("combat_change")).append('\n')
                    .append("  - ").append(row.get("summary")).append('\n')
                    .append(row.get("encounter") == null ? "" : "  - 遭遇：" + row.get("encounter") + "\n")
                    .append(row.get("news_ref") == null ? "" : "  - 纪闻：" + row.get("news_ref") + "\n")
                    .append(items.isEmpty() ? "" : "  - 物品：" + items + "\n")
                    .append(row.get("favor_change") == null ? "" : "  - 好感：" + row.get("favor_change") + "\n")
                    .append("  - 分段：").append(stageMarks(String.valueOf(row.get("raw_response")))).append('\n')
                    .append("  - 状态：").append(row.get("status_json")).append('\n');
        }
        report.append("\n### 统计\n\n")
                .append("- 行动总数：").append(rows.size()).append("（回应 ").append(reactions).append(" 条）\n")
                .append("- 被注入遭遇的步数：").append(encounterSteps).append('\n')
                .append("- 带伤状态的步数：").append(injuredSteps).append('\n')
                .append("- 伤势分布：").append(injuryCounts.isEmpty() ? "（无样本）"
                        : injuryCounts.toString().replace("{", "").replace("}", "")).append('\n')
                .append("- 引用纪闻的行动：").append(newsRefs).append('\n')
                .append("- 在集市买东西的行动：").append(purchases).append('\n')
                .append("- 平均运气：").append(luckCount == 0 ? "—" : String.format("%.2f", luckSum * 1.0 / luckCount))
                .append("（样本 ").append(luckCount).append("）\n")
                .append("- 总请求数：").append(requests).append('\n');
        performanceSection();
        questSection(worldId);
        memorySection(worldId);
        relationSection(worldId);
        report.append("### 集市订单\n\n");
        List<Map<String, Object>> orders = jdbc.queryForList(
                "select character_name, item_name, coin_price from sandbox_shop_order where world_id = ? order by id",
                worldId);
        report.append(orders.isEmpty() ? "- （无）\n" : "");
        for (Map<String, Object> row : orders) {
            report.append("- ").append(row.get("character_name")).append(" 买了 ").append(row.get("item_name"))
                    .append("（").append(row.get("coin_price")).append(" 金币）\n");
        }
        report.append("\n### 想法变化\n\n");
        List<Map<String, Object>> attitudes = jdbc.queryForList(
                "select character_name, kind, old_view, new_view, reason from sandbox_attitude_log"
                        + " where world_id = ? order by id", worldId);
        report.append(attitudes.isEmpty() ? "- （没有想法变化）\n" : "");
        for (Map<String, Object> row : attitudes) {
            report.append("- ").append(row.get("character_name")).append("：")
                    .append("power".equals(row.get("kind")) ? "实力" : "财富")
                    .append("「").append(row.get("old_view")).append("」→「").append(row.get("new_view"))
                    .append("」｜").append(row.get("reason")).append('\n');
        }
        report.append("\n### 结束时各角色\n\n");
        for (SandboxCharacter c : characterMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SandboxCharacter>()
                        .eq(SandboxCharacter::getWorldId, worldId))) {
            report.append("- ").append(c.getName()).append("：").append(c.getLocationName()).append(" · ")
                    .append(c.getSubLocation()).append(" ｜金币 ").append(c.getCoins())
                    .append(" ｜战斗力 ").append(c.getCombatPower())
                    .append(" ｜状态 ").append(c.getStatusJson()).append('\n');
        }
    }

    private String luckText(Integer luck) {
        if (luck == null) {
            return "—";
        }
        switch (luck) {
            case 3:
                return "大吉(+3)";
            case 2:
                return "走运(+2)";
            case 1:
                return "小顺(+1)";
            case 0:
                return "平常(0)";
            case -1:
                return "小背(-1)";
            case -2:
                return "倒霉(-2)";
            default:
                return "大凶(-3)";
        }
    }

    private Long earliest() {
        return simTimes.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private Long createWorld(boolean withNews) {
        // 注意 enabled 必须写 0：写成 1 的话，本地正在跑的那个实例（8080）的定时任务会把这个临时世界
        // 当成"运行中"，每隔几分钟就去驱动我们的测试角色行动——既烧额度，又会往测试数据里插进
        // 时间戳是"现在"的脏记录（实测踩到过）。本工具用 runOnce(manual=true) 手动驱动，不需要世界是运行态。
        jdbc.update("insert into sandbox_world (name, description, map_image, world_prompt, enabled,"
                + " portal_visible, create_time, update_time)"
                + " select ?, description, map_image, world_prompt, 0, 0, now(), now()"
                + " from sandbox_world where id = ?", "__场景模拟世界__", SOURCE_WORLD);
        Long worldId = jdbc.queryForObject("select max(id) from sandbox_world", Long.class);
        jdbc.update("insert into sandbox_location (world_id, name, icon, x, y, width, height, polygon,"
                + " description, danger_level, power_min, power_max, sort_order, create_time)"
                + " select ?, name, icon, x, y, width, height, polygon, description, danger_level,"
                + " power_min, power_max, sort_order, now() from sandbox_location where world_id = ?",
                worldId, SOURCE_WORLD);
        jdbc.update("insert into sandbox_shop_item (world_id, batch_time, name, description, icon, rarity,"
                + " price, original_price, stock, total_stock, source, pinned, enabled, create_time)"
                + " select ?, now(), name, description, icon, rarity, price, original_price, stock, total_stock,"
                + " source, pinned, enabled, now() from sandbox_shop_item where world_id = ? and enabled = 1",
                worldId, SOURCE_WORLD);
        if (withNews) {
            jdbc.update("insert into sandbox_news (world_id, title, content, location_name, x, y, level, source,"
                    + " news_date, pinned, enabled, create_time) values"
                    + " (?, '晨雾森林的商队遭遇魔物袭击，正在招募护卫随行', '一支商队在晨雾森林北侧遭到魔物袭击，"
                    + "货物损失过半，现正高价招募护卫随行。', '晨雾森林', 46, 68, 2, 'admin', curdate(), 1, 1, now()),"
                    + " (?, '魔物森林多支商队接连失踪，冒险家协会悬赏调查', '三支商队先后在魔物森林失踪，"
                    + "只找到被撕碎的货车残骸，协会悬赏重金调查。', '魔物森林', 23, 45, 3, 'admin', curdate(), 1, 1, now()),"
                    + " (?, '自由城邦联盟丰收祭第三天，广场摆开擂台比武', '丰收祭最后一天，广场设下擂台，"
                    + "胜者可获商会提供的奖金与合约。', '自由城邦联盟', 63, 62, 1, 'admin', curdate(), 0, 1, now())",
                    worldId, worldId, worldId);
        }
        return worldId;
    }

    private Long create(Long worldId, String name, String title, String goal, String extra, String location) {
        SandboxCharacter c = new SandboxCharacter();
        c.setWorldId(worldId);
        c.setName(name);
        c.setTitle(title);
        c.setPersona("身份：" + title + "。\n性格：" + extra + "。\n目标：" + goal
                + "\n会主动打听消息、接活，遇上机会会去争取。");
        c.setProviderId(PROVIDER_ID);
        c.setModel(MODEL);
        c.setLocationName(location);
        c.setSubLocation("金币天平广场喷泉旁");
        c.setGoal(goal);
        c.setX(63);
        c.setY(62);
        c.setCombatPower(18);
        c.setCoins(30);
        c.setPowerView("想变强");
        c.setWealthView("钱要省着花，但该花就花");
        c.setStatusJson("{\"体力\":95,\"魔力\":80,\"饥饿度\":30,\"心情\":\"平静\",\"伤势\":\"无恙\"}");
        c.setIntervalMin(45);
        c.setIntervalMax(75);
        c.setEnabled(1);
        return service.saveCharacter(c).getId();
    }

    private void cleanup(Long worldId) {
        try {
            // 委托表与区域锁是后来才加的：老版本这里漏删，跑完会留下指向已删世界的孤儿数据
            jdbc.update("delete from sandbox_quest where world_id = ?", worldId);
            jdbc.update("delete from sandbox_area_lock where world_id = ?", worldId);
            jdbc.update("delete from sandbox_coin_log where character_id in"
                    + " (select id from sandbox_character where world_id = ?)", worldId);
            jdbc.update("delete from sandbox_shop_order where world_id = ?", worldId);
            jdbc.update("delete from sandbox_item where character_id in"
                    + " (select id from sandbox_character where world_id = ?)", worldId);
            jdbc.update("delete from sandbox_relation where character_id in"
                    + " (select id from sandbox_character where world_id = ?)", worldId);
            jdbc.update("delete from sandbox_interaction where character_id in"
                    + " (select id from sandbox_character where world_id = ?)", worldId);
            jdbc.update("delete from sandbox_attitude_log where world_id = ?", worldId);
            jdbc.update("delete from sandbox_memory where world_id = ?", worldId);
            jdbc.update("delete from sandbox_act where world_id = ?", worldId);
            jdbc.update("delete from sandbox_gift where world_id = ?", worldId);
            jdbc.update("delete from sandbox_shop_item where world_id = ?", worldId);
            jdbc.update("delete from sandbox_news where world_id = ?", worldId);
            jdbc.update("delete from sandbox_character where world_id = ?", worldId);
            jdbc.update("delete from sandbox_location where world_id = ?", worldId);
            jdbc.update("delete from sandbox_world where id = ?", worldId);
            report.append("\n临时世界（id=").append(worldId).append("）已删除。\n");
        } catch (Exception e) {
            report.append("\n清理失败：").append(e.getMessage()).append("\n");
        }
    }

    private int apiCalls() {
        Integer count = jdbc.queryForObject("select count(*) from admin_api_log", Integer.class);
        return count == null ? 0 : count;
    }

    private Object value(String statusJson, String key) {
        if (statusJson == null || statusJson.trim().isEmpty()) {
            return "—";
        }
        try {
            Object value = cn.hutool.json.JSONUtil.parseObj(statusJson).get(key);
            return value == null ? "—" : value;
        } catch (Exception e) {
            return "—";
        }
    }

    private String injuryOf(String statusJson) {
        return String.valueOf(value(statusJson, "伤势"));
    }

    // ============================== 性能：一次行动要等多久 ==============================

    /**
     * 核心指标：在当前提示词体量下，点一次「立即执行」平均要等多久。
     * 统计的是 service.runOnce 的**端到端耗时**（含 1~3 次模型调用与落库），
     * 因为那正是"后台点一下、前台等一次"的真实体验。
     */
    private void performanceSection() {
        report.append("\n### 一次行动的响应时间（含模型调用）\n\n");
        if (stepMillis.isEmpty()) {
            report.append("- （没有样本）\n");
            return;
        }
        List<Long> sorted = new ArrayList<>(stepMillis);
        Collections.sort(sorted);
        long total = 0;
        for (Long ms : stepMillis) {
            total += ms;
        }
        int callTotal = 0;
        for (Integer c : stepRequests) {
            callTotal += c;
        }
        long p50 = sorted.get(sorted.size() / 2);
        long p90 = sorted.get(Math.max(0, Math.min(sorted.size() - 1,
                (int) Math.round(sorted.size() * 0.9) - 1)));
        report.append("- 样本：").append(stepMillis.size()).append(" 步；模型请求合计 ")
                .append(requests).append(" 次，平均每步 ")
                .append(String.format("%.2f", stepRequests.isEmpty() ? 0 : callTotal * 1.0 / stepRequests.size()))
                .append(" 次\n")
                .append("- **平均 ").append(String.format("%.1f", total * 1.0 / stepMillis.size() / 1000))
                .append(" 秒**（中位 ").append(String.format("%.1f", p50 / 1000.0))
                .append(" 秒，P90 ").append(String.format("%.1f", p90 / 1000.0))
                .append(" 秒，最快 ").append(String.format("%.1f", sorted.get(0) / 1000.0))
                .append(" 秒，最慢 ").append(String.format("%.1f", sorted.get(sorted.size() - 1) / 1000.0))
                .append(" 秒）\n")
                .append("- 整轮总耗时 ").append(String.format("%.1f", total / 60000.0)).append(" 分钟\n");
        report.append("- 开跑前的提示词体量（系统+用户，字符）：\n");
        for (Map.Entry<Long, Integer> entry : promptChars.entrySet()) {
            report.append("  - ").append(names.get(entry.getKey())).append("：")
                    .append(entry.getValue() < 0 ? "（测量失败）"
                            : entry.getValue() + " 字符（中文大致 1 字 ≈ 0.6~1 token）")
                    .append('\n');
        }
        String limit = jdbc.queryForObject(
                "select config_value from sys_config where config_key = 'sandbox_prompt_char_limit'", String.class);
        report.append("- 提示词上限 sandbox_prompt_char_limit = ").append(limit == null ? "（未配置）" : limit)
                .append("：超过这个数会把【今日要闻】【其他居民动静】去掉、最近行动只留 6 条，")
                .append("所以上面量到的是「完整体」的体量，实际发出去的可能是精简后的\n");
    }

    // ============================== 委托板流转 ==============================

    /** 角色之间的关系：好感度是否随着相处自然变化（同一对之间的双向数值都列出来） */
    private void relationSection(Long worldId) {
        report.append("\n### 角色关系（好感度）\n\n");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select c.name as from_name, t.name as to_name, r.favor, r.remark from sandbox_relation r"
                        + " join sandbox_character c on c.id = r.character_id"
                        + " join sandbox_character t on t.id = r.target_id"
                        + " where r.world_id = ? order by r.character_id, r.target_id", worldId);
        if (rows.isEmpty()) {
            report.append("- （没有产生任何好感度记录：这几天两人可能一直没碰上）\n");
            return;
        }
        for (Map<String, Object> row : rows) {
            report.append("- ").append(row.get("from_name")).append(" → ").append(row.get("to_name"))
                    .append("：好感 ").append(row.get("favor"))
                    .append(row.get("remark") == null ? "" : "（" + row.get("remark") + "）").append('\n');
        }
    }

    /** 每日记忆总结的结果：有没有生成、写得像不像"回忆"、有没有进到第二天的提示词里 */
    private void memorySection(Long worldId) {
        if (summarizedDays.isEmpty()) {
            return;
        }
        report.append("\n### 每日记忆总结\n\n");
        long total = 0;
        for (Long ms : memoryMillis) {
            total += ms;
        }
        report.append("- 跨天总结 ").append(summarizedDays.size()).append(" 次")
                .append(memoryMillis.isEmpty() ? "" : "，平均每次 "
                        + String.format("%.1f", total * 1.0 / memoryMillis.size() / 1000) + " 秒")
                .append("（记忆总结也走 AI，但不算在「一次行动」的响应时间里）\n");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select c.name, m.memory_date, m.summary, m.from_ai from sandbox_memory m"
                        + " join sandbox_character c on c.id = m.character_id"
                        + " where m.world_id = ? order by m.memory_date, m.id", worldId);
        report.append("- 生成记忆 ").append(rows.size()).append(" 条\n");
        for (Map<String, Object> row : rows) {
            String summary = String.valueOf(row.get("summary"));
            report.append("\n  - ").append(row.get("name")).append(" · ").append(row.get("memory_date"))
                    .append(((Number) row.get("from_ai")).intValue() == 1 ? "（AI 生成）" : "（兜底拼接）")
                    .append("，").append(summary.length()).append(" 字\n")
                    .append("    ").append(summary.replace("\n", " ")).append('\n');
        }
    }

    private void questSection(Long worldId) {
        if (questIds.isEmpty()) {
            return;
        }
        report.append("\n### 委托板流转\n\n");
        int taken = 0;
        int completed = 0;
        int abandoned = 0;
        for (Long id : questIds) {
            Map<String, Object> row = jdbc.queryForMap("select title, status, progress, taker_name, progress_note,"
                    + " completion_note, abandoned_by, reward_coins from sandbox_quest where id = ?", id);
            if (row.get("taker_name") != null) {
                taken++;
            }
            if ("completed".equals(row.get("status"))) {
                completed++;
            }
            if (row.get("abandoned_by") != null) {
                abandoned++;
            }
            report.append("- 「").append(row.get("title")).append("」").append(row.get("status"))
                    .append("，进度 ").append(row.get("progress")).append("%")
                    .append(row.get("taker_name") == null ? "" : "，接取人 " + row.get("taker_name"))
                    .append("，报酬 ").append(row.get("reward_coins")).append(" 金币\n")
                    .append(row.get("progress_note") == null ? "" : "  - 末次判断：" + row.get("progress_note") + "\n")
                    .append(row.get("completion_note") == null ? "" : "  - 完成经过：" + row.get("completion_note") + "\n")
                    .append(row.get("abandoned_by") == null ? "" : "  - 曾被放弃：" + row.get("abandoned_by") + "\n");
        }
        report.append("- 被接取过 ").append(taken).append(" 条，完成 ").append(completed)
                .append(" 条，被放弃过 ").append(abandoned).append(" 条\n");
        List<Map<String, Object>> questActs = jdbc.queryForList(
                "select quest_event from sandbox_act where world_id = ? and quest_event is not null order by id",
                worldId);
        report.append("- 与委托相关的行动 ").append(questActs.size()).append(" 条：\n");
        for (Map<String, Object> row : questActs) {
            report.append("  - ").append(row.get("quest_event")).append('\n');
        }
    }

    // ============================== 委托板 ==============================

    /**
     * 往临时世界里摆几条委托，用来观察"角色会不会频繁接委托、会不会一直赶路"这类频率问题。
     * 做成 admin 来源：刷新委托板时不会被自动换下（这个工具也不刷新板面）。
     */
    private void seedQuests(Long worldId) {
        Object[][] rows = {
                {"整理协会的委托卷宗", "chore", 1, "冒险家协会", "把协会堆在地上的委托卷宗按年份整理归档",
                        12, null, 1},
                {"清除晨雾森林的影狼", "hunt", 3, "晨雾森林", "清除 3 只影狼并带回狼牙作为凭证",
                        45, "[{\"name\":\"治疗药水\",\"rarity\":3,\"quantity\":2,\"description\":\"淡绿色的低阶恢复药\"}]", 0},
                {"替药铺采三份银叶草", "gather", 2, "晨雾森林", "采集 3 份银叶草交到药铺",
                        18, "[{\"name\":\"银叶草\",\"rarity\":1,\"quantity\":3,\"description\":\"常见的止血草药\"}]", 0},
                {"护送商队穿过魔物森林", "escort", 4, "魔物森林", "护送商队安全穿过魔物森林并把货送到对面驿站",
                        80, "[{\"name\":\"精钢匕首\",\"rarity\":4,\"quantity\":1,\"description\":\"削铁如泥的短刃\"}]", 0},
                {"勘测天空之城外围的回廊", "explore", 2, "天空之城", "勘测回廊结构并绘出简易图样",
                        35, "[{\"name\":\"星辉碎片\",\"rarity\":3,\"quantity\":1,\"description\":\"带着微光的遗迹碎片\"}]", 0}
        };
        for (Object[] row : rows) {
            jdbc.update("insert into sandbox_quest (world_id, batch_time, title, description, quest_type, difficulty,"
                            + " location_name, target, power, reward_coins, reward_items, progress, status, source,"
                            + " pinned, enabled, create_time) values (?, now(), ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'open',"
                            + " 'admin', ?, 1, now())",
                    worldId, row[0], "（模拟委托）" + row[4], row[1], row[2], row[3], row[4],
                    "hunt".equals(row[1]) || "escort".equals(row[1]) ? 22 : null,
                    row[5], row[6], row[7]);
            Long id = jdbc.queryForObject("select max(id) from sandbox_quest", Long.class);
            questIds.add(id);
        }
    }

    /**
     * 互动保险自检（零模型成本）：把两个角色摆到两个「离得近但不同」的一级地点上，
     * 看服务端还认不认他们是「附近的人」。
     *
     * 真实地图上「黑潮城」与「冒险家协会」的中心相距约 28km，小于互动上限 30km。
     * 旧规则只看距离，会让他们互相看见、甚至跨地区互动（用户实测报过这个问题）；
     * 新规则要求同一个一级地点，这里必须返回 0 个。
     */
    private void selfCheckSocialGuard(Long worldId) {
        try {
            int[] a = locationCenter(worldId, "黑潮城");
            int[] b = locationCenter(worldId, "冒险家协会");
            if (a == null || b == null) {
                report.append("- 互动保险自检：地图里没有这两个地点，跳过\n");
                return;
            }
            SandboxCharacter one = guardProbe(900001L, "黑潮城", a);
            SandboxCharacter two = guardProbe(900002L, "冒险家协会", b);
            int nearby = ((List<?>) call("nearbyCompanions",
                    new Class[]{SandboxCharacter.class, List.class, int.class, int.class, String.class},
                    one, java.util.Collections.singletonList(two), a[0], a[1], "黑潮城")).size();
            report.append("- 互动保险自检：两个角色分别在「黑潮城」与「冒险家协会」，")
                    .append("相距约 ").append(Math.round(kmBetween(a, b))).append(" km")
                    .append("（互动上限 ").append(intProp("sandbox.scene.socialMaxKm", 30))
                    .append(" km）→ 判定可互动 ").append(nearby).append(" 个（期望 0）\n");
            if (nearby != 0) {
                report.append("  ⚠️ 跨一级地点仍然能互动，请检查 sandbox_social_same_area_only 配置\n");
            }
        } catch (Exception e) {
            report.append("- 互动保险自检执行失败：").append(e.getMessage()).append('\n');
        }
    }

    /** 某个地点区域的中心坐标（取左上角 + 宽高的一半） */
    private int[] locationCenter(Long worldId, String name) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select x, y, width, height from sandbox_location where world_id = ? and name = ?",
                worldId, name);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        int x = ((Number) row.get("x")).intValue() + ((Number) row.get("width")).intValue() / 2;
        int y = ((Number) row.get("y")).intValue() + ((Number) row.get("height")).intValue() / 2;
        return new int[]{x, y};
    }

    /** 造一个只用于自检的角色对象（不落库，id 用不会和真实角色冲突的大数） */
    private SandboxCharacter guardProbe(Long id, String place, int[] point) {
        SandboxCharacter c = new SandboxCharacter();
        c.setId(id);
        c.setName("__自检__" + place);
        c.setLocationName(place);
        c.setSubLocation("自检点");
        c.setX(point[0]);
        c.setY(point[1]);
        return c;
    }

    /** 两点距离（km），与服务的换算一致：地图宽默认 200km 对应 100 个坐标单位 */
    private double kmBetween(int[] a, int[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]) * (intProp("sandbox.scene.mapWidthKm", 200) / 100.0);
    }

    /**
     * 跨天时补一次「每日记忆总结」（真实世界里是每天定时跑的那一步）。
     *
     * 为什么必须补：这一趟是"两天"的模拟，如果只跑行动不跑总结，
     * 第二天行动时提示词里的【最近的记忆】就是空的——等于把记忆链路整段漏测了。
     */
    private void summarizeCrossedDays(LocalDateTime now, Long worldId) {
        LocalDate ended = now.toLocalDate().minusDays(1);
        if (ended.isBefore(start.toLocalDate()) || summarizedDays.contains(ended)) {
            return;
        }
        summarizedDays.add(ended);
        System.setProperty(OFFSET_KEY, String.valueOf(Duration.between(LocalDateTime.now(), now).toMinutes()));
        long t0 = System.nanoTime();
        String error = null;
        try {
            service.summarizeOn(ended.toString());
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        long millis = (System.nanoTime() - t0) / 1_000_000L;
        memoryMillis.add(millis);
        report.append("\n> **【跨天】生成 ").append(ended).append(" 的每日记忆总结**");
        if (error != null) {
            report.append("（失败：").append(error).append("）\n");
            return;
        }
        report.append("（耗时 ").append(millis).append(" ms）\n");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select c.name, m.summary from sandbox_memory m join sandbox_character c on c.id = m.character_id"
                        + " where m.world_id = ? and m.memory_date = ? order by m.id", worldId, ended.toString());
        for (Map<String, Object> row : rows) {
            report.append("\n  回忆（").append(row.get("name")).append("）：")
                    .append(String.valueOf(row.get("summary")).replace("\n", " ")).append('\n');
        }
        if (rows.isEmpty()) {
            report.append("  （这一天没有产生记忆：可能两人当天都没有行动）\n");
        }
    }

    /** 每条行动后面附一行委托状态，方便一眼看出"谁在做什么委托、进度多少" */
    private String questStateLine() {
        if (questIds.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Long id : questIds) {
            Map<String, Object> row = jdbc.queryForMap(
                    "select title, status, progress, taker_name from sandbox_quest where id = ?", id);
            parts.add(row.get("title") + "=" + row.get("status") + "/" + row.get("progress") + "%"
                    + (row.get("taker_name") == null ? "" : "（" + row.get("taker_name") + "）"));
        }
        return String.join("，", parts);
    }

    /**
     * 开跑前把"这一步会发给模型的系统提示词+用户提示词"拼一遍，只为了量一下字符数——
     * 用户说的"当前 token 量"就是它（中文大致 1 字 ≈ 0.6~1 token）。
     */
    @SuppressWarnings("unchecked")
    private int promptChars(Long characterId) {
        try {
            SandboxCharacter c = characterMapper.selectById(characterId);
            Object world = call("world", new Class[]{Long.class}, c.getWorldId());
            List<?> locations = (List<?>) call("locations", new Class[]{Long.class}, c.getWorldId());
            List<SandboxCharacter> companions = (List<SandboxCharacter>) call("otherCharacters",
                    new Class[]{Long.class}, characterId);
            List<?> recent = (List<?>) call("recentActs", new Class[]{Long.class, int.class}, characterId, 12);
            List<?> whispers = (List<?>) call("recentWhispers",
                    new Class[]{Long.class, LocalDateTime.class}, characterId, c.getLastRunTime());
            List<?> companionActs = (List<?>) call("neighborActs", new Class[]{List.class}, companions);
            List<?> memories = (List<?>) call("recentMemories", new Class[]{Long.class, int.class}, characterId, 5);
            List<?> backpack = (List<?>) call("items", new Class[]{Long.class}, characterId);
            List<?> news = (List<?>) call("todayNews", new Class[]{Long.class}, c.getWorldId());
            String sys = (String) call("buildSystemPrompt",
                    new Class[]{SandboxCharacter.class, com.bc.bcblog.entity.SandboxWorld.class, List.class, List.class},
                    c, world, locations, companions);
            String user = (String) call("buildUserPrompt",
                    new Class[]{SandboxCharacter.class, List.class, List.class, List.class, List.class,
                            boolean.class, String.class, SandboxAct.class, List.class, List.class, List.class,
                            String.class, Integer.class},
                    c, recent, whispers, companions, companionActs, false, null, null,
                    memories, backpack, news, null, null);
            return sys.length() + user.length();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 反射调用服务里的私有方法（提示词组装用；服务和工具都在同一个包路径下） */
    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method method = SandboxServiceImpl.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        return method.invoke(target == null ? service : target, args);
    }

    private String safe(String text) {
        return text == null || text.trim().isEmpty() ? "—" : text.trim();
    }

    /** 这次行动的原始回复里出现了哪些分段标记（体检五段式有没有真的按顺序跑） */
    private String stageMarks(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "（没有原始回复）";
        }
        List<String> has = new ArrayList<>();
        for (String tag : new String[]{"recap", "think", "draft", "review", "final"}) {
            if (raw.contains("<" + tag + ">")) {
                has.add(tag);
            }
        }
        return has.isEmpty() ? "（无标记，可能是直接 JSON）" : String.join(" → ", has);
    }

    private int intProp(String key, int defaultValue) {
        try {
            return Integer.parseInt(System.getProperty(key, String.valueOf(defaultValue)).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean boolProp(String key, boolean defaultValue) {
        String value = System.getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private String strProp(String key, String defaultValue) {
        String value = System.getProperty(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
