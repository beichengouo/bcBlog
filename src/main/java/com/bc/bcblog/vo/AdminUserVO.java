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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
