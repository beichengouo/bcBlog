package com.bc.bcblog.common;

import com.bc.bcblog.dto.ChatMessage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * AI 调用记录器（**默认关闭**）：把每一次调用的完整请求（system / user / assistant 预填充）
 * 和模型完整返回原样落盘，用来人工核对"到底发了什么、回了什么"。
 *
 * 开启方式：设置系统属性 {@code bcblog.ai.log-file=<文件路径>}。没设置时所有方法直接返回，
 * 生产环境不会有任何额外开销（沙盒测试工具会在需要时自己设置这个属性）。
 *
 * 为什么要记预填充：三段式/五段式的输出格式是靠 assistant 预填充（最后一条 assistant 消息）
 * 和提示词一起约束出来的，只记 system+user 是看不出全貌的。
 */
public final class AiCallRecorder {

    private static final String PROP = "bcblog.ai.log-file";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private AiCallRecorder() {
    }

    /** 是否开启 */
    public static boolean enabled() {
        String path = System.getProperty(PROP);
        return path != null && !path.trim().isEmpty();
    }

    /**
     * 记一次调用。
     *
     * @param action  这次调用算什么动作（审计标签，例如「沙盒·立即执行一次」）
     * @param model   模型名
     * @param messages 实际发出去的消息列表（含 assistant 预填充）
     * @param response 模型返回；失败时为 null
     * @param error    失败原因；成功时为 null
     * @param millis  耗时（毫秒）
     */
    public static void record(String action, String model, List<ChatMessage> messages,
                              String response, String error, long millis) {
        if (!enabled()) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            boolean ok = error == null;
            sb.append("\n\n<!-- ===== [").append(LocalDateTime.now().format(TIME)).append("] ")
                    .append(action == null ? "AI 调用" : action)
                    .append(" | ").append(model)
                    .append(" | ").append(String.format("%.1f", millis / 1000.0)).append(" 秒")
                    .append(" | ").append(ok ? ("返回 " + (response == null ? 0 : response.length()) + " 字符")
                            : "失败：" + error)
                    .append(" ===== -->\n");
            sb.append("### 请求（").append(messages == null ? 0 : messages.size()).append(" 条消息）\n\n");
            if (messages != null) {
                for (int i = 0; i < messages.size(); i++) {
                    ChatMessage msg = messages.get(i);
                    sb.append("#### [").append(i + 1).append("] ").append(msg.getRole())
                            .append("（").append(msg.getContent() == null ? 0 : msg.getContent().length())
                            .append(" 字符）\n\n```text\n")
                            .append(msg.getContent() == null ? "" : msg.getContent())
                            .append("\n```\n\n");
                }
            }
            sb.append("### 返回\n\n```text\n")
                    .append(ok ? (response == null ? "（空）" : response) : ("【失败】" + error))
                    .append("\n```\n");
            Path file = Paths.get(System.getProperty(PROP));
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            // 记录失败绝不能影响业务
        }
    }
}
