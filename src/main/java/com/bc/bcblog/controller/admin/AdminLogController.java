package com.bc.bcblog.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SysLoginLog;
import com.bc.bcblog.mapper.SysLoginLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 后台登录日志接口（只读分页查询，直接使用 Mapper）。
 */
@RestController
@RequestMapping("/api/admin/log")
@RequiredArgsConstructor
public class AdminLogController {

    private final SysLoginLogMapper loginLogMapper;

    @GetMapping("/login")
    public Result<PageResult<SysLoginLog>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String username) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.trim().isEmpty()) {
            wrapper.like(SysLoginLog::getUsername, username.trim());
        }
        wrapper.orderByDesc(SysLoginLog::getCreateTime);
        Page<SysLoginLog> p = new Page<>(page, size);
        IPage<SysLoginLog> result = loginLogMapper.selectPage(p, wrapper);
        return Result.ok(PageResult.of(result.getTotal(), result.getRecords()));
    }

    /** 清空登录日志；传 startTime/endTime 时只删除该时间范围内的日志 */
    @DeleteMapping("/login")
    public Result<Integer> delete(@RequestParam(required = false) String startTime,
                                  @RequestParam(required = false) String endTime) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (startTime != null && !startTime.trim().isEmpty()) {
            wrapper.ge(SysLoginLog::getCreateTime, LocalDateTime.parse(startTime.trim(), formatter));
        }
        if (endTime != null && !endTime.trim().isEmpty()) {
            wrapper.le(SysLoginLog::getCreateTime, LocalDateTime.parse(endTime.trim(), formatter));
        }
        int deleted = loginLogMapper.delete(wrapper);
        return Result.ok(deleted);
    }
}
