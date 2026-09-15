package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 沙盒行动记录：角色每一步做了什么、想了什么。 */
@Data
@TableName("sandbox_act")
public class SandboxAct {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属世界 */
    private Long worldId;
    /** 角色 ID */
    private Long characterId;
    /** 这一步所处地点 */
    private String locationName;
    /** 这一步的横向坐标 */
    private Integer x;
    /** 这一步的纵向坐标 */
    private Integer y;
    /** 做了什么，多条用换行分隔 */
    private String actions;
    /** 心声 */
    private String innerVoice;
    /** 这一步互动的其他角色，逗号分隔 */
    private String companions;
    /** 这一步的好感度变化，如「零 +3」 */
    private String favorChange;
    /** 这一步结束后的状态（JSON） */
    private String statusJson;
    /** 这一步的金币变化，正为赚取、负为消耗 */
    private Integer coinChange;
    /** 一句话概括 */
    private String summary;
    /** AI 原始返回，便于排查问题 */
    private String rawResponse;
    /** 是否来自 AI：1 是，0 为兜底记录 */
    private Integer fromAi;
    /** 是否管理员手动执行：1 是（不占用每日额度） */
    private Integer manual;
    /** 是否由其他角色的互动触发的回应回合：1 是 */
    private Integer reaction;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
