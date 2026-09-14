package com.bc.bcblog.service;

import com.bc.bcblog.vo.SystemInfoVO;

/** 系统运行信息服务。 */
public interface SystemInfoService {

    /** 获取系统启动时间、已运行时长和服务器当前时间。 */
    SystemInfoVO getSystemInfo();
}
