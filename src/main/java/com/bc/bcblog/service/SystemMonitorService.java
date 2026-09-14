package com.bc.bcblog.service;

import com.bc.bcblog.vo.SystemMonitorVO;

/** 系统性能监控与依赖信息服务。 */
public interface SystemMonitorService {

    /** 获取性能监控 + 系统信息 + 依赖信息。 */
    SystemMonitorVO overview();
}
