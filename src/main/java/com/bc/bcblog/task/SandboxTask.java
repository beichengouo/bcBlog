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
    /** 记录最近一次总结的日期，保证每天只跑一次 */
    private volatile String lastMemoryDate = "";
    /** 记录最近一次自动生成纪闻的日期 */
    private volatile String lastNewsDate = "";

    /**
     * 旅人集市刷新：每分钟检查一次，到点（间隔/起始时间）就自动生成新一批商品。
     * 具体是否到期由服务层按「刷新间隔 + 起始时间」判断，这里只负责触发与防重入。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void shop() {
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
     * 旅人纪闻自动生成：每分钟检查一次，到达后台配置的时间（默认 07:00）且当天还没生成过时执行一次。
     */
    @Scheduled(cron = "0 * * * * ?")
    public void news() {
        if (!"1".equals(configService.getConfigValue("sandbox_news_auto_enabled", "1"))) {
            return;
        }
        String configured = configService.getConfigValue("sandbox_news_auto_time", "07:00");
        String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String today = LocalDate.now().toString();
        if (!now.equals(configured) || today.equals(lastNewsDate)) {
            return;
        }
        if (!newsGenerating.compareAndSet(false, true)) {
            return;
        }
        try {
            // 多世界：每个「运行中」的世界各自生成当天的纪闻
            for (com.bc.bcblog.entity.SandboxWorld world : sandboxService.worlds()) {
                if (world.getEnabled() == null || world.getEnabled() != 1) {
                    continue;
                }
                sandboxService.autoGenerateNews(world.getId());
            }
            lastNewsDate = today;
        } finally {
            newsGenerating.set(false);
        }
    }
}
