package com.bc.bcblog.task;

import com.bc.bcblog.service.SandboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

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

    /** 防止上一次还没跑完（AI 调用较慢）时重复触发 */
    private final AtomicBoolean running = new AtomicBoolean(false);

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
}
