package com.bc.bcblog.service;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.entity.AdminApiLog;

import java.util.List;
import java.util.Map;

/** API 调用审计服务。 */
public interface AuditLogService {

    /** 记录一次调用（动作/来源取自 AuditContext） */
    void record(String target, boolean success, String message, Long costMs);

    /** 记录一次被拒绝的越权尝试 */
    void recordDenied(String action, String target);

    PageResult<AdminApiLog> page(Long adminId, String keyword, String startDate, String endDate, long page, long size);

    /** 按管理员汇总今天的调用次数与近 7 天次数 */
    List<Map<String, Object>> summary();
}
