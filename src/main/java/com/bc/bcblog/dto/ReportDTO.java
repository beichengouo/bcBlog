package com.bc.bcblog.dto;

import lombok.Data;

/** 前台提交举报 */
@Data
public class ReportDTO {
    /** 目前只开放评论举报；留空按 comment 处理 */
    private String targetType;
    private Long targetId;
    /** 违法有害信息 / 广告垃圾 / 人身攻击 / 色情低俗 / 侵权 / 其他 */
    private String reason;
    private String detail;
}
