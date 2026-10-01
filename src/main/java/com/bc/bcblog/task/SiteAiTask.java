package com.bc.bcblog.task;

import com.bc.bcblog.service.SiteAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 网站 AI「IRIS」的定时任务。
 *
 * 每分钟检查一次：写文章 / 评论吐槽 / 状态更新 三个时间点是否到了（到点且今天没跑过才执行），
 * 以及每天 23:30 生成当天记忆。真正是否执行由服务层按开关、配额、每日篇数判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteAiTask {

    private final SiteAiService siteAiService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String lastMemoryDate = "";
    /** 今天试过几次、最后一次是什么时候：失败不可以每分钟猛打接口（一次就是一次调用额度） */
    private volatile String memoryTryDate = "";
    private volatile int memoryTries = 0;
    private volatile LocalDateTime memoryLastTryAt = null;
    /** 一晚上最多试几次、两次之间至少隔几分钟 */
    private static final int MEMORY_MAX_TRIES = 3;
    private static final int MEMORY_RETRY_MINUTES = 5;

    private boolean disabled() {
        return Boolean.parseBoolean(System.getProperty("bcblog.sandbox.scheduler.disabled", "false"));
    }

    @Scheduled(cron = "0 * * * * ?")
    public void run() {
        if (disabled() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            int done = siteAiService.autoRun();
            if (done > 0) {
                log.info("IRIS 本轮执行了 {} 次活动", done);
            }
        } catch (Exception e) {
            log.warn("IRIS 定时任务异常：{}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** 每天 23:30 之后生成一次当天记忆（供后续几天写作参考） */
    @Scheduled(cron = "0 * * * * ?")
    public void memory() {
        if (disabled()) {
            return;
        }
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(23, 30))) {
            return;
        }
        String today = LocalDate.now().toString();
        if (today.equals(lastMemoryDate)) {
            return;
        }
        // 失败退避：以前失败时不记日期，23:30 之后会每分钟重试一次（最多 30 次），白烧调用额度
        if (!today.equals(memoryTryDate)) {
            memoryTryDate = today;
            memoryTries = 0;
        }
        if (memoryTries >= MEMORY_MAX_TRIES) {
            return;
        }
        LocalDateTime nowTime = LocalDateTime.now();
        if (memoryLastTryAt != null && memoryLastTryAt.plusMinutes(MEMORY_RETRY_MINUTES).isAfter(nowTime)) {
            return;
        }
        memoryTries++;
        memoryLastTryAt = nowTime;
        try {
            int n = siteAiService.summarize();
            lastMemoryDate = today;
            if (n > 0) {
                log.info("IRIS 今天的记忆已生成");
            }
        } catch (Exception e) {
            log.warn("IRIS 记忆生成失败（今晚已试 {} 次，最多 {} 次）：{}",
                    memoryTries, MEMORY_MAX_TRIES, e.getMessage());
        }
    }
}
