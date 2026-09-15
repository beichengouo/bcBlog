package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 旅人低语：前台登录用户给角色留言，消耗积分，并会被角色下一次 AI 行动参考。
 */
@Data
@TableName("sandbox_interaction")
public class SandboxInteraction {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 角色 ID */
    private Long characterId;
    /** 留言用户 ID */
    private Long userId;
    /** 用户昵称快照 */
    private String userName;
    /** 用户头像快照 */
    private String userAvatar;
    /** 低语内容 */
    private String content;
    /** 消耗积分 */
    private Integer pointsCost;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
