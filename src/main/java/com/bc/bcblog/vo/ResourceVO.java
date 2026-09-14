package com.bc.bcblog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 前台智库资源展示对象。 */
@Data
public class ResourceVO {
    private Long id;
    private String title;
    private String description;
    private String cover;
    /** 前往资源所需积分 */
    private Integer points;
    /** 资源详情内容（HTML） */
    private String content;
    /** 未解锁时不返回 */
    private String url;
    /** 未解锁时不返回 */
    private String password;
    /** 当前用户是否已解锁 */
    private Boolean unlocked;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
