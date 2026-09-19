package com.bc.bcblog.vo;

import com.bc.bcblog.entity.SandboxLocation;
import com.bc.bcblog.entity.SandboxNews;
import com.bc.bcblog.entity.SandboxWorld;
import lombok.Data;

import java.util.List;

/** 前台沙盒首页聚合数据。 */
@Data
public class SandboxPortalVO {
    /** 沙盒 AI 调用是否开启（关闭时前台只展示已有内容） */
    private boolean enabled;
    /** 旅人低语是否开启（关闭时前台隐藏入口，接口也会拦截） */
    private boolean whisperEnabled;
    /** 每次旅人低语消耗的积分 */
    private int whisperPoints;
    /** 1 积分可兑换的金币数量 */
    private int coinRate;
    private SandboxWorld world;
    private List<SandboxLocation> locations;
    private List<SandboxCharacterVO> characters;
    /** 旅人纪闻栏目名称（后台可配置） */
    private String newsTitle;
    /** 当天纪闻 */
    private List<SandboxNews> news;
    /** 旅人集市栏目名（后台可改） */
    private String shopTitle;
    /** 集市总开关 */
    private boolean shopEnabled;
    /** 角色每天最多自购几件（0 = 不限制），前台用来提示玩家 */
    private int shopBuyPerDay;
    /** 地图宽度（km）：前台地图 hover 与行动时间线用它把坐标差换算成实际距离 */
    private int kmMapWidth;
    /** 当前世界最新一批商品 */
    private List<com.bc.bcblog.entity.SandboxShopItem> shopItems;
    /** 旅人委托板栏目名（后台可改） */
    private String questTitle;
    /** 委托板总开关（关闭时前台整块隐藏，角色也不再接取） */
    private boolean questEnabled;
    /** 委托板：最新一批可接 + 所有接取中 + 近三天已完成（标「已被 XX 完成」） */
    private List<com.bc.bcblog.entity.SandboxQuest> quests;
}
