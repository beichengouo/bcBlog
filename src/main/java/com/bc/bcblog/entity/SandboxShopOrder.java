package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 旅人集市的购买记录：谁把哪件商品送给了哪个角色。 */
@Data
@TableName("sandbox_shop_order")
public class SandboxShopOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long worldId;
    private Long itemId;
    private String itemName;
    /** 购买者（仅前台商品弹窗与后台可见，绝不写进角色提示词） */
    private Long userId;
    private String userName;
    /** 收礼角色 */
    private Long characterId;
    private String characterName;
    private Integer quantity;
    /** 消耗积分（管理员为 0） */
    private Integer pointsCost;
    /** 商品单价（金币）：前台用户购买时按汇率折算成积分，角色自购时直接扣金币 */
    private Integer coinPrice;
    /** 购买者类型：user=前台用户赠送 / character=沙盒角色自购 */
    private String buyerType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
