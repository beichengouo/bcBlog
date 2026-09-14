package com.bc.bcblog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 管理员列表返回对象。 */
@Data
public class AdminUserVO {
    private Long id;
    private String username;
    private String nickname;
    private String role;
    private List<String> menus;
    private Integer status;
    private String email;
    private Integer exp;
    private Integer points;
    private Integer level;
    private Integer canInvite;
    private Integer signDays;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
