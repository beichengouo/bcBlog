package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.entity.SysLoginLog;
import com.bc.bcblog.entity.SysPointLog;
import com.bc.bcblog.entity.SysSignLog;
import com.bc.bcblog.entity.SysVisitStat;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxMemory;
import com.bc.bcblog.entity.SandboxNews;
import com.bc.bcblog.entity.AdminApiLog;
import com.bc.bcblog.mapper.SysLoginLogMapper;
import com.bc.bcblog.mapper.SysPointLogMapper;
import com.bc.bcblog.mapper.SysSignLogMapper;
import com.bc.bcblog.mapper.SysVisitStatMapper;
import com.bc.bcblog.mapper.SandboxActMapper;
import com.bc.bcblog.mapper.SandboxMemoryMapper;
import com.bc.bcblog.mapper.SandboxNewsMapper;
import com.bc.bcblog.mapper.AdminApiLogMapper;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.DataCleanupService;
import com.bc.bcblog.vo.CleanupResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 数据定期清理实现：只清理历史日志类数据，不碰文章、评论等核心内容。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCleanupServiceImpl implements DataCleanupService {

    private final ConfigService configService;
    private final SysLoginLogMapper loginLogMapper;
    private final SysVisitStatMapper visitStatMapper;
    private final SysSignLogMapper signLogMapper;
    private final SysPointLogMapper pointLogMapper;
    private final SandboxActMapper sandboxActMapper;
    private final SandboxMemoryMapper sandboxMemoryMapper;
    private final SandboxNewsMapper sandboxNewsMapper;
    private final com.bc.bcblog.mapper.SandboxShopItemMapper sandboxShopItemMapper;
    private final AdminApiLogMapper adminApiLogMapper;

    @Override
    public CleanupResultVO clean() {
        CleanupResultVO result = new CleanupResultVO();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        int loginDays = parseInt(configService.getConfigValue("cleanup_login_log_days", "90"), 90);
        result.setLoginLog(loginLogMapper.delete(new LambdaQueryWrapper<SysLoginLog>()
                .lt(SysLoginLog::getCreateTime, now.minusDays(loginDays))));

        int visitDays = parseInt(configService.getConfigValue("cleanup_visit_stat_days", "730"), 730);
        result.setVisitStat(visitStatMapper.delete(new LambdaQueryWrapper<SysVisitStat>()
                .lt(SysVisitStat::getStatDate, today.minusDays(visitDays))));

        int signDays = parseInt(configService.getConfigValue("cleanup_sign_log_days", "365"), 365);
        result.setSignLog(signLogMapper.delete(new LambdaQueryWrapper<SysSignLog>()
                .lt(SysSignLog::getSignDate, today.minusDays(signDays))));

        int pointDays = parseInt(configService.getConfigValue("cleanup_point_log_days", "365"), 365);
        result.setPointLog(pointLogMapper.delete(new LambdaQueryWrapper<SysPointLog>()
                .lt(SysPointLog::getCreateTime, now.minusDays(pointDays))));

        // 沙盒行动日志：默认保留 7 天（角色记忆会长期保留，日志只用于前台时间线展示）
        int sandboxActDays = parseInt(configService.getConfigValue("cleanup_sandbox_act_days", "7"), 7);
        result.setSandboxAct(sandboxActMapper.delete(new LambdaQueryWrapper<SandboxAct>()
                .lt(SandboxAct::getCreateTime, now.minusDays(sandboxActDays))));

        // 沙盒每日记忆：默认保留 30 天
        int sandboxMemoryDays = parseInt(configService.getConfigValue("cleanup_sandbox_memory_days", "30"), 30);
        result.setSandboxMemory(sandboxMemoryMapper.delete(new LambdaQueryWrapper<SandboxMemory>()
                .lt(SandboxMemory::getMemoryDate, today.minusDays(sandboxMemoryDays))));

        // 旅人纪闻：默认保留 1 天（即只保留当天，第二天自动清空）
        int sandboxNewsDays = parseInt(configService.getConfigValue("cleanup_sandbox_news_days", "1"), 1);
        result.setSandboxNews(sandboxNewsMapper.delete(new LambdaQueryWrapper<SandboxNews>()
                .lt(SandboxNews::getNewsDate, today.minusDays(sandboxNewsDays - 1L))));
        // 旅人集市：旧批次的商品只保留几天（默认 3 天），前台本来就只展示最新一批
        int shopDays = parseInt(configService.getConfigValue("cleanup_sandbox_shop_days", "3"), 3);
        result.setSandboxShopItem(sandboxShopItemMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.bc.bcblog.entity.SandboxShopItem>()
                        .lt(com.bc.bcblog.entity.SandboxShopItem::getCreateTime,
                                today.minusDays(shopDays - 1L).atStartOfDay())));

        // API 调用审计：默认只保留 3 天
        int auditDays = parseInt(configService.getConfigValue("cleanup_admin_api_log_days", "3"), 3);
        result.setAdminApiLog(adminApiLogMapper.delete(new LambdaQueryWrapper<AdminApiLog>()
                .lt(AdminApiLog::getCreateTime, now.minusDays(auditDays))));

        log.info("数据清理完成：登录日志 {}，访问统计 {}，签到记录 {}，积分流水 {}，沙盒日志 {}，沙盒记忆 {}，旅人纪闻 {}，调用审计 {}",
                result.getLoginLog(), result.getVisitStat(), result.getSignLog(), result.getPointLog(),
                result.getSandboxAct(), result.getSandboxMemory(), result.getSandboxNews(), result.getAdminApiLog());
        return result;
    }

    private int parseInt(String value, int defaultValue) {
        try {
            int v = Integer.parseInt(value);
            return v < 1 ? defaultValue : v;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
