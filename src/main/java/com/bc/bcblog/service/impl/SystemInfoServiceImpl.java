package com.bc.bcblog.service.impl;

import com.bc.bcblog.service.SystemInfoService;
import com.bc.bcblog.vo.SystemInfoVO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/** 系统运行信息实现，启动时记录开始时间。 */
@Service
public class SystemInfoServiceImpl implements SystemInfoService {

    /** 应用启动时间（Bean 初始化时间） */
    private final LocalDateTime startTime = LocalDateTime.now();

    @Override
    public SystemInfoVO getSystemInfo() {
        LocalDateTime now = LocalDateTime.now();
        SystemInfoVO vo = new SystemInfoVO();
        vo.setStartTime(startTime);
        vo.setServerTime(now);
        vo.setUptimeSeconds(Duration.between(startTime, now).getSeconds());
        return vo;
    }
}
