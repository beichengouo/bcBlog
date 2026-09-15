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
}
