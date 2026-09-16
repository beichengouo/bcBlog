package com.bc.bcblog.vo;

import lombok.Data;

/**
 * 前台某个页面的背景（给前台渲染用，也给后台管理页做回显）。
 * url / type 为「实际生效」的那张壁纸；mode = none 时 url 为 null，前台就只显示主题渐变。
 */
@Data
public class PageBackgroundVO {
    /** 页面标识：home / photos / resources / sandbox */
    private String pageKey;
    /** 页面名称（后台展示用） */
    private String label;
    /** follow / none / custom */
    private String mode;
    /** mode = custom 时选中的壁纸 id（后台回显用） */
    private Long backgroundId;
    /** 实际生效的壁纸地址，可能来自「前台默认壁纸」 */
    private String url;
    /** image / video */
    private String type;
    /** 壁纸不透明度 0.10~1.00 */
    private Double opacity;
}
