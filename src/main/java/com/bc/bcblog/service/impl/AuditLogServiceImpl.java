package com.bc.bcblog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.component.AuditContext;
import com.bc.bcblog.entity.AdminApiLog;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.AdminApiLogMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API 调用审计实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AdminApiLogMapper logMapper;
    private final SysUserMapper userMapper;

    @Override
    public void record(String target, boolean success, String message, Long costMs) {
        try {
            AdminApiLog entity = new AdminApiLog();
            entity.setAction(AuditContext.action());
            entity.setSource(AuditContext.isSchedule() ? "schedule" : "manual");
            entity.setTarget(truncate(target, 190));
            entity.setSuccess(success ? 1 : 0);
            entity.setMessage(truncate(message, 290));
            entity.setCostMs(costMs == null ? null : (int) Math.min(Integer.MAX_VALUE, costMs));
            entity.setCreateTime(LocalDateTime.now());
            fillCaller(entity);
            logMapper.insert(entity);
        } catch (Exception e) {
            // 审计失败不能影响主流程
            log.warn("写入审计日志失败：{}", e.getMessage());
        }
    }

    @Override
    public void recordDenied(String action, String target) {
        try {
            AdminApiLog entity = new AdminApiLog();
            entity.setAction(truncate(action, 90));
            entity.setSource("manual");
            entity.setTarget(truncate(target, 190));
            entity.setSuccess(0);
            // 消息按真实原因写，便于在「API 调用审计」里分辨是超管专属还是二次验证未通过
            entity.setMessage("权限不足：" + action);
            entity.setCreateTime(LocalDateTime.now());
            fillCaller(entity);
            logMapper.insert(entity);
        } catch (Exception e) {
            log.warn("写入越权审计失败：{}", e.getMessage());
        }
    }

    @Override
    public PageResult<AdminApiLog> page(Long adminId, String keyword, String startDate, String endDate,
                                        long page, long size) {
        LambdaQueryWrapper<AdminApiLog> wrapper = new LambdaQueryWrapper<AdminApiLog>()
                .eq(adminId != null, AdminApiLog::getAdminId, adminId)
                .like(keyword != null && !keyword.trim().isEmpty(), AdminApiLog::getAction, keyword == null ? null : keyword.trim())
                .orderByDesc(AdminApiLog::getId);
        if (startDate != null && !startDate.trim().isEmpty()) {
            wrapper.ge(AdminApiLog::getCreateTime, LocalDate.parse(startDate.trim()).atStartOfDay());
        }
        if (endDate != null && !endDate.trim().isEmpty()) {
            wrapper.lt(AdminApiLog::getCreateTime, LocalDate.parse(endDate.trim()).plusDays(1).atStartOfDay());
        }
        IPage<AdminApiLog> result = logMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public List<Map<String, Object>> summary() {
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getRole, "SUPER", "ADMIN1", "ADMIN2"));
        for (SysUser user : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("adminId", user.getId());
            row.put("adminName", user.getNickname() == null ? user.getUsername() : user.getNickname());
            row.put("role", user.getRole());
            row.put("today", logMapper.selectCount(new LambdaQueryWrapper<AdminApiLog>()
                    .eq(AdminApiLog::getAdminId, user.getId())
                    .ge(AdminApiLog::getCreateTime, todayStart)));
            row.put("week", logMapper.selectCount(new LambdaQueryWrapper<AdminApiLog>()
                    .eq(AdminApiLog::getAdminId, user.getId())
                    .ge(AdminApiLog::getCreateTime, weekStart)));
            list.add(row);
        }
        Map<String, Object> scheduleRow = new LinkedHashMap<>();
        scheduleRow.put("adminId", null);
        scheduleRow.put("adminName", "定时任务");
        scheduleRow.put("role", "SYSTEM");
        scheduleRow.put("today", logMapper.selectCount(new LambdaQueryWrapper<AdminApiLog>()
                .eq(AdminApiLog::getSource, "schedule")
                .ge(AdminApiLog::getCreateTime, todayStart)));
        scheduleRow.put("week", logMapper.selectCount(new LambdaQueryWrapper<AdminApiLog>()
                .eq(AdminApiLog::getSource, "schedule")
                .ge(AdminApiLog::getCreateTime, weekStart)));
        list.add(scheduleRow);
        return list;
    }

    private void fillCaller(AdminApiLog entity) {
        try {
            if (StpUtil.isLogin()) {
                Long uid = StpUtil.getLoginIdAsLong();
                entity.setAdminId(uid);
                SysUser user = userMapper.selectById(uid);
                if (user != null) {
                    entity.setAdminName(user.getNickname() == null ? user.getUsername() : user.getNickname());
                }
            }
        } catch (Exception ignored) {
            // 定时任务或非 Web 线程取不到登录信息
        }
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                entity.setIp(forwarded != null && !forwarded.isEmpty()
                        ? forwarded.split(",")[0].trim() : request.getRemoteAddr());
            }
        } catch (Exception ignored) {
            // 忽略
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
