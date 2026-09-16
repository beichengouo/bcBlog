package com.bc.bcblog.service;

import com.bc.bcblog.entity.Background;
import com.bc.bcblog.vo.PageBackgroundVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BackgroundService {
    List<Background> list();
    Background upload(MultipartFile file);
    void delete(Long id);
    void setActive(Long id, String scope);
    Background activeByScope(String scope);
    void clearActive(String scope);

    /** 前台各页面的背景设置（后台管理页用，含实际生效的壁纸地址） */
    List<PageBackgroundVO> pageSettings();

    /** 前台某个页面的实际背景：mode=follow 时回落到前台默认壁纸；mode=none 时 url 为 null */
    PageBackgroundVO pageBackground(String pageKey);

    /** 渲染用的实际背景：scope=admin 走「后台使用中」的壁纸，scope=portal 支持按页面独立设置 */
    PageBackgroundVO resolve(String scope, String pageKey);

    /** 保存某个页面的背景设置（mode：follow / none / custom） */
    void savePageSetting(String pageKey, String mode, Long backgroundId, Double opacity);
}
