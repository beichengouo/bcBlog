package com.bc.bcblog.tools;

import com.bc.bcblog.dto.SandboxCharacterGenerateDTO;
import com.bc.bcblog.vo.SandboxCharacterDraftVO;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 「AI 创作角色」这条链路的**真实 AI** 复现工具（只读：不会写任何数据）。
 *
 * 用来回答"为什么这次生成很慢 / 到底有没有返回 / 是不是格式有问题"：
 * 用同样的要求、同样的服务商与模型跑一次，把耗时、返回长度、解析后的字段（或异常信息）写进报告。
 *
 * 手动运行：
 *   mvn test "-Dtest=SandboxCharacterDraftCheck" "-Dsandbox.draft.worldId=2" ^
 *            "-Dsandbox.draft.providerId=2" "-Dsandbox.draft.model=gemini-3.1-pro-preview" ^
 *            "-Dsandbox.draft.requirement=格蕾，魔族，女性，魔王……"
 * 报告：target/probe/character-draft-check.md
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxCharacterDraftCheck {

    static {
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    @Autowired
    private SandboxServiceImpl service;
    /** 用应用自身的 Jackson 配置序列化，看到的就是浏览器真正收到的 JSON */
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 这个 VO 序列化出来都有哪些字段（对照前端读取的字段用） */
    private String jsonKeys(SandboxCharacterDraftVO draft) throws Exception {
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.valueToTree(draft);
        java.util.List<String> keys = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        return String.join(", ", keys);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    @Test
    void run() throws Exception {
        Long worldId = Long.getLong("sandbox.draft.worldId", 2L);
        Long providerId = Long.getLong("sandbox.draft.providerId", 2L);
        String model = System.getProperty("sandbox.draft.model", "假流式-gemini-3.1-pro-preview");
        String requirement = System.getProperty("sandbox.draft.requirement",
                "格蕾，魔族，女性，魔王，女同，但与传闻中的嗜血残暴不同，格蕾实则是一个摆烂的魔王，"
                        + "不喜战争，最大的兴趣是从世界各地抓回漂亮的女孩子填充后宫。");

        StringBuilder report = new StringBuilder("# AI 创作角色 · 复现报告（只读）\n\n");
        report.append("- 世界 id：").append(worldId)
                .append("；服务商 id：").append(providerId)
                .append("；模型：").append(model).append('\n')
                .append("- 输入要求：").append(requirement).append("\n\n");

        SandboxCharacterGenerateDTO dto = new SandboxCharacterGenerateDTO();
        dto.setProviderId(providerId);
        dto.setModel(model);
        dto.setRequirement(requirement);

        long start = System.currentTimeMillis();
        try {
            SandboxCharacterDraftVO draft = service.generateCharacter(dto, worldId);
            long cost = System.currentTimeMillis() - start;
            report.append("## 结果：成功（解析通过）\n\n")
                    .append("- 耗时：").append(cost).append(" ms\n")
                    // 先给"接口真正返回的 JSON"（字段名与浏览器收到的一致）
                    .append("- 接口 JSON 字段：").append(jsonKeys(draft)).append("\n")
                    .append("- 姓名：").append(draft.getName()).append("\n")
                    .append("- 称号：").append(draft.getTitle()).append("\n")
                    .append("- 战斗力：").append(draft.getCombatPower()).append("\n")
                    .append("- 初始地点：").append(draft.getLocationName()).append("\n")
                    .append("- 人设：\n").append(draft.getPersona()).append("\n")
                    .append("- 初始物品：").append(draft.getItems()).append("\n")
                    .append("- 原始返回长度：").append(draft.getRaw() == null ? 0 : draft.getRaw().length())
                    .append(" 字符\n\n## 原始返回（截断 4000 字）\n\n```text\n")
                    .append(draft.getRaw()).append("\n```\n");
            report.append("\n## 接口真正返回的 JSON（前 1200 字）\n\n```json\n")
                    .append(truncate(objectMapper.writeValueAsString(draft), 1200)).append("\n```\n");
            System.out.println("✅ 生成成功，耗时 " + cost + " ms");
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - start;
            report.append("## 结果：失败\n\n")
                    .append("- 耗时：").append(cost).append(" ms\n")
                    .append("- 异常：").append(e.getClass().getSimpleName()).append("：").append(e.getMessage())
                    .append('\n');
            System.out.println("❌ 生成失败：" + e.getMessage());
        }

        Path dir = Paths.get("target", "probe");
        Files.createDirectories(dir);
        Files.write(dir.resolve("character-draft-check.md"),
                report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("报告：target/probe/character-draft-check.md");
    }
}
