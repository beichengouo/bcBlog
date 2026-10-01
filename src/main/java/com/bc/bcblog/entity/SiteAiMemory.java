package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** IRIS 的每日记忆摘要（滚动保留，供后续几天写文章/评论时参考） */
@Data
@TableName("site_ai_memory")
public class SiteAiMemory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String aiName;
    private LocalDate memoryDate;
    /** 当天记忆摘要（第一人称，像随笔，不是流水账） */
    private String summary;
    private Integer activityCount;
    private LocalDateTime createTime;
}
