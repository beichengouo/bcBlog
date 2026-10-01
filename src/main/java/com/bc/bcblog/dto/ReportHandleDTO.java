package com.bc.bcblog.dto;

import lombok.Data;

/** 后台处理举报 */
@Data
public class ReportHandleDTO {
    /** handled 已处理 / ignored 已忽略 */
    private String status;
    /** 处理结论说明 */
    private String note;
    /**
     * 是否顺带处置被举报的评论：
     *   none   仅记录结论，不动评论（可稍后在「评论管理」里处置）
     *   reject 把评论置为「已拒绝」，前台立即不再展示（默认，可逆、留档）
     *   delete 直接删除评论（内容快照仍在举报记录里，可追溯）
     */
    private String commentAction;
}
