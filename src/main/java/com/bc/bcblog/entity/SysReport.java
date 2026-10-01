package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 违法有害信息举报记录。
 *
 * 备案安全评估关注两点：① 用户有没有举报渠道；② 举报之后有没有处置记录。
 * 所以这里既存举报本身，也存「被举报内容快照」与「处理结论、处理人、处理时间」——
 * 举报对象后来被删除时，仍然能追溯当时举报的是什么。
 */
@Data
@TableName("sys_report")
public class SysReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** comment / article / user */
    private String targetType;
    private Long targetId;
    private Long articleId;
    private String articleTitle;
    /** 被举报内容快照（评论被删也能追溯） */
    private String contentSnapshot;
    private String reason;
    private String detail;
    private Long reporterUserId;
    private String reporterName;
    /** pending / handled / ignored */
    private String status;
    private String handleNote;
    private Long handlerId;
    private String handlerName;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
