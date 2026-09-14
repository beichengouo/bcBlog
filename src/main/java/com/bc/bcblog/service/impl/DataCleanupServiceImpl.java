package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.entity.SysLoginLog;
import com.bc.bcblog.entity.SysPointLog;
import com.bc.bcblog.entity.SysSignLog;
import com.bc.bcblog.entity.SysVisitStat;
import com.bc.bcblog.mapper.SysLoginLogMapper;
import com.bc.bcblog.mapper.SysPointLogMapper;
import com.bc.bcblog.mapper.SysSignLogMapper;
import com.bc.bcblog.mapper.SysVisitStatMapper;
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

        log.info("数据清理完成：登录日志 {}，访问统计 {}，签到记录 {}，积分流水 {}",
                result.getLoginLog(), result.getVisitStat(), result.getSignLog(), result.getPointLog());
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
