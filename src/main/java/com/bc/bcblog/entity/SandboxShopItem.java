package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅人集市的商品。每一批对应一个 batch_time（刷新时间），前台只展示最新一批。
 * 展示方式与角色背包物品一致：图标 + 品质配色 + 名称 + 价格 + 库存。
 */
@Data
@TableName("sandbox_shop_item")
public class SandboxShopItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long worldId;
    /** 所属批次（刷新时间） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime batchTime;
    private String name;
    private String description;
    /** 自定义图标地址；为空时前端按名字匹配 emoji */
    private String icon;
    /** 品质 1 普通 ~ 5 传说 */
    private Integer rarity;
    /** 现价（积分） */
    private Integer price;
    /** 原价（打折时展示划线价） */
    private Integer originalPrice;
    /** 剩余库存 */
    private Integer stock;
    /** 本批总量 */
    private Integer totalStock;
    /** ai / admin */
    private String source;
    private Integer pinned;
    private Integer enabled;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 该商品的赠送记录（前台点开商品时展示「谁送给了谁」），不落库 */
    @TableField(exist = false)
    private List<SandboxShopOrder> orders;
}
