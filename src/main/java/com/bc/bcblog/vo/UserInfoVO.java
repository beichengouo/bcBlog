package com.bc.bcblog.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserInfoVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private List<String> menus;
    private String email;
    private Integer exp;
    private Integer points;
    private Integer level;
    private String levelName;
    private Integer nextLevelExp;
    private Integer canInvite;
    private Integer signDays;
    private Boolean signedToday;
    private String inviteCode;
}
