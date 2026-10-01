package com.bc.bcblog.service;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.dto.ReportDTO;
import com.bc.bcblog.dto.ReportHandleDTO;
import com.bc.bcblog.entity.SysReport;

/** 违法有害信息举报：前台提交、后台处理 */
public interface ReportService {

    /** 前台提交举报（需登录，便于责任可追溯） */
    void submit(ReportDTO dto);

    /** 后台分页查询（status 为空表示全部） */
    PageResult<SysReport> page(String status, long page, long size);

    /** 后台处理：标记已处理 / 已忽略，并记录结论与处理人 */
    void handle(Long id, ReportHandleDTO dto);
}
