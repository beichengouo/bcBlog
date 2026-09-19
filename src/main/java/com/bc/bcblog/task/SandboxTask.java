package com.bc.bcblog.task;

import com.bc.bcblog.service.SandboxService;
import com.bc.bcblog.service.ConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 沙盒自动行动任务。
 *
 * 每 5 分钟扫一次，看哪些角色的「下次行动时间」已经到期；
 * 角色真正的行动间隔由角色自己的随机区间决定（默认 45~75 分钟），
 * 并受后台的「沙盒开关、夜间静默、每日上限」约束。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxTask {

    private final SandboxService sandboxService;
    private final ConfigService configService;

    /** 防止上一次还没跑完（AI 调用较慢）时重复触发 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** 记忆总结同样防止重入 */
    private final AtomicBoolean summarizing = new AtomicBoolean(false);
    /** 纪闻自动生成防止重入 */
    private final AtomicBoolean newsGenerating = new AtomicBoolean(false);
    /** 旅人集市刷新防止重入 */
    private final AtomicBoolean shopRefreshing = new AtomicBoolean(false);
    /** 旅人委托板刷新防止重入 */
    private final AtomicBoolean questRefreshing = new AtomicBoolean(false);
    /** 记录最近一次总结的日期，保证每天只跑一次 */
    private volatile String lastMemoryDate = "";

    /**
     * 测试进程里把定时任务整个跳过。
     *
     * 为什么需要：沙盒测试工具会设「模拟时钟偏移」（把时间往后推，用来快速模拟多天剧情），
     * 而测试用的 Spring 上下文里定时任务也在跑——它一旦用模拟时钟判断"谁到期了"，
     * 就会把**真实世界**里本该明天才行动的角色提前执行掉，污染正式数据（真的发生过一次）。
     * 所以测试进程启动时会设上 bcblog.sandbox.scheduler.disabled=true，这里直接返回。
     */
    private boolean schedulerDisabled() {
        return Boolean.parseBoolean(System.getProperty("bcblog.sandbox.scheduler.disabled", "false"));
    }

    /**
     * 旅人集市刷新：每分钟检查一次，到点（间隔/起始时间）就自动生成新一批商品。
     * 具体是否到期由服务层按「刷新间隔 + 起始时间」判断，这里只负责触发与防重入。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void shop() {
        if (schedulerDisabled()) {
            return;
        }
        if (!"1".equals(configService.getConfigValue("sandbox_shop_auto_enabled", "1"))) {
            return;
        }
        if (!shopRefreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            sandboxService.autoRefreshShop();
        } catch (Exception e) {
            log.warn("旅人集市刷新任务异常：{}", e.getMessage());
        } finally {
            shopRefreshing.set(false);
        }
    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void run() {
        if (schedulerDisabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            sandboxService.runScheduled();
        } catch (Exception e) {
            log.warn("沙盒自动行动任务执行异常：{}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /**
     * 每日记忆总结：每分钟检查一次，到达后台配置的时间（默认 23:50）时为当天有行动的角色生成记忆。
     * 记忆用于后续几天的活动，让角色的日志不必长期堆积。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void memory() {
        if (schedulerDisabled()) {
            return;
        }
        if (!"1".equals(configService.getConfigValue("sandbox_memory_enabled", "1"))) {
            return;
        }
        String configured = configService.getConfigValue("sandbox_memory_time", "23:50");
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String today = LocalDate.now().toString();
        if (!now.equals(configured) || today.equals(lastMemoryDate)) {
            return;
        }
        if (!summarizing.compareAndSet(false, true)) {
            return;
        }
        try {
            sandboxService.summarizeDaily();
            lastMemoryDate = today;
            log.info("沙盒每日记忆总结完成（{}）", today);
        } catch (Exception e) {
            log.warn("沙盒每日记忆总结异常：{}", e.getMessage());
        } finally {
            summarizing.set(false);
        }
    }

    /**
     * 旅人委托板刷新：每分钟检查一次，到点（刷新间隔 + 当天首次时间）就换一批新委托。
     * 是否到期由服务层按配置判断，接取中的委托会被保留。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void questBoard() {
        if (schedulerDisabled()) {
            return;
        }
        if (!"1".equals(configService.getConfigValue("sandbox_quest_auto_enabled", "1"))) {
            return;
        }
        if (!questRefreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            sandboxService.autoRefreshQuests();
        } catch (Exception e) {
            log.warn("旅人委托板刷新任务异常：{}", e.getMessage());
        } finally {
            questRefreshing.set(false);
        }
    }

    /**
     * 旅人纪闻自动生成：每分钟检查一次，到点（刷新间隔 + 当天首次时间，默认每天 07:00）就生成一批。
     * 是否到期由服务层按配置判断，这样「一天刷几次」只需要改间隔。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void news() {
        if (schedulerDisabled()) {
            return;
        }
        if (!"1".equals(configService.getConfigValue("sandbox_news_auto_enabled", "1"))) {
            return;
        }
        if (!newsGenerating.compareAndSet(false, true)) {
            return;
        }
        try {
            sandboxService.autoRefreshNews();
        } catch (Exception e) {
            log.warn("旅人纪闻生成任务异常：{}", e.getMessage());
        } finally {
            newsGenerating.set(false);
        }
    }
}
