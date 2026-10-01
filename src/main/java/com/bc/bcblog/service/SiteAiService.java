package com.bc.bcblog.service;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.entity.SiteAiActivity;
import com.bc.bcblog.entity.SiteAiProfile;

import java.util.Map;

/**
 * 网站 AI「IRIS」：档案参数、四个用途的执行、活动日志与撤销、前台主页数据。
 *
 * 她是"特殊站娘"：不在用户体系里（不占等级、积分、注册统计），
 * 内容直接发布但带 AI 标识，并保留敏感词硬拦、一键撤销与举报入口。
 */
public interface SiteAiService {

    /** 档案（不存在时自动建一行默认值） */
    SiteAiProfile profile();

    /** 保存档案参数 */
    SiteAiProfile saveProfile(SiteAiProfile form);

    /** 后台概览：开关、今日调用与各类型计数、最近活动 */
    Map<String, Object> overview();

    /** 立即执行一次：article 写文章 / comment 评论吐槽 / status 状态更新 */
    SiteAiActivity run(String type, boolean manual);

    PageResult<SiteAiActivity> activityPage(String type, long page, long size);

    /** 一键撤销：文章下架 / 评论与回复删除 */
    void revert(Long activityId);

    /** 定时任务入口：按配置的三个时段执行，返回本次真正执行的次数 */
    int autoRun();

    /** 生成当天记忆（供后续几天写作参考） */
    int summarize();

    /** 读者留言后可能触发她回复（异步，不阻塞评论提交） */
    void maybeReplyAsync(Long commentId);

    /** 前台主页：档案 + 当前状态 + 最近活动 + 她写的文章 */
    Map<String, Object> portal();

    /** 把已发布文章的封面统一换成当前设置的封面（返回更新篇数） */
    int applyCoverToExisting();

    /** 生成"今日运行简报"文本（后台可预览她会看到的数字） */
    String digestPreview();

    /** 重置今日调用计数（测试用：只把计数起点推到现在，不删除任何日志） */
    int resetTodayCounters();
}
