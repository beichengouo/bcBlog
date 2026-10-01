package com.bc.bcblog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.dto.ReportDTO;
import com.bc.bcblog.dto.ReportHandleDTO;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.entity.BlogComment;
import com.bc.bcblog.entity.SysReport;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.BlogArticleMapper;
import com.bc.bcblog.mapper.BlogCommentMapper;
import com.bc.bcblog.mapper.SysReportMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 举报处理。
 *
 * 两个设计点：
 *   1. 举报必须登录——安全评估要求用户行为可追溯，匿名举报无法核实也无法防刷；
 *   2. 提交时把「被举报内容」快照一起存下来：评论可能随后被作者或管理员删除，
 *      只存 ID 的话事后无法说明当时举报的是什么。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    /** 允许的举报原因（与前台下拉保持一致） */
    private static final List<String> REASONS = Arrays.asList(
            "违法有害信息", "广告垃圾", "人身攻击", "色情低俗", "侵权内容", "其他");

    private final SysReportMapper reportMapper;
    private final BlogCommentMapper commentMapper;
    private final BlogArticleMapper articleMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public void submit(ReportDTO dto) {
        if (dto == null || dto.getTargetId() == null) {
            throw new BusinessException("举报对象不存在");
        }
        Long userId;
        try {
            userId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            throw new BusinessException(401, "请先登录后再举报");
        }
        SysUser user = sysUserMapper.selectById(userId);
        String targetType = dto.getTargetType() == null || dto.getTargetType().trim().isEmpty()
                ? "comment" : dto.getTargetType().trim();
        if (!"comment".equals(targetType)) {
            throw new BusinessException("目前仅支持举报评论");
        }
        BlogComment comment = commentMapper.selectById(dto.getTargetId());
        if (comment == null) {
            throw new BusinessException("这条评论已经不存在了，无需举报");
        }
        if (userId.equals(comment.getUserId())) {
            throw new BusinessException("不能举报自己的评论");
        }
        String reason = dto.getReason() == null ? "" : dto.getReason().trim();
        if (!REASONS.contains(reason)) {
            throw new BusinessException("请选择举报原因");
        }
        // 同一个用户对同一条评论只保留一条待处理举报，避免刷屏
        Long dup = reportMapper.selectCount(new LambdaQueryWrapper<SysReport>()
                .eq(SysReport::getTargetType, "comment")
                .eq(SysReport::getTargetId, comment.getId())
                .eq(SysReport::getReporterUserId, userId)
                .eq(SysReport::getStatus, "pending"));
        if (dup != null && dup > 0) {
            throw new BusinessException("你已经举报过这条评论了，我们会在 24 小时内处理");
        }

        SysReport report = new SysReport();
        report.setTargetType("comment");
        report.setTargetId(comment.getId());
        report.setArticleId(comment.getArticleId());
        if (comment.getArticleId() != null) {
            BlogArticle article = articleMapper.selectById(comment.getArticleId());
            report.setArticleTitle(article == null ? null : article.getTitle());
        }
        report.setContentSnapshot(truncate(comment.getContent(), 900));
        report.setReason(reason);
        report.setDetail(truncate(dto.getDetail(), 400));
        report.setReporterUserId(userId);
        report.setReporterName(user == null ? null : (user.getNickname() == null ? user.getUsername() : user.getNickname()));
        report.setStatus("pending");
        report.setCreateTime(LocalDateTime.now());
        reportMapper.insert(report);
        log.info("用户 #{} 举报评论 #{}（原因：{}）", userId, comment.getId(), reason);
    }

    @Override
    public PageResult<SysReport> page(String status, long page, long size) {
        Page<SysReport> p = new Page<>(page, size);
        IPage<SysReport> result = reportMapper.selectPage(p, new LambdaQueryWrapper<SysReport>()
                .eq(status != null && !status.trim().isEmpty(), SysReport::getStatus, status)
                .orderByAsc(SysReport::getStatus)
                .orderByDesc(SysReport::getCreateTime));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public void handle(Long id, ReportHandleDTO dto) {
        SysReport report = reportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException("举报记录不存在");
        }
        String status = dto == null || dto.getStatus() == null ? "handled" : dto.getStatus().trim();
        if (!"handled".equals(status) && !"ignored".equals(status)) {
            throw new BusinessException("处理结果只能是 handled 或 ignored");
        }
        Long adminId = null;
        String adminName = null;
        try {
            adminId = StpUtil.getLoginIdAsLong();
            SysUser admin = sysUserMapper.selectById(adminId);
            adminName = admin == null ? null
                    : (admin.getNickname() == null ? admin.getUsername() : admin.getNickname());
        } catch (Exception ignored) {
            // 定时任务等无登录上下文的场景不记录处理人
        }
        report.setStatus(status);
        // 顺带处置评论（方案 A）：拒绝是软处置——前台不再展示，但内容与处置记录都留档
        String action = dto == null || dto.getCommentAction() == null ? "none" : dto.getCommentAction().trim();
        String actionNote = "";
        if (!"ignored".equals(status) && !"none".equals(action)) {
            if ("delete".equals(action)) {
                commentMapper.deleteById(report.getTargetId());
                actionNote = "；已删除该评论（内容快照已留档）";
                log.info("处理举报 #{}：删除评论 #{}", report.getId(), report.getTargetId());
            } else if ("reject".equals(action)) {
                BlogComment comment = commentMapper.selectById(report.getTargetId());
                if (comment != null) {
                    comment.setStatus(2);
                    commentMapper.updateById(comment);
                    actionNote = "；已将该评论置为「已拒绝」，前台不再展示";
                    log.info("处理举报 #{}：拒绝评论 #{}", report.getId(), report.getTargetId());
                } else {
                    actionNote = "；该评论此前已被删除";
                }
            }
        }
        String note = dto == null ? null : dto.getNote();
        String finalNote = (note == null || note.trim().isEmpty() ? "" : note.trim()) + actionNote;
        report.setHandleNote(truncate(finalNote.isEmpty() ? null : finalNote, 280));
        report.setHandlerId(adminId);
        report.setHandlerName(adminName);
        report.setHandleTime(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
