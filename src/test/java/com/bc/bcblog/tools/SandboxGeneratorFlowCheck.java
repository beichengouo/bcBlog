package com.bc.bcblog.tools;

import com.bc.bcblog.dto.SandboxCharacterGenerateDTO;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import com.bc.bcblog.vo.SandboxCharacterDraftVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 四个生成器的**真实 AI** 验证：它们现在会不会参考地图地点的完整信息（描述、危险度、战力区间），
 * 以及三段式（think → draft → final）跑通没有。
 *
 * 手动运行： mvn test "-Dtest=SandboxGeneratorFlowCheck"
 * 报告：target/probe/generator-flow.md
 *
 * 成本：4~6 次模型调用（四个生成器各一次；掉格式时服务端不会再补救，因为生成器本来就没接补救流程）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxGeneratorFlowCheck {

    static {
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final Long SOURCE_WORLD = 2L;
    private static final Long PROVIDER_ID = 2L;
    private static final String MODEL = "假流式-gemini-3-flash-preview";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private JdbcTemplate jdbc;

    private final StringBuilder report = new StringBuilder();

    @Test
    void run() throws Exception {
        Long worldId = null;
        try {
            worldId = createWorld();
            final Long wid = worldId;
            report.append("# 四个生成器：地点信息 + 三段式 验证\n\n")
                    .append("- 临时世界 id=").append(worldId)
                    .append("（地图从正式世界复制，所以描述与危险度都是真实的）\n")
                    .append("- 模型 ").append(MODEL).append('\n')
                    .append("- 生成器三段式配置 sandbox_generator_flow = ")
                    .append(jdbc.queryForObject(
                            "select config_value from sys_config where config_key='sandbox_generator_flow'",
                            String.class)).append("\n\n");
            report.append("## 地图地点（生成器能看到的就是这些）\n\n");
            for (Map<String, Object> row : jdbc.queryForList(
                    "select name, danger_level, power_min, power_max, description from sandbox_location"
                            + " where world_id = ? order by id", worldId)) {
                report.append("- ").append(row.get("name"))
                        .append("（危险度 ").append(row.get("danger_level"))
                        .append("；战力 ").append(row.get("power_min")).append("~").append(row.get("power_max"))
                        .append("）：").append(row.get("description")).append('\n');
            }
            report.append('\n');

            int callsBefore = apiCalls();
            check("① AI 创作角色", () -> {
                SandboxCharacterGenerateDTO dto = new SandboxCharacterGenerateDTO();
                dto.setProviderId(PROVIDER_ID);
                dto.setModel(MODEL);
                dto.setRequirement("一个在危险地区讨生活的年轻猎人，穷但讲义气");
                SandboxCharacterDraftVO draft = service.generateCharacter(dto, wid);
                report.append("  - 姓名：").append(draft.getName()).append("｜称号：").append(draft.getTitle())
                        .append("｜初始地点：").append(draft.getLocationName())
                        .append("｜战斗力：").append(draft.getCombatPower()).append('\n')
                        .append("  - 人设：").append(brief(draft.getPersona())).append('\n');
            });
            check("② 旅人纪闻", () -> {
                int n = service.generateNews(2, PROVIDER_ID, MODEL, wid);
                report.append("  - 生成 ").append(n).append(" 条：\n");
                for (Map<String, Object> row : jdbc.queryForList(
                        "select title, location_name, content from sandbox_news where world_id = ? order by id",
                        wid)) {
                    report.append("    - 「").append(row.get("title")).append("」（")
                            .append(row.get("location_name")).append("）：")
                            .append(brief(String.valueOf(row.get("content")))).append('\n');
                }
            });
            check("③ 旅人集市", () -> {
                int n = service.generateShopItems(3, PROVIDER_ID, MODEL, wid);
                report.append("  - 生成 ").append(n).append(" 件：\n");
                for (Map<String, Object> row : jdbc.queryForList(
                        "select name, description, rarity, price from sandbox_shop_item where world_id = ? order by id",
                        wid)) {
                    report.append("    - 「").append(row.get("name")).append("」（品质 ").append(row.get("rarity"))
                            .append("，").append(row.get("price")).append(" 金币）：")
                            .append(brief(String.valueOf(row.get("description")))).append('\n');
                }
            });
            check("④ 旅人委托", () -> {
                int n = service.generateQuests(3, PROVIDER_ID, MODEL, wid);
                report.append("  - 生成 ").append(n).append(" 条：\n");
                for (Map<String, Object> row : jdbc.queryForList(
                        "select title, quest_type, difficulty, location_name, target, power, reward_coins, reward_items"
                                + " from sandbox_quest where world_id = ? order by id", wid)) {
                    report.append("    - 「").append(row.get("title")).append("」（").append(row.get("quest_type"))
                            .append(" · 难度 ").append(row.get("difficulty")).append(" · ")
                            .append(row.get("location_name")).append("）：").append(row.get("target"))
                            .append("｜战力 ").append(row.get("power")).append("｜").append(row.get("reward_coins"))
                            .append(" 金币｜奖励物品 ").append(row.get("reward_items")).append('\n');
                }
            });
            int calls = apiCalls() - callsBefore;
            report.append("\n## 结论\n\n")
                    .append("- 四个生成器合计 **").append(calls).append("** 次模型调用")
                    .append("（每个一次即说明三段式没有触发重试/补救）\n");
            if (calls > 4) {
                report.append("- ⚠️ 调用次数多于 4：可能有生成器掉格式后重试了，需要看日志\n");
            }
        } finally {
            if (worldId != null) {
                cleanup(worldId);
            }
            Path dir = Paths.get("target", "probe");
            Files.createDirectories(dir);
            Path file = dir.resolve("generator-flow.md");
            Files.write(file, report.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("generator flow report: " + file.toAbsolutePath());
        }
    }

    /** 跑一个生成器，失败时把异常写进报告（不让整轮中断） */
    private void check(String label, Runnable action) {
        report.append("## ").append(label).append("\n\n");
        try {
            action.run();
        } catch (Exception e) {
            report.append("  - ❌ 失败：")
                    .append(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()).append('\n');
        }
        report.append('\n');
    }

    private Long createWorld() {
        jdbc.update("insert into sandbox_world (name, description, map_image, world_prompt, enabled,"
                + " portal_visible, create_time, update_time)"
                + " select ?, description, map_image, world_prompt, 0, 0, now(), now()"
                + " from sandbox_world where id = ?", "__生成器验证世界__", SOURCE_WORLD);
        Long id = jdbc.queryForObject("select max(id) from sandbox_world", Long.class);
        jdbc.update("insert into sandbox_location (world_id, name, icon, x, y, width, height, polygon,"
                + " description, danger_level, power_min, power_max, sort_order, create_time)"
                + " select ?, name, icon, x, y, width, height, polygon, description, danger_level,"
                + " power_min, power_max, sort_order, now() from sandbox_location where world_id = ?",
                id, SOURCE_WORLD);
        return id;
    }

    private void cleanup(Long worldId) {
        jdbc.update("delete from sandbox_quest where world_id = ?", worldId);
        jdbc.update("delete from sandbox_shop_item where world_id = ?", worldId);
        jdbc.update("delete from sandbox_news where world_id = ?", worldId);
        jdbc.update("delete from sandbox_character where world_id = ?", worldId);
        jdbc.update("delete from sandbox_location where world_id = ?", worldId);
        jdbc.update("delete from sandbox_area_lock where world_id = ?", worldId);
        jdbc.update("delete from sandbox_world where id = ?", worldId);
    }

    private int apiCalls() {
        Integer n = jdbc.queryForObject("select count(*) from admin_api_log", Integer.class);
        return n == null ? 0 : n;
    }

    private String brief(String text) {
        if (text == null) {
            return "—";
        }
        String one = text.replace("\n", " ").trim();
        return one.length() <= 110 ? one : one.substring(0, 110) + "…";
    }
}
