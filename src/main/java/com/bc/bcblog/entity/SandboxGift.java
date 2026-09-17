package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异世界礼物：用户从旅人集市买下商品赠送给角色的记录。
 * 角色下一次行动的提示词里只会出现「收到来自异世界的礼物XXX」——不带赠送者名字，
 * 避免角色记忆错乱（购买者信息留在购买记录里，仅前台弹窗与后台可见）。
 */
@Data
@TableName("sandbox_gift")
public class SandboxGift {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long worldId;
    private Long characterId;
    private String itemName;
    private String itemDescription;
    private Integer quantity;
    private Integer pointsCost;
    /** 商品单价（金币）：前台用户按汇率折算成积分购买 */
    private Integer coinPrice;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
