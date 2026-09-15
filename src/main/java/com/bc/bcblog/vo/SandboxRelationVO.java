package com.bc.bcblog.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 前台展示用的角色关系（含双向好感度）。 */
@Data
public class SandboxRelationVO {
    /** 角色 ID（后台列表用：好感度的持有方） */
    private Long characterId;
    /** 角色名（后台列表用） */
    private String characterName;
    /** 关系记录 ID（管理员编辑用，前台可忽略） */
    private Long id;
    /** 对方角色 ID */
    private Long targetId;
    /** 对方角色名 */
    private String targetName;
    /** 对方称号 */
    private String targetTitle;
    /** 对方立绘 */
    private String targetAvatar;
    /** 对方当前位置 */
    private String targetLocation;
    /** 本角色对对方的好感度 */
    private Integer favor;
    /** 上一次实际生效的变化（前台显示「较上次 +3」） */
    private Integer lastChange;
    /** 上一次变化时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastChangeTime;
    /** 本角色对对方的好感等级文字 */
    private String favorLevel;
    /** 对方对本角色的好感度 */
    private Integer reverseFavor;
    /** 对方对本角色的好感等级文字 */
    private String reverseFavorLevel;
    /** 备注 */
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
