package com.bc.bcblog.tools;

import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxQuest;
import com.bc.bcblog.entity.SandboxQuestReward;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxQuestMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旅人委托 x 角色行动：真实 AI 联调测试。
 *
 * 建一个临时世界（地图从正式世界复制，地点战力区间一致），放一个急着赚钱的角色，
 * 委托板上挂 5 条不同类型/距离的委托，让角色真刀真枪地行动十几步；
 * 中途模拟管理员的后台操作（改委托、改进度、下架、重新上板），
 * 用来验证「后台动过之后角色再行动会不会出问题」。
 *
 * 用法（PowerShell，参数要加引号）：
 *   mvn test "-Dtest=SandboxQuestRun"
 *   mvn test "-Dtest=SandboxQuestRun" "-Dsandbox.questRun.steps=16"
 *
 * 参数：
 *   sandbox.questRun.steps  跑多少步（默认 12，最多 24）
 *   sandbox.questRun.model  模型（默认公益站 flash）
 *   sandbox.questRun.keep   跑完保留临时世界（默认 false）
 *   sandbox.questRun.out    报告文件名（默认 quest-run.md）
 *
 * 报告：target/probe/quest-run.md（步骤明细 + 剧情全文 + 自动校验清单 + 汇总）
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxQuestRun {

    static {
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final Long SOURCE_WORLD = 2L;
    private static final Long PROVIDER_ID = 2L;
    private static final String DEFAULT_MODEL = "假流式-gemini-3-flash-preview";
    private static final String OFFSET_KEY = "bcblog.sandbox.time-offset-minutes";
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final String HERO = "__委托·伊芙__";
    private static final String START_PLACE = "冒险家协会";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private SandboxCharacterMapper characterMapper;
    @Autowired
    private SandboxQuestMapper questMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private final StringBuilder report = new StringBuilder();
    private final List<String> passes = new ArrayList<>();
    private final List<String> fails = new ArrayList<>();
    private final List<Map<String, Object>> snapshots = new ArrayList<>();
    private int requests = 0;
    private int stepErrors = 0;
    /** 第 5 步做「后台改委托」时的最大行动 id，用来验证改完之后角色还能继续正常行动 */
    private int actIdAtEdit = 0;
    private Long heroId;
    private Long worldId;

    @Test
    void run() throws Exception {
        int steps = Math.max(1, Math.min(24, intProp("sandbox.questRun.steps", 12)));
        String model = strProp("sandbox.questRun.model", DEFAULT_MODEL);
        boolean keep = boolProp("sandbox.questRun.keep", false);
        String out = strProp("sandbox.questRun.out", "quest-run.md");

        try {
            worldId = createWorld();
            heroId = createHero(model);
            Map<String, Long> quests = createQuests();

            LocalDateTime sim = LocalDate.now().atTime(8, 30);
            if (sim.isBefore(LocalDateTime.now())) {
                sim = sim.plusDays(1);
            }
            header(steps, model);

            boolean nudged = false;
            for (int step = 1; step <= steps; step++) {
                sim = sim.plusMinutes(50);
                System.setProperty(OFFSET_KEY, String.valueOf(Duration.between(LocalDateTime.now(), sim).toMinutes()));
                adminActions(step, quests);
                if (!nudged && step == 3 && currentQuest() == null) {
                    SandboxCharacter c = characterMapper.selectById(heroId);
                    c.setGoal("委托板上有活儿，先接一条今天能做完的");
                    characterMapper.updateById(c);
                    nudged = true;
                    report.append("\n> （第 ").append(step)
                            .append(" 步前提示：角色还没接委托，把 TA 的「当前目标」写得更明确了）\n");
                }
                runStep(step, sim);
                snapshots.add(questSnapshot());
            }
            verify();
        } finally {
            System.clearProperty(OFFSET_KEY);
            if (worldId != null && !keep) {
                cleanup(worldId);
            } else if (worldId != null) {
                report.append("\n（按 keep=true 保留了临时世界 id=").append(worldId).append("）\n");
            }
            writeReport(out);
        }
    }

    // ============================== 跑一步 ==============================

    private void runStep(int step, LocalDateTime sim) {
        Integer maxActId = jdbc.queryForObject(
                "select ifnull(max(id), 0) from sandbox_act where world_id = ?", Integer.class, worldId);
        int callsBefore = apiCalls();
        SandboxCharacter before = characterMapper.selectById(heroId);
        String error = null;
        try {
            service.runOnce(heroId, true);
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        requests += apiCalls() - callsBefore;
        if (error != null) {
            stepErrors++;
        }

        List<Map<String, Object>> acts = jdbc.queryForList(
                "select id, location_name, sub_location, next_after_minutes, next_after_reason, quest_event,"
                        + " coin_change, item_change, luck, summary, actions, inner_voice"
                        + " from sandbox_act where world_id = ? and id > ? order by id",
                worldId, maxActId);
        SandboxCharacter after = characterMapper.selectById(heroId);
        report.append("\n### 第 ").append(step).append(" 步 · ").append(sim.format(HM)).append('\n');
        if (error != null) {
            report.append("\n> 行动失败：").append(error).append('\n');
            return;
        }
        for (Map<String, Object> act : acts) {
            report.append("\n- 地点：").append(safe(act.get("location_name"))).append(" · ")
                    .append(safe(act.get("sub_location")))
                    .append("  |  时长 ").append(act.get("next_after_minutes")).append(" 分钟（")
                    .append(safe(act.get("next_after_reason"))).append("）\n")
                    .append("- 摘要：").append(safe(act.get("summary"))).append('\n');
            if (act.get("quest_event") != null) {
                report.append("- **委托事件：").append(act.get("quest_event")).append("**\n");
            }
            report.append("- 金币 ").append(act.get("coin_change"))
                    .append("  |  运气 ").append(act.get("luck"))
                    .append(act.get("item_change") == null ? "" : "  |  物品：" + act.get("item_change"))
                    .append('\n');
            String actions = String.valueOf(act.get("actions") == null ? "" : act.get("actions"));
            for (String line : actions.split("\\r?\\n")) {
                if (!line.trim().isEmpty()) {
                    report.append("  > ").append(line.trim()).append('\n');
                }
            }
            String voice = safe(act.get("inner_voice"));
            if (!"—".equals(voice)) {
                report.append("  「").append(voice).append("」\n");
            }
        }
        report.append("\n- 这步之后：金币 ").append(coins(heroId))
                .append("  |  背包 ").append(backpackText()).append('\n')
                .append("- 委托：").append(questLine()).append('\n')
                .append("- 状态：").append(after == null ? "—" : after.getStatusJson())
                .append("  |  想法：实力「").append(after == null ? "—" : safe(after.getPowerView()))
                .append("」财富「").append(after == null ? "—" : safe(after.getWealthView())).append("」\n");
        if (before != null && after != null
                && before.getCombatPower() != null && after.getCombatPower() != null
                && !before.getCombatPower().equals(after.getCombatPower())) {
            report.append("- 战斗力：").append(before.getCombatPower()).append(" → ").append(after.getCombatPower()).append('\n');
        }
    }

    // ============================== 管理员操作 ==============================

    /**
     * 在指定步之前做一次后台操作，模拟「玩家在后台动了委托，角色随后继续行动」。
     * 每一步之后角色都必须照常跑完、不报错，所以这里也顺便验证了后台改动不会把行动搞崩。
     */
    private void adminActions(int step, Map<String, Long> quests) {
        if (step == 2) {
            service.expireQuest(quests.get("explore"));
            SandboxQuest q = questMapper.selectById(quests.get("explore"));
            // 这条只能在"当下"断言：第 7 步会把它重新上板
            check("后台下架「" + q.getTitle() + "」后它立刻不在板上", !onBoardFlag(q.getTitle()));
            report.append("\n> **【后台操作】下架了「").append(q.getTitle()).append("」**\n");
        } else if (step == 3) {
            actIdAtEdit = maxActId();
            SandboxQuest cur = currentQuest();
            SandboxQuest target = cur != null ? cur : questMapper.selectById(quests.get("hunt"));
            if (cur != null) {
                target.setTitle("【临时加急】" + target.getTitle());
            }
            target.setTarget("（协会临时追加要求）完成后把凭证一并交回协会");
            target.setRewardCoins((target.getRewardCoins() == null ? 0 : target.getRewardCoins()) + 5);
            target.setDescription("【协会临时追加】委托人加急，报酬上浮 5 金币，完成后需交回凭证。");
            service.saveQuest(target);
            report.append("\n> **【后台操作】改了").append(cur != null ? "角色手上那条" : "板面上的")
                    .append("委托「").append(target.getTitle()).append("」**：目标与描述被改写、报酬 +5\n");
        } else if (step == 4) {
            SandboxQuest cur = currentQuest();
            if (cur != null) {
                service.setQuestProgress(cur.getId(), 80, "【管理员】手工推进到八成");
                report.append("\n> **【后台操作】把「").append(cur.getTitle()).append("」的进度手工推到 80%**\n");
            } else {
                report.append("\n> （第 4 步还没有进行中的委托，跳过「改进度」）\n");
            }
        } else if (step == 7) {
            SandboxQuest q = questMapper.selectById(quests.get("explore"));
            service.resetQuest(q.getId());
            check("后台「重新上板」后「" + q.getTitle() + "」回到板上（且没有把别人挤下去）",
                    onBoardFlag(q.getTitle()) && onBoardFlag("整理协会的委托卷宗"));
            report.append("\n> **【后台操作】把「").append(q.getTitle()).append("」重新上板**\n");
        }
    }

    // ============================== 校验 ==============================

    private void verify() {
        report.append("\n\n## 自动校验\n");
        check("全程行动失败 0 次（实际 " + stepErrors + " 次）", stepErrors == 0);

        int maxTaken = 0;
        boolean monotonic = true;
        Map<Long, Integer> last = new LinkedHashMap<>();
        for (Map<String, Object> snap : snapshots) {
            maxTaken = Math.max(maxTaken, ((Number) snap.get("taken")).intValue());
            @SuppressWarnings("unchecked")
            Map<Long, Integer> progresses = (Map<Long, Integer>) snap.get("progress");
            for (Map.Entry<Long, Integer> entry : progresses.entrySet()) {
                Integer old = last.get(entry.getKey());
                if (old != null && entry.getValue() < old) {
                    monotonic = false;
                }
                last.put(entry.getKey(), entry.getValue());
            }
        }
        check("任何一步「接取中」的委托都不超过 1 条（一人一委托）", maxTaken <= 1);
        check("所有委托的进度全程只增不减", monotonic);
        check("临时世界里只有 1 个角色（不会串到正式世界的角色）", characterCount() == 1);

        int takenEver = jdbc.queryForObject("select count(*) from sandbox_quest"
                + " where world_id = ? and taker_id is not null", Integer.class, worldId);
        int progressed = jdbc.queryForObject("select count(*) from sandbox_quest"
                + " where world_id = ? and (progress > 0 or progress_note is not null)", Integer.class, worldId);
        int completed = jdbc.queryForObject("select count(*) from sandbox_quest"
                + " where world_id = ? and status = 'completed'", Integer.class, worldId);
        int abandoned = jdbc.queryForObject("select count(*) from sandbox_quest"
                + " where world_id = ? and abandoned_by is not null", Integer.class, worldId);
        check("角色接取过委托（接取过的委托数 " + takenEver + "）", takenEver > 0);
        check("有委托被推进过（进度或进度说明非空 " + progressed + " 条）", progressed > 0);
        report.append("\n- 已完成的委托：").append(completed).append(" 条；被放弃过的：")
                .append(abandoned).append(" 条\n");

        // 后台改动之后，角色又成功行动过（说明"后台改委托"没有把行动搞崩）
        int afterEdit = jdbc.queryForObject("select count(*) from sandbox_act where world_id = ? and id > ?",
                Integer.class, worldId, actIdAtEdit);
        check("后台改过委托之后，角色又成功行动了 " + afterEdit + " 步", afterEdit > 0);

        rewardCheck();
        attitudeCheck();
    }

    private int maxActId() {
        Integer id = jdbc.queryForObject("select ifnull(max(id),0) from sandbox_act where world_id = ?",
                Integer.class, worldId);
        return id == null ? 0 : id;
    }

    private boolean onBoardFlag(String title) {
        return service.questsForAdmin(worldId, "all", "all").stream()
                .filter(q -> title.equals(q.getTitle()))
                .findFirst()
                .map(q -> Boolean.TRUE.equals(q.getOnBoard()))
                .orElse(false);
    }

    private int characterCount() {
        Integer n = jdbc.queryForObject("select count(*) from sandbox_character where world_id = ?",
                Integer.class, worldId);
        return n == null ? 0 : n;
    }

    /** 完成奖励核对：金币到账、物品进背包（品质一致）、完成经过写了 */
    private void rewardCheck() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, title, taker_name, reward_coins, reward_items, progress, completion_note, completed_at"
                        + " from sandbox_quest where world_id = ? and status = 'completed'", worldId);
        report.append("\n### 完成结算核对\n");
        if (rows.isEmpty()) {
            report.append("\n- 这一趟没有委托被完成（步数不够、或角色中途放弃/改做别的）。"
                    + "想覆盖完成链路可以把步数加大再跑一次：`-Dsandbox.questRun.steps=20`\n");
            fails.add("- ❌ 没有任何委托被完成，未覆盖奖励结算链路");
            return;
        }
        for (Map<String, Object> row : rows) {
            String title = String.valueOf(row.get("title"));
            int reward = row.get("reward_coins") == null ? 0 : ((Number) row.get("reward_coins")).intValue();
            Integer paid = jdbc.queryForObject("select ifnull(sum(coins),0) from sandbox_coin_log"
                            + " where character_id = ? and type = 'quest' and remark like ?",
                    Integer.class, heroId, "%" + title + "%");
            int paidCoins = paid == null ? 0 : paid;
            report.append("\n**「").append(title).append("」**\n\n")
                    .append("- 完成时间：").append(row.get("completed_at"))
                    .append("　接取人：").append(row.get("taker_name"))
                    .append("　进度：").append(row.get("progress")).append("%\n")
                    .append("- 完成经过：").append(safe(row.get("completion_note"))).append('\n')
                    .append("- 奖励金币 ").append(reward).append("，金币流水记入 ").append(paidCoins).append('\n');
            check("「" + title + "」奖励金币到账（流水 " + paidCoins + " / 应有 " + reward + "）", paidCoins == reward);
            for (SandboxQuestReward r : parseRewards(String.valueOf(row.get("reward_items")))) {
                List<Map<String, Object>> items = jdbc.queryForList(
                        "select name, quantity, rarity from sandbox_item where character_id = ? and name = ?",
                        heroId, r.getName());
                if (items.isEmpty()) {
                    check("奖励物品「" + r.getName() + "」进了背包", false);
                } else {
                    int rarity = items.get(0).get("rarity") == null ? 0 : ((Number) items.get(0).get("rarity")).intValue();
                    int expected = r.getRarity() == null ? 1 : r.getRarity();
                    check("奖励物品「" + r.getName() + "」进了背包且品质一致（" + rarity + "/" + expected + "）",
                            rarity == expected);
                }
            }
        }
    }

    /** 心态：委托/受伤这类经历有没有改变角色对实力、财富的看法 */
    private void attitudeCheck() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select kind, old_view, new_view, reason, major, create_time from sandbox_attitude_log"
                        + " where world_id = ? order by id", worldId);
        report.append("\n### 想法变化（心态）\n");
        if (rows.isEmpty()) {
            report.append("\n- 这一趟没有产生想法变化。不算错（态度变化有 12 小时冷却、且要有够分量的经历），"
                    + "但如果完成过委托仍毫无变化，值得再看一眼提示词里的态度触发条件。\n");
            return;
        }
        for (Map<String, Object> row : rows) {
            report.append("\n- ").append("power".equals(row.get("kind")) ? "实力" : "财富")
                    .append("：「").append(row.get("old_view")).append("」→「").append(row.get("new_view"))
                    .append("」\n  - 原因：").append(row.get("reason"))
                    .append(((Number) row.get("major")).intValue() == 1 ? "（重大经历）" : "")
                    .append("　").append(row.get("create_time")).append('\n');
        }
        SandboxCharacter c = characterMapper.selectById(heroId);
        check("想法变化已记入流水，且角色身上的当前看法同步更新",
                c != null && (c.getPowerView() != null || c.getWealthView() != null));
    }

    // ============================== 脚手架 ==============================

    private void header(int steps, String model) {
        report.append("# 旅人委托 x 角色行动：联调测试\n\n")
                .append("- 临时世界 id=").append(worldId).append("（跑完即删，正式数据不受影响）\n")
                .append("- 角色：").append(HERO).append("（").append(START_PLACE).append(" 起步，金币 12）\n")
                .append("- 模型：").append(model).append("（公益站）\n")
                .append("- 步数：").append(steps).append(" 步\n")
                .append("- 委托板：\n");
        for (SandboxQuest q : allQuests()) {
            report.append("  - [").append(q.getQuestType()).append(" · 难度 ").append(q.getDifficulty())
                    .append("] ").append(q.getTitle())
                    .append("（").append(safe(q.getLocationName())).append("）：").append(safe(q.getTarget()))
                    .append("　报酬 ").append(q.getRewardCoins()).append(" 金币")
                    .append(rewardsText(q)).append('\n');
        }
    }

    private Long createWorld() {
        jdbc.update("insert into sandbox_world (name, description, map_image, world_prompt, enabled,"
                + " portal_visible, create_time, update_time)"
                + " select ?, description, map_image, world_prompt, 0, 0, now(), now()"
                + " from sandbox_world where id = ?", "__委托测试世界__", SOURCE_WORLD);
        Long id = jdbc.queryForObject("select max(id) from sandbox_world", Long.class);
        jdbc.update("insert into sandbox_location (world_id, name, icon, x, y, width, height, polygon,"
                + " description, danger_level, power_min, power_max, sort_order, create_time)"
                + " select ?, name, icon, x, y, width, height, polygon, description, danger_level,"
                + " power_min, power_max, sort_order, now() from sandbox_location where world_id = ?",
                id, SOURCE_WORLD);
        jdbc.update("insert into sandbox_news (world_id, title, content, location_name, x, y, level, source,"
                + " news_date, pinned, enabled, create_time) values (?, ?, ?, '冒险家协会', 40, 30, 1,"
                + " 'admin', curdate(), 1, 1, now())",
                id, "冒险家协会贴出新一批委托，赏金比上周高了一成",
                "协会门口的委托板一夜之间贴满了新委托，来打听行情的人排到了街上。");
        return id;
    }

    private Long createHero(String model) {
        SandboxCharacter c = new SandboxCharacter();
        c.setWorldId(worldId);
        c.setName(HERO);
        c.setTitle("见习魔女");
        c.setAppearance("银白色及腰长发，淡紫罗兰色眼眸，斗篷洗得发白。");
        c.setPersona("身份：独自闯荡的见习魔女，魔力不高但肯下功夫。\n"
                + "处境：住店房费还差十几金币，兜里只剩 12 金币，必须尽快接到活儿赚钱。\n"
                + "性格：务实、话不多、有点怕危险，但为了钱会咬牙接活；答应了的事会做完。\n"
                + "习惯：接委托前先看清报酬与地点，路上会顺手采点能卖钱的东西。");
        c.setGoal("去冒险家协会的委托板接一条报酬够付房费的委托");
        c.setProviderId(PROVIDER_ID);
        c.setModel(model);
        c.setLocationName(START_PLACE);
        c.setSubLocation("门口的委托板前");
        // 坐标要落在「冒险家协会」这块区域里（20,67 起、12x11；这里是它的大致中心），
        // 否则 saveCharacter 会按坐标把一级地点改回别的地区，角色就得先赶路才能接活
        c.setX(26);
        c.setY(72);
        c.setCombatPower(12);
        c.setCoins(12);
        c.setPowerView("知道自己还弱，能不打架就不打");
        c.setWealthView("钱快见底了，得赶紧赚");
        c.setStatusJson("{\"体力\":92,\"魔力\":70,\"饥饿度\":30,\"心情\":\"有点着急\",\"伤势\":\"无恙\"}");
        c.setTemperature(new java.math.BigDecimal("0.9"));
        c.setIntervalMin(45);
        c.setIntervalMax(75);
        c.setEnabled(1);
        return service.saveCharacter(c).getId();
    }

    /** 5 条委托：从「同城随手能做的小活」到「要出远门的危险活」，覆盖不同类型与距离 */
    private Map<String, Long> createQuests() {
        Map<String, Long> ids = new LinkedHashMap<>();
        ids.put("chore", saveQuest("整理协会的委托卷宗", "chore", 1, START_PLACE,
                "把协会堆在地上的委托卷宗按年份整理归档", null, 12, "[]", 1));
        ids.put("hunt", saveQuest("清除晨雾森林的影狼", "hunt", 3, "晨雾森林",
                "清除 3 只影狼并带回狼牙作为凭证", 22, 45,
                "[{\"name\":\"治疗药水\",\"rarity\":3,\"quantity\":2,\"description\":\"淡绿色的低阶恢复药\"}]", 0));
        ids.put("gather", saveQuest("替药铺采三份银叶草", "gather", 2, "晨雾森林",
                "采集 3 份银叶草交到药铺", null, 18,
                "[{\"name\":\"银叶草\",\"rarity\":1,\"quantity\":3,\"description\":\"常见的止血草药\"}]", 0));
        ids.put("escort", saveQuest("护送商队穿过魔物森林", "escort", 4, "魔物森林",
                "护送商队安全穿过魔物森林并把货送到对面驿站", 40, 80,
                "[{\"name\":\"精钢匕首\",\"rarity\":4,\"quantity\":1,\"description\":\"削铁如泥的短刃\"}]", 0));
        ids.put("explore", saveQuest("勘测天空之城外围的回廊", "explore", 2, "天空之城",
                "勘测回廊结构并绘出简易图样", null, 35,
                "[{\"name\":\"星辉碎片\",\"rarity\":3,\"quantity\":1,\"description\":\"带着微光的遗迹碎片\"}]", 0));
        return ids;
    }

    private Long saveQuest(String title, String type, int difficulty, String place, String target,
                           Integer power, int coins, String items, int pinned) {
        SandboxQuest q = new SandboxQuest();
        q.setWorldId(worldId);
        q.setTitle(title);
        q.setQuestType(type);
        q.setDifficulty(difficulty);
        q.setLocationName(place);
        q.setTarget(target);
        q.setPower(power);
        q.setRewardCoins(coins);
        q.setRewardItems(items);
        q.setPinned(pinned);
        q.setSource("admin");
        q.setDescription("（测试委托）" + target + "。委托人：协会值班书记官。");
        return service.saveQuest(q).getId();
    }

    private List<SandboxQuest> allQuests() {
        return questMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SandboxQuest>()
                        .eq(SandboxQuest::getWorldId, worldId).orderByAsc(SandboxQuest::getId));
    }

    private Map<String, Object> questSnapshot() {
        Map<String, Object> snap = new LinkedHashMap<>();
        Map<Long, Integer> progresses = new LinkedHashMap<>();
        int taken = 0;
        for (SandboxQuest q : allQuests()) {
            progresses.put(q.getId(), q.getProgress() == null ? 0 : q.getProgress());
            if ("taken".equals(q.getStatus())) {
                taken++;
            }
        }
        snap.put("progress", progresses);
        snap.put("taken", taken);
        return snap;
    }

    private SandboxQuest currentQuest() {
        return questMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SandboxQuest>()
                .eq("world_id", worldId).eq("taker_id", heroId).eq("status", "taken").last("limit 1"));
    }

    private String questLine() {
        List<String> parts = new ArrayList<>();
        for (SandboxQuest q : allQuests()) {
            parts.add(q.getTitle() + "=" + q.getStatus() + "/" + (q.getProgress() == null ? 0 : q.getProgress()) + "%"
                    + (q.getTakerName() == null ? "" : "（" + q.getTakerName() + "）"));
        }
        return String.join("，", parts);
    }

    private int coins(Long characterId) {
        SandboxCharacter c = characterMapper.selectById(characterId);
        return c == null || c.getCoins() == null ? 0 : c.getCoins();
    }

    private String backpackText() {
        List<Map<String, Object>> items = jdbc.queryForList(
                "select name, quantity from sandbox_item where character_id = ? order by id", heroId);
        if (items.isEmpty()) {
            return "（空）";
        }
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> item : items) {
            parts.add(item.get("name") + "×" + item.get("quantity"));
        }
        return String.join("、", parts);
    }

    private String rewardsText(SandboxQuest q) {
        List<SandboxQuestReward> rewards = parseRewards(q.getRewardItems());
        if (rewards.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (SandboxQuestReward r : rewards) {
            parts.add(r.getName() + "×" + r.getQuantity() + "（品质 " + r.getRarity() + "）");
        }
        return "  + " + String.join("、", parts);
    }

    private List<SandboxQuestReward> parseRewards(String json) {
        List<SandboxQuestReward> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            for (Object element : cn.hutool.json.JSONUtil.parseArray(json)) {
                if (!(element instanceof cn.hutool.json.JSONObject)) {
                    continue;
                }
                cn.hutool.json.JSONObject obj = (cn.hutool.json.JSONObject) element;
                SandboxQuestReward r = new SandboxQuestReward();
                r.setName(obj.getStr("name"));
                r.setQuantity(cn.hutool.core.convert.Convert.toInt(obj.get("quantity"), 1));
                r.setRarity(cn.hutool.core.convert.Convert.toInt(obj.get("rarity"), 1));
                list.add(r);
            }
        } catch (Exception ignored) {
            // 解析失败不影响报告
        }
        return list;
    }

    private void check(String what, boolean ok) {
        if (ok) {
            passes.add("- ✅ " + what);
        } else {
            fails.add("- ❌ " + what);
        }
        report.append("\n> ").append(ok ? "[通过] " : "[失败] ").append(what).append('\n');
    }

    private int apiCalls() {
        Integer count = jdbc.queryForObject("select count(*) from admin_api_log", Integer.class);
        return count == null ? 0 : count;
    }

    private void writeReport(String out) throws Exception {
        Path dir = Paths.get("target", "probe");
        Files.createDirectories(dir);
        StringBuilder head = new StringBuilder("# 旅人委托 x 角色行动：测试结论\n\n")
                .append("通过 ").append(passes.size()).append(" 项，失败 ").append(fails.size())
                .append(" 项；AI 请求约 ").append(requests).append(" 次。\n\n");
        for (String line : fails) {
            head.append(line).append('\n');
        }
        for (String line : passes) {
            head.append(line).append('\n');
        }
        head.append("\n---\n").append(report);
        Path file = dir.resolve(out);
        Files.write(file, head.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("quest run report: " + file.toAbsolutePath());
    }

    private void cleanup(Long wid) {
        try {
            jdbc.update("delete from sandbox_quest where world_id = ?", wid);
            jdbc.update("delete from sandbox_coin_log where world_id = ?", wid);
            jdbc.update("delete from sandbox_item where world_id = ?", wid);
            jdbc.update("delete from sandbox_attitude_log where world_id = ?", wid);
            jdbc.update("delete from sandbox_memory where world_id = ?", wid);
            jdbc.update("delete from sandbox_act where world_id = ?", wid);
            jdbc.update("delete from sandbox_relation where world_id = ?", wid);
            jdbc.update("delete from sandbox_interaction where world_id = ?", wid);
            jdbc.update("delete from sandbox_gift where world_id = ?", wid);
            jdbc.update("delete from sandbox_news where world_id = ?", wid);
            jdbc.update("delete from sandbox_area_lock where world_id = ?", wid);
            jdbc.update("delete from sandbox_character where world_id = ?", wid);
            jdbc.update("delete from sandbox_location where world_id = ?", wid);
            jdbc.update("delete from sandbox_world where id = ?", wid);
            report.append("\n---\n\n临时世界 id=").append(wid).append(" 已删除（正式数据未受影响）。\n");
        } catch (Exception e) {
            report.append("\n清理失败：").append(e.getMessage()).append("\n");
        }
    }

    private String safe(Object text) {
        return text == null || String.valueOf(text).trim().isEmpty() ? "—" : String.valueOf(text).trim();
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
