package com.bc.bcblog.tools;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxQuest;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxQuestMapper;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 「完成校验没过 → 让 AI 自检修正」这条链路的**真实 AI** 验证（一次调用就够）。
 *
 * 构造的场景就是实测踩到的那次：角色在 A 地声称"委托完成、拿到报酬"，但委托要求的是 B 地，
 * 于是服务端拦下。要验证的是：拦下之后服务端会再调一次 AI，让它把"其实没完成"改回来，
 * 并且三条硬约束都守住了——不改地点、不写"已拿到报酬"、进度不高于修正前。
 *
 * 手动运行： mvn test "-Dtest=SandboxQuestSelfCheckCheck"
 * 报告：target/probe/quest-selfcheck.md
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxQuestSelfCheckCheck {

    static {
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final Long PROVIDER_ID = 2L;
    private static final String MODEL = "假流式-gemini-3-flash-preview";
    private static final String HERO = "__自检·伊芙__";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private SandboxCharacterMapper characterMapper;
    @Autowired
    private SandboxQuestMapper questMapper;
    @Autowired
    private JdbcTemplate jdbc;

    private final StringBuilder report = new StringBuilder();
    private int passed = 0;
    private int failed = 0;

    @Test
    void run() throws Exception {
        Long worldId = null;
        try {
            worldId = setupWorld();
            Long heroId = setupHero(worldId);
            Long questId = setupQuest(worldId, heroId);

            int callsBefore = apiCalls();
            SandboxAct act = new SandboxAct();
            act.setLocationName("风息镇");
            act.setSubLocation("镇口的告示牌前");
            act.setActions("把最后一箱货搬到驿站门口。\n和商队负责人交割，拿到报酬 80 金币。\n数完钱，心里踏实了。");
            act.setSummary("在风息镇完成护送交接拿到报酬");

            JSONObject obj = new JSONObject();
            obj.set("quest_progress", 100);
            obj.set("quest_note", "已经把货送到驿站，商队付了报酬");
            JSONArray actions = new JSONArray();
            actions.add("把最后一箱货搬到驿站门口。");
            actions.add("和商队负责人交割，拿到报酬 80 金币。");
            obj.set("actions", actions);
            obj.set("summary", "在风息镇完成护送交接拿到报酬");

            report.append("# 委托自检修正：真实 AI 验证\n\n")
                    .append("- 委托：护送商队穿过魔物森林（目标地区 **魔物森林**）\n")
                    .append("- 角色这一步所在：**风息镇**（不是目标地区，所以完成校验必然不通过）\n")
                    .append("- 角色自称：`").append(obj.getStr("quest_note")).append("`\n\n");

            invokeQuestActions(characterMapper.selectById(heroId), obj, "风息镇", act);
            int calls = apiCalls() - callsBefore;

            SandboxQuest after = questMapper.selectById(questId);
            report.append("## 结果\n\n")
                    .append("- 服务端这一趟花了 **").append(calls).append("** 次模型调用")
                    .append("（探针直接调用结算逻辑，省掉了\"行动本身\"那次调用，所以这里就是自检修正那一次）\n")
                    .append("- 委托状态：").append(after.getStatus()).append("，进度 ").append(after.getProgress()).append("%\n")
                    .append("- 进度说明：").append(after.getProgressNote()).append("\n\n")
                    .append("- 自检后这一步的动作：\n");
            for (String line : String.valueOf(act.getActions()).split("\\r?\\n")) {
                report.append("  > ").append(line).append('\n');
            }
            report.append("- 自检后的摘要：").append(act.getSummary()).append("\n");

            check("委托没有被误判为完成", "taken".equals(after.getStatus()));
            check("进度被压在 99% 以内", after.getProgress() != null && after.getProgress() <= 99);
            check("进度说明写清了还差什么（不是空话）",
                    after.getProgressNote() != null && !after.getProgressNote().trim().isEmpty());
            check("自检确实额外调了一次模型", calls >= 1);
            String text = String.valueOf(act.getActions()) + " " + String.valueOf(act.getSummary());
            check("自检后的叙述不再声称「已拿到报酬 / 已完成」",
                    !text.contains("拿到报酬") && !text.contains("完成交接") && !text.contains("拿到 80"));
            check("自检没有把角色挪到魔物森林（不许靠瞬移糊过校验）",
                    "风息镇".equals(act.getLocationName()));
            check("自检结果落到了行动记录上（动作被重写）",
                    act.getActions() != null && !act.getActions().isEmpty());
            check("审计日志里能看到这次自检调用", hasSelfCheckAudit());
        } finally {
            if (worldId != null) {
                cleanup(worldId);
            }
            Path dir = Paths.get("target", "probe");
            Files.createDirectories(dir);
            StringBuilder head = new StringBuilder("# 委托自检修正：结论\n\n")
                    .append("通过 ").append(passed).append(" 项，失败 ").append(failed).append(" 项。\n\n")
                    .append("\n").append(report);
            Path file = dir.resolve("quest-selfcheck.md");
            Files.write(file, head.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("selfcheck report: " + file.toAbsolutePath());
        }
    }

    private Long setupWorld() {
        jdbc.update("insert into sandbox_world (name, description, world_prompt, enabled, portal_visible,"
                + " create_time, update_time) values ('__自检探针世界__', '', '这是一个剑与魔法的幻想世界。',"
                + " 0, 0, now(), now())");
        Long id = jdbc.queryForObject("select max(id) from sandbox_world", Long.class);
        jdbc.update("insert into sandbox_location (world_id, name, x, y, width, height, danger_level,"
                        + " power_min, power_max, sort_order, description, create_time) values"
                        + " (?, '风息镇', 20, 20, 10, 10, 1, 6, 14, 0, '一座小镇。', now()),"
                        + " (?, '魔物森林', 70, 70, 14, 14, 3, 25, 55, 1, '危险的森林。', now())",
                id, id);
        return id;
    }

    private Long setupHero(Long worldId) {
        SandboxCharacter c = new SandboxCharacter();
        c.setWorldId(worldId);
        c.setName(HERO);
        c.setTitle("见习冒险者");
        c.setPersona("务实、话不多的见习冒险者，接了护送委托。");
        c.setAppearance("银白色长发，淡紫罗兰色眼眸。");
        c.setProviderId(PROVIDER_ID);
        c.setModel(MODEL);
        c.setLocationName("风息镇");
        c.setSubLocation("镇口的告示牌前");
        c.setX(22);
        c.setY(22);
        c.setCombatPower(14);
        c.setCoins(20);
        c.setStatusJson("{\"体力\":60,\"魔力\":50,\"饥饿度\":40,\"心情\":\"疲惫\",\"伤势\":\"无恙\"}");
        c.setTemperature(new java.math.BigDecimal("0.9"));
        c.setIntervalMin(45);
        c.setIntervalMax(75);
        c.setEnabled(1);
        return service.saveCharacter(c).getId();
    }

    private Long setupQuest(Long worldId, Long heroId) {
        SandboxQuest q = new SandboxQuest();
        q.setWorldId(worldId);
        q.setTitle("护送商队穿过魔物森林");
        q.setQuestType("escort");
        q.setDifficulty(3);
        q.setLocationName("魔物森林");
        q.setTarget("护送商队安全穿过魔物森林并把货送到对面驿站");
        q.setPower(30);
        q.setRewardCoins(80);
        q.setSource("admin");
        q.setDescription("（自检探针）商队要穿过魔物森林。");
        Long id = service.saveQuest(q).getId();
        // 直接置为"接取中、进度 90%"，省掉一次"接取"的模型调用
        jdbc.update("update sandbox_quest set status = 'taken', taker_id = ?, taker_name = ?,"
                + " taken_at = now(), progress = 90, progress_note = '货已经装好，准备出发' where id = ?",
                heroId, HERO, id);
        return id;
    }

    private void invokeQuestActions(SandboxCharacter hero, JSONObject obj, String locationName, SandboxAct act)
            throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod("applyQuestActions",
                SandboxCharacter.class, JSONObject.class, String.class, LocalDateTime.class,
                SandboxAct.class, boolean.class);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        method.invoke(target == null ? service : target, hero, obj, locationName, LocalDateTime.now(), act, true);
    }

    private boolean hasSelfCheckAudit() {
        Integer n = jdbc.queryForObject("select count(*) from admin_api_log where action like ?",
                Integer.class, "%委托自检修正%");
        return n != null && n > 0;
    }

    private int apiCalls() {
        Integer n = jdbc.queryForObject("select count(*) from admin_api_log", Integer.class);
        return n == null ? 0 : n;
    }

    private void check(String what, boolean ok) {
        if (ok) {
            passed++;
            report.append("\n> [通过] ").append(what).append('\n');
        } else {
            failed++;
            report.append("\n> [失败] ").append(what).append('\n');
        }
    }

    private void cleanup(Long worldId) {
        jdbc.update("delete from sandbox_quest where world_id = ?", worldId);
        jdbc.update("delete from sandbox_act where world_id = ?", worldId);
        jdbc.update("delete from sandbox_coin_log where world_id = ?", worldId);
        jdbc.update("delete from sandbox_item where world_id = ?", worldId);
        jdbc.update("delete from sandbox_attitude_log where world_id = ?", worldId);
        jdbc.update("delete from sandbox_memory where world_id = ?", worldId);
        jdbc.update("delete from sandbox_area_lock where world_id = ?", worldId);
        jdbc.update("delete from sandbox_character where world_id = ?", worldId);
        jdbc.update("delete from sandbox_location where world_id = ?", worldId);
        jdbc.update("delete from sandbox_world where id = ?", worldId);
    }
}
