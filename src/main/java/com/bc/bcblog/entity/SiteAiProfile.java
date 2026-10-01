package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 网站 AI「IRIS」的档案与参数（单行，id 固定 1）。
 *
 * 前台与提示词统一使用英文名 IRIS；中文名「伊莉丝」只作档案展示（显示为「伊莉丝 IRIS」）。
 * 四个用途各自配服务商与模型：写文章可以上 pro，评论与回复用 flash，状态与记忆用最便宜的即可。
 */
@Data
@TableName("site_ai_profile")
public class SiteAiProfile {
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 总开关：默认关闭，配置好再打开 */
    private Integer enabled;
    private String nameEn;
    private String nameCn;
    /** 型号，展示口径为「IRIS」（不带编号后缀） */
    private String modelNo;
    private String tagline;
    /** 头像 / 立绘地址；留空表示还没上传，前台用占位图 */
    private String avatar;
    private String bio;
    /** 结构化角色档案（JSON） */
    private String personalityJson;
    /** 提示词补充：站长可随时改，不动代码 */
    private String promptExtra;
    private Integer dailyLimit;

    private Integer articleEnabled;
    /** 每天最多写几篇（后台可改） */
    private Integer articleDailyLimit;
    /** 写作窗口：每天的写作时间会落在这个区间内 */
    private String articleWindowStart;
    private String articleWindowEnd;
    /** 写作时间是否随机分布：1 随机（更像真人）/ 0 均匀 */
    private Integer articleRandom;
    /** 当天写作计划 JSON（日期 + 各时段与状态），每天首次执行时自动生成 */
    private String articlePlanJson;
    private String articleTime;
    private Long articleProviderId;
    private String articleModel;
    /** 写文章的选题偏好（后台可改） */
    private String articleTopics;
    /** 写文章的禁忌与边界（后台可改） */
    private String articleAvoid;

    private Integer commentEnabled;
    private String commentTime;
    private Long commentProviderId;
    private String commentModel;
    /** latest 只评论最新文章 / latest+owner 再加上站长（超管）的文章 */
    private String commentScope;

    private String musingTime;

    private Integer replyEnabled;
    private Long replyProviderId;
    private String replyModel;
    private Integer replyCooldownMinutes;
    private Integer replyDailyLimit;

    private Long statusProviderId;
    private String statusModel;

    /** acg 随机封面接口 / fixed 固定图 / none 不要封面 */
    private String coverSource;
    private String coverFixed;
    /** 封面池：每行一个图片地址（coverSource = pool 时按顺序轮换） */
    private String coverPool;
    private Integer memoryDays;
    /** 发布前是否过站内敏感词过滤（关掉就只依赖 AI 服务商自身的判断） */
    private Integer sensitiveFilterEnabled;
    /** 今日调用计数的起点：后台点「重置今日计数」时写入，只影响计数、不删日志 */
    private LocalDateTime countResetAt;
    /** 写文章时是否参考站内运行数据（聚合数字） */
    private Integer digestEnabled;
    /** 数据范围：site 只给本站聚合数字 / site+sandbox 再加上沙盒世界细节 */
    private String digestScope;
    /** 吐槽语气：gentle 克制观察 / spicy 更直接 */
    private String digestTone;
    /** 每天是否写一篇「今日运行情况」 */
    private Integer reportEnabled;
    /** 运行报告时间 HH:mm，默认 23:00 */
    private String reportTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
