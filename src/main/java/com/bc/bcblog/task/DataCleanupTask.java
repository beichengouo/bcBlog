package com.bc.bcblog.task;

import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.DataCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 定期清理任务：每分钟检查一次，到达后台配置的执行时间时执行一次，每天只执行一次。
 * 执行时间可以在后台「系统设置 → 数据清理」里修改，不需要重启。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataCleanupTask {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ConfigService configService;
    private final DataCleanupService dataCleanupService;

    /** 记录最近一次执行的日期，避免同一天重复执行 */
    private volatile String lastRunDate = "";

    @Scheduled(cron = "0 * * * * ?")
    public void run() {
        // 测试进程里跳过（见 SandboxTask.schedulerDisabled 的说明）
        if (Boolean.parseBoolean(System.getProperty("bcblog.sandbox.scheduler.disabled", "false"))) {
            return;
        }
        if (!"1".equals(configService.getConfigValue("cleanup_enabled", "1"))) {
            return;
        }
        String configured = configService.getConfigValue("cleanup_time", "03:30");
        String now = LocalTime.now().format(TIME_FORMATTER);
        String today = LocalDate.now().toString();
        if (!now.equals(configured) || today.equals(lastRunDate)) {
            return;
        }
        try {
            dataCleanupService.clean();
            lastRunDate = today;
        } catch (Exception e) {
            log.error("定期清理执行失败", e);
        }
    }
}
