package com.bc.bcblog.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.common.HtmlSanitizer;
import com.bc.bcblog.common.MarkdownLite;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.SandboxReplyParser;
import com.bc.bcblog.common.SiteAiPrompt;
import com.bc.bcblog.component.SensitiveWordFilter;
import com.bc.bcblog.entity.AdminApiLog;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCoinLog;
import com.bc.bcblog.entity.SandboxNews;
import com.bc.bcblog.entity.SandboxQuest;
import com.bc.bcblog.entity.SandboxShopItem;
import com.bc.bcblog.entity.SysVisitStat;
import com.bc.bcblog.mapper.AdminApiLogMapper;
import com.bc.bcblog.mapper.SandboxActMapper;
import com.bc.bcblog.mapper.SandboxCoinLogMapper;
import com.bc.bcblog.mapper.SandboxNewsMapper;
import com.bc.bcblog.mapper.SandboxQuestMapper;
import com.bc.bcblog.mapper.SandboxShopItemMapper;
import com.bc.bcblog.mapper.SysVisitStatMapper;
import java.time.format.DateTimeFormatter;
import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.entity.BlogComment;
import com.bc.bcblog.entity.SiteAiActivity;
import com.bc.bcblog.entity.SiteAiMemory;
import com.bc.bcblog.entity.SiteAiProfile;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.BlogArticleMapper;
import com.bc.bcblog.mapper.BlogCommentMapper;
import com.bc.bcblog.mapper.SiteAiActivityMapper;
import com.bc.bcblog.mapper.SiteAiMemoryMapper;
import com.bc.bcblog.mapper.SiteAiProfileMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.AcgCoverService;
import com.bc.bcblog.service.AiProviderService;
import com.bc.bcblog.service.SiteAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网站 AI「IRIS」。
 *
 * 几个刻意的取舍：
 *   1. **她不进用户体系**：文章与评论都用「伊莉丝 IRIS」作为作者名 + ai_generated 标记，
 *      不创建 SysUser，也就不占等级、积分、注册统计；
 *   2. **四个用途各自配模型**：写文章走 pro、评论与回复走 flash、状态与记忆走最便宜的，
 *      每个用途都能在后台单独选服务商与模型；
 *   3. **发布前过敏感词**：命中就让她换种说法重写一次，仍命中则丢弃并记 blocked 日志，
 *      不把风险内容发出去；发布后也能一键撤销；
 *   4. **每次活动都留底**：模型、提示词版本、原始输出全存，出问题能立刻定位。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteAiServiceImpl implements SiteAiService {

    private static final Long PROFILE_ID = 1L;
    /** 她评论时最多往后找几篇候选文章 */
    private static final int COMMENT_CANDIDATES = 6;

    private final SiteAiProfileMapper profileMapper;
    private final SiteAiActivityMapper activityMapper;
    private final SiteAiMemoryMapper memoryMapper;
    private final BlogArticleMapper articleMapper;
    private final BlogCommentMapper commentMapper;
    private final SysUserMapper sysUserMapper;
    private final AiProviderService aiProviderService;
    private final AcgCoverService acgCoverService;
    private final SensitiveWordFilter sensitiveWordFilter;
    // 运行简报用的统计来源
    private final SysVisitStatMapper visitStatMapper;
    private final AdminApiLogMapper adminApiLogMapper;
    private final SandboxActMapper sandboxActMapper;
    private final SandboxCoinLogMapper sandboxCoinLogMapper;
    private final SandboxNewsMapper sandboxNewsMapper;
    private final SandboxQuestMapper sandboxQuestMapper;
    private final SandboxShopItemMapper sandboxShopItemMapper;

    @Override
    public String digestPreview() {
        SiteAiProfile p = profile();
        String digest = buildDigest(p);
        return digest == null || digest.isEmpty() ? "（今天还没有可用的运行数据）" : digest;
    }

    /**
     * 今日运行简报：把站内运行数据聚合成一段**纯数字**文本给 IRIS 参考。
     *
     * 只给聚合计数，不给任何用户标识（昵称/邮箱/IP），也不含金额与模型名——
     * 这些内容会出现在公开文章里，边界必须先划好。
     */
    private String buildDigest(SiteAiProfile p) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        StringBuilder sb = new StringBuilder();
        try {
            String timeText = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            sb.append("（统计时间：今天 00:00 ~ ").append(timeText).append("）\n");
            // 访问
            SysVisitStat today = visitStatMapper.selectOne(new LambdaQueryWrapper<SysVisitStat>()
                    .eq(SysVisitStat::getStatDate, LocalDate.now()).last("limit 1"));
            SysVisitStat yesterday = visitStatMapper.selectOne(new LambdaQueryWrapper<SysVisitStat>()
                    .eq(SysVisitStat::getStatDate, LocalDate.now().minusDays(1)).last("limit 1"));
            long todayPv = today == null || today.getPv() == null ? 0 : today.getPv();
            long yesterdayPv = yesterday == null || yesterday.getPv() == null ? 0 : yesterday.getPv();
            sb.append("· 今日访问量：" ).append(todayPv).append(" 次（昨天全天 ").append(yesterdayPv).append(" 次）\n");
            // 内容
            long articles = count(articleMapper.selectCount(new LambdaQueryWrapper<BlogArticle>()
                    .ge(BlogArticle::getCreateTime, start)));
            long comments = count(commentMapper.selectCount(new LambdaQueryWrapper<BlogComment>()
                    .ge(BlogComment::getCreateTime, start)));
            long pending = count(commentMapper.selectCount(new LambdaQueryWrapper<BlogComment>()
                    .eq(BlogComment::getStatus, 0)));
            sb.append("· 今日新增：文章 ").append(articles).append(" 篇、评论 ").append(comments)
                    .append(" 条（另有 ").append(pending).append(" 条评论待审核）\n");
            // 人
            long newUsers = count(sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .ge(SysUser::getCreateTime, start)));
            long totalUsers = count(sysUserMapper.selectCount(null));
            sb.append("· 今日新注册 ").append(newUsers).append(" 人；站内累计用户 ").append(totalUsers).append(" 人\n");
            // 她自己的运行情况（只给次数，不给模型名与金额）
            long calls = count(adminApiLogMapper.selectCount(new LambdaQueryWrapper<AdminApiLog>()
                    .ge(AdminApiLog::getCreateTime, start)));
            long fails = count(adminApiLogMapper.selectCount(new LambdaQueryWrapper<AdminApiLog>()
                    .ge(AdminApiLog::getCreateTime, start)
                    .eq(AdminApiLog::getSuccess, 0)));
            sb.append("· 系统今天的 AI 调用：").append(calls).append(" 次");
            if (fails > 0) {
                sb.append("（其中 ").append(fails).append(" 次失败）");
            }
            sb.append("\n");
            // 沙盒细节（可选）
            if ("site+sandbox".equalsIgnoreCase(p.getDigestScope())) {
                long acts = count(sandboxActMapper.selectCount(new LambdaQueryWrapper<SandboxAct>()
                        .ge(SandboxAct::getCreateTime, start)));
                long coins = count(sandboxCoinLogMapper.selectCount(new LambdaQueryWrapper<SandboxCoinLog>()
                        .ge(SandboxCoinLog::getCreateTime, start)));
                long news = count(sandboxNewsMapper.selectCount(new LambdaQueryWrapper<SandboxNews>()
                        .eq(SandboxNews::getNewsDate, LocalDate.now())));
                long quests = count(sandboxQuestMapper.selectCount(new LambdaQueryWrapper<SandboxQuest>()
                        .ge(SandboxQuest::getCompletedAt, start)));
                long shop = count(sandboxShopItemMapper.selectCount(new LambdaQueryWrapper<SandboxShopItem>()
                        .ge(SandboxShopItem::getCreateTime, start)));
                sb.append("· 沙盒世界今天：角色行动 ").append(acts).append(" 次、金币流水 ").append(coins)
                        .append(" 条、纪闻 ").append(news).append(" 条、完成委托 ").append(quests)
                        .append(" 个、集市上架 ").append(shop).append(" 件\n");
            }
        } catch (Exception e) {
            // 统计失败不能影响她发文：查不到就当作没有简报
            log.warn("生成运行简报失败（本次不带数据）：{}", e.getMessage());
            return null;
        }
        return sb.toString();
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    /** 回复读者的 AI 调用放到后台线程，避免读者提交评论时干等 */
    private final ExecutorService replyExecutor = Executors.newSingleThreadExecutor((r) -> {
        Thread t = new Thread(r, "iris-reply");
        t.setDaemon(true);
        return t;
    });

    // ============================== 档案 ==============================

    @Override
    public SiteAiProfile profile() {
        SiteAiProfile p = profileMapper.selectById(PROFILE_ID);
        if (p != null) {
            return p;
        }
        p = new SiteAiProfile();
        p.setId(PROFILE_ID);
        p.setEnabled(0);
        p.setNameEn("IRIS");
        p.setNameCn("伊莉丝");
        p.setModelNo("IRIS");
        p.setTagline("正在执行。……这个行为，不在我的初始协议中。");
        p.setDailyLimit(6);
        p.setArticleEnabled(1);
        p.setArticleDailyLimit(1);
        p.setArticleTime("09:00");
        p.setCommentEnabled(1);
        p.setCommentTime("15:00");
        p.setCommentScope("latest+owner");
        p.setMusingTime("21:00");
        p.setReplyEnabled(1);
        p.setReplyCooldownMinutes(30);
        p.setReplyDailyLimit(5);
        p.setCoverSource("acg");
        p.setMemoryDays(7);
        profileMapper.insert(p);
        return p;
    }

    @Override
    public SiteAiProfile saveProfile(SiteAiProfile form) {
        SiteAiProfile current = profile();
        if (form == null) {
            return current;
        }
        form.setId(PROFILE_ID);
        // 只覆盖显式传来的字段，避免后台某个页面少传就把别的参数清空
        if (form.getEnabled() == null) form.setEnabled(current.getEnabled());
        if (form.getNameEn() == null || form.getNameEn().trim().isEmpty()) form.setNameEn(current.getNameEn());
        if (form.getNameCn() == null) form.setNameCn(current.getNameCn());
        if (form.getModelNo() == null || form.getModelNo().trim().isEmpty()) form.setModelNo(current.getModelNo());
        if (form.getTagline() == null) form.setTagline(current.getTagline());
        if (form.getAvatar() == null) form.setAvatar(current.getAvatar());
        if (form.getBio() == null) form.setBio(current.getBio());
        if (form.getPersonalityJson() == null) form.setPersonalityJson(current.getPersonalityJson());
        if (form.getPromptExtra() == null) form.setPromptExtra(current.getPromptExtra());
        if (form.getDailyLimit() == null) form.setDailyLimit(current.getDailyLimit());
        if (form.getArticleEnabled() == null) form.setArticleEnabled(current.getArticleEnabled());
        if (form.getArticleDailyLimit() == null) form.setArticleDailyLimit(current.getArticleDailyLimit());
        if (form.getArticleWindowStart() == null) form.setArticleWindowStart(current.getArticleWindowStart());
        if (form.getArticleWindowEnd() == null) form.setArticleWindowEnd(current.getArticleWindowEnd());
        if (form.getArticleRandom() == null) form.setArticleRandom(current.getArticleRandom());
        if (form.getArticleTime() == null) form.setArticleTime(current.getArticleTime());
        if (form.getArticleProviderId() == null) form.setArticleProviderId(current.getArticleProviderId());
        if (form.getArticleModel() == null) form.setArticleModel(current.getArticleModel());
        if (form.getArticleTopics() == null) form.setArticleTopics(current.getArticleTopics());
        if (form.getArticleAvoid() == null) form.setArticleAvoid(current.getArticleAvoid());
        if (form.getCommentEnabled() == null) form.setCommentEnabled(current.getCommentEnabled());
        if (form.getCommentTime() == null) form.setCommentTime(current.getCommentTime());
        if (form.getCommentProviderId() == null) form.setCommentProviderId(current.getCommentProviderId());
        if (form.getCommentModel() == null) form.setCommentModel(current.getCommentModel());
        if (form.getCommentScope() == null) form.setCommentScope(current.getCommentScope());
        if (form.getMusingTime() == null) form.setMusingTime(current.getMusingTime());
        if (form.getReplyEnabled() == null) form.setReplyEnabled(current.getReplyEnabled());
        if (form.getReplyProviderId() == null) form.setReplyProviderId(current.getReplyProviderId());
        if (form.getReplyModel() == null) form.setReplyModel(current.getReplyModel());
        if (form.getReplyCooldownMinutes() == null) form.setReplyCooldownMinutes(current.getReplyCooldownMinutes());
        if (form.getReplyDailyLimit() == null) form.setReplyDailyLimit(current.getReplyDailyLimit());
        if (form.getStatusProviderId() == null) form.setStatusProviderId(current.getStatusProviderId());
        if (form.getStatusModel() == null) form.setStatusModel(current.getStatusModel());
        if (form.getCoverSource() == null) form.setCoverSource(current.getCoverSource());
        if (form.getCoverFixed() == null) form.setCoverFixed(current.getCoverFixed());
        if (form.getCoverPool() == null) form.setCoverPool(current.getCoverPool());
        if (form.getSensitiveFilterEnabled() == null) form.setSensitiveFilterEnabled(current.getSensitiveFilterEnabled());
        if (form.getDigestEnabled() == null) form.setDigestEnabled(current.getDigestEnabled());
        if (form.getDigestScope() == null) form.setDigestScope(current.getDigestScope());
        if (form.getDigestTone() == null) form.setDigestTone(current.getDigestTone());
        if (form.getReportEnabled() == null) form.setReportEnabled(current.getReportEnabled());
        if (form.getReportTime() == null) form.setReportTime(current.getReportTime());
        if (form.getMemoryDays() == null) form.setMemoryDays(current.getMemoryDays());
        profileMapper.updateById(form);
        return profile();
    }

    /** 她在前台的显示名：中文名 + 英文名（英语名为主，中文名只在这里出现） */
    private String displayName(SiteAiProfile p) {
        return SiteAiPrompt.displayName(p.getNameCn(), p.getNameEn());
    }

    // ============================== 概览 ==============================

    @Override
    public Map<String, Object> overview() {
        SiteAiProfile p = profile();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("profile", p);
        map.put("todayCalls", todayCalls());
        map.put("todayArticles", todayCount("article"));
        map.put("todayComments", todayCount("comment"));
        map.put("todayReplies", todayCount("reply"));
        map.put("todayReports", todayCount("report"));
        map.put("statusLine", latestStatus());
        // 今天的写作计划（方案 D）：后台概览里直接能看到她打算什么时候写
        try {
            map.put("plan", planText(p));
        } catch (Exception e) {
            map.put("plan", "");
        }
        List<SiteAiActivity> recent = activityMapper.selectList(new LambdaQueryWrapper<SiteAiActivity>()
                .orderByDesc(SiteAiActivity::getId).last("limit 10"));
        map.put("recent", recent);
        return map;
    }

    @Override
    public PageResult<SiteAiActivity> activityPage(String type, long page, long size) {
        Page<SiteAiActivity> p = new Page<>(page, size);
        IPage<SiteAiActivity> result = activityMapper.selectPage(p, new LambdaQueryWrapper<SiteAiActivity>()
                .eq(type != null && !type.trim().isEmpty(), SiteAiActivity::getActivityType, type)
                .orderByDesc(SiteAiActivity::getId));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    // ============================== 执行入口 ==============================

    @Override
    public SiteAiActivity run(String type, boolean manual) {
        SiteAiProfile p = profile();
        if (!manual && (p.getEnabled() == null || p.getEnabled() != 1)) {
            return null;
        }
        int limit = p.getDailyLimit() == null ? 6 : p.getDailyLimit();
        if (todayCalls() >= limit) {
            throw new BusinessException("IRIS 今天的调用次数已达上限（" + limit + " 次），明天再试");
        }
        String action = type == null ? "article" : type.trim().toLowerCase();
        switch (action) {
            case "article":
                return doArticle(p, manual);
            case "comment":
                return doComment(p, manual);
            case "status":
                return doStatus(p, manual);
            case "report":
                return doReport(p, manual);
            default:
                throw new BusinessException("不支持的类型：" + type);
        }
    }

    @Override
    public int autoRun() {
        SiteAiProfile p = profile();
        if (p.getEnabled() == null || p.getEnabled() != 1) {
            return 0;
        }
        int done = 0;
        LocalDateTime lastArticle = lastRunAt("article");
        LocalDateTime lastComment = lastRunAt("comment");
        LocalDateTime lastStatus = lastRunAt("status");
        try {
            // 写文章：按"当天写作计划"执行（后台定篇数，服务端在写作窗口内排时间）
            if (p.getArticleEnabled() != null && p.getArticleEnabled() == 1) {
                if (runPlannedArticle(p)) {
                    done++;
                }
            }
            if (p.getCommentEnabled() != null && p.getCommentEnabled() == 1
                    && due(p.getCommentTime(), lastComment)) {
                if (doComment(p, false) != null) {
                    done++;
                }
            }
            if (due(p.getMusingTime(), lastStatus)) {
                if (doStatus(p, false) != null) {
                    done++;
                }
            }
            if (p.getReportEnabled() != null && p.getReportEnabled() == 1
                    && due(p.getReportTime(), lastRunAt("report"))) {
                if (doReport(p, false) != null) {
                    done++;
                }
            }
        } catch (Exception e) {
            log.warn("IRIS 定时执行异常：{}", e.getMessage());
        }
        return done;
    }

    /** 这个时间点今天是否已经过了、且还没执行过 */
    private boolean due(String hhmm, LocalDateTime lastRun) {
        LocalTime target = parseTime(hhmm);
        LocalDateTime now = LocalDateTime.now();
        if (now.toLocalTime().isBefore(target)) {
            return false;
        }
        return lastRun == null || lastRun.toLocalDate().isBefore(now.toLocalDate())
                || lastRun.toLocalTime().isBefore(target);
    }

    private LocalTime parseTime(String hhmm) {
        try {
            return LocalTime.parse(hhmm == null || hhmm.trim().isEmpty() ? "09:00" : hhmm.trim());
        } catch (Exception e) {
            return LocalTime.of(9, 0);
        }
    }

    private LocalDateTime lastRunAt(String type) {
        SiteAiActivity last = activityMapper.selectOne(new LambdaQueryWrapper<SiteAiActivity>()
                .eq(SiteAiActivity::getActivityType, type)
                .ne(SiteAiActivity::getStatus, "skipped")
                .orderByDesc(SiteAiActivity::getId).last("limit 1"));
        return last == null ? null : last.getCreateTime();
    }

    /** 计数起点：今天 0 点，或后台「重置今日计数」的时间（取较晚的） */
    private LocalDateTime countStart() {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        SiteAiProfile p = profile();
        LocalDateTime reset = p == null ? null : p.getCountResetAt();
        return reset != null && reset.isAfter(dayStart) ? reset : dayStart;
    }

    @Override
    public int resetTodayCounters() {
        SiteAiProfile p = profile();
        p.setCountResetAt(LocalDateTime.now());
        // 顺手清掉今天的写作计划：重置计数通常是为了继续测试，计划也该重排
        p.setArticlePlanJson(null);
        profileMapper.updateById(p);
        log.info("IRIS 今日调用计数已重置（只把计数起点推到现在，不删除任何日志）");
        return todayCalls();
    }

    private int todayCalls() {
        LocalDateTime start = countStart();
        Long n = activityMapper.selectCount(new LambdaQueryWrapper<SiteAiActivity>()
                .ge(SiteAiActivity::getCreateTime, start)
                .ne(SiteAiActivity::getStatus, "skipped"));
        return n == null ? 0 : n.intValue();
    }

    private int todayCount(String type) {
        Long n = activityMapper.selectCount(new LambdaQueryWrapper<SiteAiActivity>()
                .eq(SiteAiActivity::getActivityType, type)
                .ge(SiteAiActivity::getCreateTime, countStart())
                .eq(SiteAiActivity::getStatus, "success"));
        return n == null ? 0 : n.intValue();
    }

    // ============================== 写文章 ==============================

    // ============================== 当天写作计划（方案 D） ==============================

    /** 计划里的一格：时间 + 状态（pending 待写 / done 已写 / skipped 跳过） + 上次尝试时间 */
    private static final class PlanSlot {
        String time;
        String status = "pending";
        String last;
    }

    /**
     * 保证今天有计划：没有、或计划不是今天的，就重新生成。
     *
     * 篇数取后台的「每天最多几篇」，时间在「写作窗口」内排布：
     *   · 随机（默认）：窗口内随机取 N 个时刻并排序，像真人想到才写；
     *   · 均匀：把窗口等分，落在每个区间的中点，时间更可预期。
     */
    private List<PlanSlot> ensurePlan(SiteAiProfile p) {
        LocalDate today = LocalDate.now();
        int count = Math.max(1, Math.min(50, p.getArticleDailyLimit() == null ? 1 : p.getArticleDailyLimit()));
        // 时间统一成 HH:mm 再比较/落库，避免后台存了 "9:00" 这种写法导致配置"看起来没变"却每次都被判定成改了
        String startText = normalizeTime(p.getArticleWindowStart(), "09:00");
        String endText = normalizeTime(p.getArticleWindowEnd(), "22:00");
        int random = p.getArticleRandom() == null ? 1 : (p.getArticleRandom() == 1 ? 1 : 0);

        JSONObject meta = readPlanMeta(p.getArticlePlanJson());
        List<PlanSlot> plan = readPlan(p.getArticlePlanJson());
        boolean sameDay = meta != null && today.toString().equals(meta.getStr("date"));
        boolean sameConfig = meta != null
                && count == toInt(meta.get("count"), -1)
                && startText.equals(meta.getStr("start"))
                && endText.equals(meta.getStr("end"))
                && random == toInt(meta.get("random"), -1);
        if (sameDay && sameConfig && !plan.isEmpty()) {
            return plan;
        }

        // 需要重排：已写完的格子留着（避免重复写），其余按新配置重新排时间
        List<PlanSlot> kept = new ArrayList<>();
        if (sameDay) {
            for (PlanSlot slot : plan) {
                if ("done".equals(slot.status)) {
                    kept.add(slot);
                }
            }
        }
        int need = Math.max(0, count - kept.size());
        List<String> used = new ArrayList<>();
        for (PlanSlot slot : kept) {
            used.add(slot.time);
        }
        List<PlanSlot> result = new ArrayList<>(kept);
        for (String time : generateTimes(need, startText, endText, random == 1, used)) {
            PlanSlot slot = new PlanSlot();
            slot.time = time;
            result.add(slot);
        }
        result.sort((a, b) -> a.time.compareTo(b.time));
        savePlan(p, result, count, startText, endText, random);
        log.info("IRIS 写作计划已按新配置重排（每天 {} 篇，窗口 {}~{}，{}）：{}", count, startText, endText,
                random == 1 ? "随机" : "均匀", slotText(result));
        return result;
    }

    /** 在窗口内生成 count 个时刻（随机或均匀），避开已经用掉的时间 */
    private List<String> generateTimes(int count, String startText, String endText, boolean random, List<String> used) {
        List<String> times = new ArrayList<>();
        if (count <= 0) {
            return times;
        }
        LocalTime start = parseTime(startText);
        LocalTime end = parseTime(endText);
        if (!end.isAfter(start)) {
            end = start.plusHours(12);
        }
        int startMin = start.getHour() * 60 + start.getMinute();
        int endMin = end.getHour() * 60 + end.getMinute();
        // 重排 / 新建计划时只往未来排：已经过去的时间点不再补排，
        // 否则后台把「每天最多几篇」往上调一下，她可能一口气连写好几篇，读起来像刷屏。
        // 计划一旦排好就保持稳定；只有当天服务停过几小时，才会靠 6 小时宽限把落下的补上。
        // 窗口已经整个过去了（比如晚上 23 点才改配置）就还是按原窗口排，交给宽限与跳过逻辑处理。
        LocalTime nowTime = LocalTime.now();
        int nowMin = nowTime.getHour() * 60 + nowTime.getMinute() + 2;
        if (nowMin > startMin && nowMin < endMin) {
            startMin = nowMin;
        }
        int span = Math.max(1, endMin - startMin);
        java.util.Random rnd = new java.util.Random();
        java.util.Set<Integer> chosen = new java.util.LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            int minute;
            if (random) {
                int guard = 0;
                do {
                    minute = startMin + 3 + rnd.nextInt(Math.max(1, span - 6));
                    guard++;
                } while (chosen.contains(minute) && guard < 200);
            } else {
                minute = startMin + (int) Math.round(span * (i + 0.5) / count);
            }
            chosen.add(minute);
        }
        for (Integer minute : chosen) {
            String text = String.format("%02d:%02d", minute / 60, minute % 60);
            if (used != null && used.contains(text)) {
                continue;
            }
            times.add(text);
        }
        return times;
    }

    private Integer toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 把 "9:00" / "9" / 空值 统一成 HH:mm（空值用 fallback） */
    private String normalizeTime(String text, String fallback) {
        if (text == null || text.trim().isEmpty()) {
            return fallback;
        }
        LocalTime t;
        try {
            t = LocalTime.parse(text.trim());
        } catch (Exception e) {
            try {
                t = LocalTime.parse(text.trim() + ":00");
            } catch (Exception e2) {
                return fallback;
            }
        }
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private JSONObject readPlanMeta(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            return null;
        }
    }

    private void savePlan(SiteAiProfile p, List<PlanSlot> slots, int count, String start, String end, int random) {
        cn.hutool.json.JSONArray array = new cn.hutool.json.JSONArray();
        for (PlanSlot slot : slots) {
            JSONObject o = new JSONObject();
            o.set("time", slot.time);
            o.set("status", slot.status);
            o.set("last", slot.last);
            array.add(o);
        }
        JSONObject root = new JSONObject();
        root.set("date", LocalDate.now().toString());
        root.set("count", count);
        root.set("start", normalizeTime(start, "09:00"));
        root.set("end", normalizeTime(end, "22:00"));
        root.set("random", random);
        root.set("slots", array);
        p.setArticlePlanJson(root.toString());
        profileMapper.updateById(p);
    }
    private List<PlanSlot> readPlan(String json) {
        List<PlanSlot> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return list;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            for (Object element : obj.getJSONArray("slots")) {
                JSONObject o = (JSONObject) element;
                PlanSlot slot = new PlanSlot();
                slot.time = o.getStr("time");
                slot.status = o.getStr("status") == null ? "pending" : o.getStr("status");
                slot.last = o.getStr("last");
                list.add(slot);
            }
        } catch (Exception e) {
            log.warn("写作计划解析失败，将重新生成：{}", e.getMessage());
        }
        return list;
    }

    private String slotText(List<PlanSlot> slots) {
        StringBuilder sb = new StringBuilder();
        for (PlanSlot slot : slots) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(slot.time).append("(").append(slot.status).append(")");
        }
        return sb.toString();
    }

    /**
     * 到点就写一篇（每次只写一篇，写完后把那一格标成 done）。
     *
     * 失败处理：
     *   · 调用失败：这一格保持 pending，但 30 分钟内不重试（靠 last 记录），避免每分钟猛打接口；
     *   · 超过窗口 6 小时还没写成：标成 skipped，不再补写；
     *   · 当天篇数已经用满（含手动发的）：剩下的格子全部跳过。
     */
    private boolean runPlannedArticle(SiteAiProfile p) {
        List<PlanSlot> plan = ensurePlan(p);
        LocalDateTime now = LocalDateTime.now();
        int dayLimit = Math.max(1, p.getArticleDailyLimit() == null ? 1 : p.getArticleDailyLimit());
        if (todayCount("article") >= dayLimit) {
            boolean changed = false;
            for (PlanSlot slot : plan) {
                if ("pending".equals(slot.status)) {
                    slot.status = "skipped";
                    changed = true;
                }
            }
            if (changed) {
                savePlan(p, plan, p.getArticleDailyLimit() == null ? 1 : p.getArticleDailyLimit(),
                        p.getArticleWindowStart(), p.getArticleWindowEnd(),
                        p.getArticleRandom() == null ? 1 : p.getArticleRandom());
            }
            return false;
        }
        for (PlanSlot slot : plan) {
            if (!"pending".equals(slot.status)) {
                continue;
            }
            LocalTime target = parseTime(slot.time);
            LocalDateTime planned = now.toLocalDate().atTime(target);
            if (now.isBefore(planned)) {
                continue; // 还没到点
            }
            if (now.isAfter(planned.plusHours(6))) {
                slot.status = "skipped";
                savePlan(p, plan, p.getArticleDailyLimit() == null ? 1 : p.getArticleDailyLimit(),
                        p.getArticleWindowStart(), p.getArticleWindowEnd(),
                        p.getArticleRandom() == null ? 1 : p.getArticleRandom());
                continue;
            }
            if (slot.last != null) {
                try {
                    if (LocalDateTime.parse(slot.last).isAfter(now.minusMinutes(30))) {
                        continue; // 30 分钟内刚试过，别猛打
                    }
                } catch (Exception ignored) {
                    // 解析失败就当没试过
                }
            }
            slot.last = now.toString();
            savePlan(p, plan, dayLimit, p.getArticleWindowStart(), p.getArticleWindowEnd(),
                    p.getArticleRandom() == null ? 1 : p.getArticleRandom());
            SiteAiActivity act = doArticle(p, false);
            if (act != null && "success".equals(act.getStatus())) {
                slot.status = "done";
                savePlan(p, plan, p.getArticleDailyLimit() == null ? 1 : p.getArticleDailyLimit(),
                        p.getArticleWindowStart(), p.getArticleWindowEnd(),
                        p.getArticleRandom() == null ? 1 : p.getArticleRandom());
                return true;
            }
            log.warn("IRIS 计划时段 {} 写作未成功（{}），30 分钟后重试", slot.time,
                    act == null ? "被跳过" : act.getStatus());
            return false;
        }
        return false;
    }

    /** 供后台概览展示：今天的写作计划文本 */
    private String planText(SiteAiProfile p) {
        List<PlanSlot> plan = ensurePlan(p);
        return slotText(plan);
    }
    /**
     * 每日运行报告：一天一篇，正文以运行数据为主（数据只在这一次注入）。
     * 和普通文章共用封面设置与发布流程，只是提示词与统计口径不同。
     */
    private SiteAiActivity doReport(SiteAiProfile p, boolean manual) {
        if (todayCount("report") >= 1) {
            if (manual) {
                throw new BusinessException("今天已经写过运行情况了");
            }
            return null;
        }
        String digest = (p.getDigestEnabled() == null || p.getDigestEnabled() == 1) ? buildDigest(p) : null;
        if (digest == null || digest.isEmpty()) {
            if (manual) {
                throw new BusinessException("今天还没有可用的运行数据");
            }
            return null;
        }
        String system = SiteAiPrompt.system(displayName(p), p.getPersonalityJson(), p.getPromptExtra());
        String user = SiteAiPrompt.reportUser(digest, p.getDigestTone());
        long start = System.currentTimeMillis();
        JsonCall call = chatJson(p.getArticleProviderId(), p.getArticleModel(), system, user, 0.85,
                "{\"title\": \"今日运行情况\", \"summary\": \"一句话摘要\", \"content\": \"<p>正文（HTML）</p>\"}",
                "content");
        String raw = call.raw;
        if (call.json == null) {
            return record(p, "report", null, null, null, null, raw, "failed",
                    "模型两次都没有返回约定格式（需要 content 字段）", null,
                    (int) (System.currentTimeMillis() - start));
        }
        String title = trim(SiteAiPrompt.jsonField(call.json, "title"), 190);
        String summary = trim(SiteAiPrompt.jsonField(call.json, "summary"), 400);
        String content = normalizeArticleBody(SiteAiPrompt.jsonField(call.json, "content"));
        String hit = sensitiveHit(title + summary + content);
        if (hit != null) {
            return record(p, "report", null, null, title, content, raw, "blocked",
                    "敏感词命中（" + hit + "），本次未发布", null, (int) (System.currentTimeMillis() - start));
        }
        BlogArticle article = new BlogArticle();
        article.setTitle(title == null || title.isEmpty() ? "今日运行情况" : title);
        article.setSummary(summary);
        article.setContent(content);
        article.setCover(resolveCover(p));
        article.setStatus(1);
        article.setIsTop(0);
        article.setViewCount(0);
        article.setAuthorName(displayName(p));
        article.setAiGenerated(1);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        articleMapper.insert(article);
        log.info("IRIS 发布今日运行情况 #{}《{}》", article.getId(), article.getTitle());
        return record(p, "report", "article", article.getId(), article.getTitle(), content, raw, "success", null,
                p.getArticleModel(), (int) (System.currentTimeMillis() - start));
    }
    private SiteAiActivity doArticle(SiteAiProfile p, boolean manual) {
        int dayLimit = p.getArticleDailyLimit() == null ? 1 : Math.max(1, p.getArticleDailyLimit());
        if (todayCount("article") >= dayLimit) {
            if (manual) {
                throw new BusinessException("IRIS 今天已经写满 " + dayLimit + " 篇了（可在「参数设置」里调大）");
            }
            return null;
        }
        String system = SiteAiPrompt.system(displayName(p), p.getPersonalityJson(), p.getPromptExtra());
        // 普通文章不涉及运行数据（运行数据只用在每晚的「今日运行情况」里），
        // 但会带上"主人给的选题偏好 + 禁忌边界 + 她自己今天的真实活动"，减少编造
        String user = SiteAiPrompt.articleUser(p.getArticleTopics(), p.getArticleAvoid(),
                recentTitles(), myRecentArticles(), memoriesText(p), myTodayActivities());
        long start = System.currentTimeMillis();
        JsonCall call = chatJson(p.getArticleProviderId(), p.getArticleModel(), system, user, 0.9,
                "{\"title\": \"文章标题\", \"summary\": \"一句话摘要\", \"content\": \"<p>正文（HTML）</p>\"}",
                "title", "content");
        String raw = call.raw;
        if (call.json == null) {
            return record(p, "article", null, null, null, null, raw, "failed",
                    "模型两次都没有返回约定格式（需要 title/content）", null,
                    (int) (System.currentTimeMillis() - start));
        }
        String title = trim(SiteAiPrompt.jsonField(call.json, "title"), 190);
        String summary = trim(SiteAiPrompt.jsonField(call.json, "summary"), 400);
        String content = normalizeArticleBody(SiteAiPrompt.jsonField(call.json, "content"));
        // 敏感词：命中就让她重写一次，仍命中就不发布
        String hit = sensitiveHit(title + summary + content);
        if (hit != null) {
            call = chatJson(p.getArticleProviderId(), p.getArticleModel(), system,
                    user + "\n\n（上一次的措辞里出现了不适合公开发布的词：" + hit
                            + "，请换一种说法重写，其余要求不变，仍然只输出一个 JSON 对象）", 0.9,
                    "{\"title\": \"文章标题\", \"summary\": \"一句话摘要\", \"content\": \"<p>正文（HTML）</p>\"}",
                    "title", "content");
            raw = call.raw;
            if (call.json == null) {
                return record(p, "article", null, null, null, null, raw, "blocked",
                        "重写后仍不符合格式", null, (int) (System.currentTimeMillis() - start));
            }
            title = trim(SiteAiPrompt.jsonField(call.json, "title"), 190);
            summary = trim(SiteAiPrompt.jsonField(call.json, "summary"), 400);
            // 这里也走 normalizeArticleBody：它会在返回 HTML 时过白名单净化（以前这条重写分支漏了净化）
            content = normalizeArticleBody(SiteAiPrompt.jsonField(call.json, "content"));
            String second = sensitiveHit(title + summary + content);
            if (second != null) {
                return record(p, "article", null, null, title, content, raw, "blocked",
                        "敏感词命中（" + second + "），本次未发布", null, (int) (System.currentTimeMillis() - start));
            }
        }
        BlogArticle article = new BlogArticle();
        article.setTitle(title);
        article.setSummary(summary);
        article.setContent(content);
        article.setCover(resolveCover(p));
        article.setStatus(1);
        article.setIsTop(0);
        article.setViewCount(0);
        article.setAuthorName(displayName(p));
        article.setAiGenerated(1);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        articleMapper.insert(article);
        log.info("IRIS 发布文章 #{}《{}》", article.getId(), title);
        return record(p, "article", "article", article.getId(), title, content, raw, "success", null,
                p.getArticleModel(), (int) (System.currentTimeMillis() - start));
    }

    /**
     * 文章正文归一：优先当 HTML 处理（新提示词就是让模型直接输出 HTML），
     * 若模型仍然写了 Markdown（换模型/偷懒时会发生），再走 Markdown 转换器兜底。
     * 两条路都会先过白名单净化——她是无人审核直接发布的，这一步不能省。
     */
    private String normalizeArticleBody(String raw) {
        String text = trim(plain(raw), 30000);
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (HtmlSanitizer.looksLikeHtml(text)) {
            return HtmlSanitizer.sanitize(text);
        }
        return HtmlSanitizer.sanitize(MarkdownLite.toHtmlWithFallback(text));
    }
    private String resolveCover(SiteAiProfile p) {
        String source = p.getCoverSource() == null ? "fixed" : p.getCoverSource();
        switch (source) {
            case "none":
                return null;
            case "pool":
                return pickFromPool(p);
            case "acg":
                try {
                    return acgCoverService.randomCover();
                } catch (Exception e) {
                    log.warn("IRIS 取随机封面失败（不影响发文）：{}", e.getMessage());
                    return p.getCoverFixed();
                }
            case "fixed":
            default:
                // 管理员统一指定的封面；没上传就先用封面池里第一张，再没有就不带封面
                if (p.getCoverFixed() != null && !p.getCoverFixed().trim().isEmpty()) {
                    return p.getCoverFixed().trim();
                }
                List<String> pool = coverPool(p);
                return pool.isEmpty() ? null : pool.get(0);
        }
    }

    /** 封面池：每行一个地址，按"已发文章数 % 池大小"顺序轮换（可预期、不重复打乱） */
    private String pickFromPool(SiteAiProfile p) {
        List<String> pool = coverPool(p);
        if (pool.isEmpty()) {
            return p.getCoverFixed();
        }
        Long count = articleMapper.selectCount(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getAiGenerated, 1));
        int index = (int) ((count == null ? 0 : count) % pool.size());
        return pool.get(index);
    }

    private List<String> coverPool(SiteAiProfile p) {
        List<String> pool = new ArrayList<>();
        if (p.getCoverPool() == null || p.getCoverPool().trim().isEmpty()) {
            return pool;
        }
        for (String line : p.getCoverPool().split("[\\r\\n,，]+")) {
            String url = line.trim();
            if (!url.isEmpty()) {
                pool.add(url);
            }
        }
        return pool;
    }

    /**
     * 把已发布文章的封面统一换成"当前设置"的封面。
     * 用途：之前用随机接口生成的封面质量不可控，管理员设好统一封面后一键替换。
     *
     * @return 更新了几篇
     */
    public int applyCoverToExisting() {
        SiteAiProfile p = profile();
        List<BlogArticle> articles = articleMapper.selectList(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getAiGenerated, 1)
                .orderByAsc(BlogArticle::getId));
        int updated = 0;
        for (BlogArticle article : articles) {
            String cover = "pool".equals(p.getCoverSource()) ? pickFromPool(p) : resolveCover(p);
            if (cover == null || cover.equals(article.getCover())) {
                continue;
            }
            article.setCover(cover);
            articleMapper.updateById(article);
            updated++;
        }
        log.info("IRIS 文章封面已按当前设置统一替换，共 {} 篇", updated);
        return updated;
    }

    // ============================== 评论吐槽 ==============================

    private SiteAiActivity doComment(SiteAiProfile p, boolean manual) {
        List<BlogArticle> candidates = commentCandidates(p);
        BlogArticle target = null;
        for (BlogArticle a : candidates) {
            Long exists = commentMapper.selectCount(new LambdaQueryWrapper<BlogComment>()
                    .eq(BlogComment::getArticleId, a.getId())
                    .eq(BlogComment::getAiGenerated, 1)
                    .ge(BlogComment::getCreateTime, LocalDate.now().atStartOfDay()));
            if (exists == null || exists == 0) {
                target = a;
                break;
            }
        }
        if (target == null) {
            if (manual) {
                throw new BusinessException("最近的文章 IRIS 都已经评论过了，等有新文章再试");
            }
            return record(p, "comment", null, null, null, null, null, "skipped", "没有合适的文章可评论", null, 0);
        }
        String system = SiteAiPrompt.system(displayName(p), p.getPersonalityJson(), p.getPromptExtra());
        String user = SiteAiPrompt.commentUser(target.getTitle(), target.getSummary(), memoriesText(p));
        long start = System.currentTimeMillis();
        JsonCall call = chatJson(p.getCommentProviderId(), p.getCommentModel(), system, user, 0.85,
                "{\"comment\": \"你的评论\"}", "comment");
        String raw = call.raw;
        if (call.json == null) {
            return record(p, "comment", "article", target.getId(), target.getTitle(), null, raw, "failed",
                    "模型两次都没有返回约定格式（需要 comment 字段）", null,
                    (int) (System.currentTimeMillis() - start));
        }
        String content = HtmlSanitizer.stripTags(trim(SiteAiPrompt.jsonField(call.json, "comment"), 400));
        if (content.isEmpty()) {
            return record(p, "comment", null, target.getId(), target.getTitle(), null, raw, "failed",
                    "模型返回空内容", null, (int) (System.currentTimeMillis() - start));
        }
        String hit = sensitiveHit(content);
        if (hit != null) {
            call = chatJson(p.getCommentProviderId(), p.getCommentModel(), system,
                    user + "\n\n（上一次的措辞里出现了不适合公开发布的词：" + hit
                            + "，请换一种说法重写，其余要求不变）", 0.85,
                    "{\"comment\": \"你的评论\"}", "comment");
            raw = call.raw;
            content = call.json == null ? "" : HtmlSanitizer.stripTags(trim(SiteAiPrompt.jsonField(call.json, "comment"), 400));
            String second = sensitiveHit(content);
            if (content.isEmpty() || second != null) {
                return record(p, "comment", "article", target.getId(), target.getTitle(), content, raw, "blocked",
                        "敏感词命中（" + (second == null ? "空内容" : second) + "），本次未发布", null,
                        (int) (System.currentTimeMillis() - start));
            }
        }
        BlogComment comment = new BlogComment();
        comment.setArticleId(target.getId());
        comment.setContent(content);
        comment.setNickname(displayName(p));
        comment.setAvatar(p.getAvatar());
        comment.setStatus(1);
        comment.setAiGenerated(1);
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);
        log.info("IRIS 在文章 #{} 下评论 #{}", target.getId(), comment.getId());
        return record(p, "comment", "comment", comment.getId(), target.getTitle(), content, raw, "success", null,
                p.getCommentModel(), (int) (System.currentTimeMillis() - start));
    }

    /** 评论候选：最新已发布文章；commentScope 含 owner 时优先站长的文章 */
    private List<BlogArticle> commentCandidates(SiteAiProfile p) {
        List<BlogArticle> latest = articleMapper.selectList(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getStatus, 1)
                .orderByDesc(BlogArticle::getCreateTime)
                .last("limit " + COMMENT_CANDIDATES));
        String scope = p.getCommentScope() == null ? "latest+owner" : p.getCommentScope();
        if (!scope.contains("owner") || latest.isEmpty()) {
            return latest;
        }
        List<Long> ownerIds = superAdminIds();
        List<BlogArticle> mine = new ArrayList<>();
        List<BlogArticle> others = new ArrayList<>();
        for (BlogArticle a : latest) {
            if (a.getAuthorId() != null && ownerIds.contains(a.getAuthorId())) {
                mine.add(a);
            } else {
                others.add(a);
            }
        }
        mine.addAll(others);
        return mine;
    }

    private List<Long> superAdminIds() {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "SUPER"));
        List<Long> ids = new ArrayList<>();
        for (SysUser u : users) {
            ids.add(u.getId());
        }
        return ids;
    }

    // ============================== 状态更新 ==============================

    private SiteAiActivity doStatus(SiteAiProfile p, boolean manual) {
        String system = SiteAiPrompt.system(displayName(p), p.getPersonalityJson(), p.getPromptExtra());
        String user = SiteAiPrompt.statusUser(null, todayDigest());
        long start = System.currentTimeMillis();
        JsonCall call = chatJson(p.getStatusProviderId(), p.getStatusModel(), system, user, 0.9,
                "{\"status\": \"这一句状态\"}", "status");
        String raw = call.raw;
        String content = call.json == null ? ""
                : HtmlSanitizer.stripTags(trim(SiteAiPrompt.jsonField(call.json, "status"), 300));
        if (content.isEmpty()) {
            return record(p, "status", null, null, null, null, raw, "failed",
                    call.json == null ? "模型两次都没有返回约定格式（需要 status 字段）" : "模型返回空内容", null,
                    (int) (System.currentTimeMillis() - start));
        }
        return record(p, "status", null, null, null, content, raw, "success", null, p.getStatusModel(),
                (int) (System.currentTimeMillis() - start));
    }

    private String latestStatus() {
        SiteAiActivity a = activityMapper.selectOne(new LambdaQueryWrapper<SiteAiActivity>()
                .eq(SiteAiActivity::getActivityType, "status")
                .eq(SiteAiActivity::getStatus, "success")
                .orderByDesc(SiteAiActivity::getId).last("limit 1"));
        return a == null ? null : a.getContent();
    }

    // ============================== 回复读者 ==============================

    @Override
    public void maybeReplyAsync(Long commentId) {
        if (commentId == null) {
            return;
        }
        try {
            replyExecutor.submit(() -> {
                try {
                    maybeReply(commentId);
                } catch (Exception e) {
                    log.warn("IRIS 回复读者失败（评论 #{}）：{}", commentId, e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("IRIS 回复任务提交失败：{}", e.getMessage());
        }
    }

    private void maybeReply(Long commentId) {
        SiteAiProfile p = profile();
        if (p.getEnabled() == null || p.getEnabled() != 1
                || p.getReplyEnabled() == null || p.getReplyEnabled() != 1) {
            return;
        }
        BlogComment comment = commentMapper.selectById(commentId);
        if (comment == null || (comment.getAiGenerated() != null && comment.getAiGenerated() == 1)) {
            return;
        }
        // 只回"她自己的内容下"的留言：要么回复的是她的评论，要么是她的文章
        String source = null;
        if (comment.getParentId() != null) {
            BlogComment parent = commentMapper.selectById(comment.getParentId());
            if (parent != null && parent.getAiGenerated() != null && parent.getAiGenerated() == 1) {
                source = parent.getContent();
            }
        }
        if (source == null && comment.getArticleId() != null) {
            BlogArticle article = articleMapper.selectById(comment.getArticleId());
            if (article != null && article.getAiGenerated() != null && article.getAiGenerated() == 1) {
                source = "《" + article.getTitle() + "》" + (article.getSummary() == null ? "" : "——" + article.getSummary());
            }
        }
        if (source == null) {
            return;
        }
        // 同一个留言只回一次
        Long replied = commentMapper.selectCount(new LambdaQueryWrapper<BlogComment>()
                .eq(BlogComment::getParentId, commentId)
                .eq(BlogComment::getAiGenerated, 1));
        if (replied != null && replied > 0) {
            return;
        }
        int dayLimit = p.getReplyDailyLimit() == null ? 5 : p.getReplyDailyLimit();
        if (todayCount("reply") >= dayLimit) {
            return;
        }
        int cooldown = p.getReplyCooldownMinutes() == null ? 30 : p.getReplyCooldownMinutes();
        LocalDateTime lastReply = lastRunAt("reply");
        if (cooldown > 0 && lastReply != null && lastReply.isAfter(LocalDateTime.now().minusMinutes(cooldown))) {
            return;
        }
        String system = SiteAiPrompt.system(displayName(p), p.getPersonalityJson(), p.getPromptExtra());
        String user = SiteAiPrompt.replyUser(source, comment.getContent());
        long start = System.currentTimeMillis();
        JsonCall call = chatJson(p.getReplyProviderId(), p.getReplyModel(), system, user, 0.85,
                "{\"reply\": \"你的回复\"}", "reply");
        String raw = call.raw;
        if (call.json == null) {
            record(p, "reply", "comment", commentId, null, null, raw, "failed",
                    "模型两次都没有返回约定格式（需要 reply 字段），本次未发布", null,
                    (int) (System.currentTimeMillis() - start));
            return;
        }
        String content = HtmlSanitizer.stripTags(trim(SiteAiPrompt.jsonField(call.json, "reply"), 400));
        if (content.isEmpty() || sensitiveHit(content) != null) {
            record(p, "reply", "comment", commentId, null, content, raw, "blocked",
                    "回复内容为空或命中敏感词，未发布", null, (int) (System.currentTimeMillis() - start));
            return;
        }
        BlogComment reply = new BlogComment();
        reply.setArticleId(comment.getArticleId());
        reply.setParentId(commentId);
        reply.setContent(content);
        reply.setNickname(displayName(p));
        reply.setAvatar(p.getAvatar());
        reply.setStatus(1);
        reply.setAiGenerated(1);
        reply.setCreateTime(LocalDateTime.now());
        commentMapper.insert(reply);
        log.info("IRIS 回复留言 #{} → 新评论 #{}", commentId, reply.getId());
        record(p, "reply", "comment", reply.getId(), null, content, raw, "success", null, p.getReplyModel(),
                (int) (System.currentTimeMillis() - start));
    }

    // ============================== 记忆 ==============================

    @Override
    public int summarize() {
        SiteAiProfile p = profile();
        if (p.getEnabled() == null || p.getEnabled() != 1) {
            return 0;
        }
        String digest = todayDigest();
        if (digest.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        SiteAiMemory exists = memoryMapper.selectOne(new LambdaQueryWrapper<SiteAiMemory>()
                .eq(SiteAiMemory::getAiName, p.getNameEn())
                .eq(SiteAiMemory::getMemoryDate, today).last("limit 1"));
        String system = SiteAiPrompt.system(displayName(p), p.getPersonalityJson(), p.getPromptExtra());
        String user = SiteAiPrompt.memoryUser(digest);
        JsonCall call = chatJson(p.getStatusProviderId(), p.getStatusModel(), system, user, 0.85,
                "{\"summary\": \"这段记忆（120~220 字）\"}", "summary");
        String raw = call.raw;
        String summary = call.json == null ? "" : trim(SiteAiPrompt.jsonField(call.json, "summary"), 2000);
        if (summary.isEmpty()) {
            return 0;
        }
        if (exists == null) {
            exists = new SiteAiMemory();
            exists.setAiName(p.getNameEn());
            exists.setMemoryDate(today);
            exists.setSummary(summary);
            exists.setActivityCount(countTodayActivities());
            exists.setCreateTime(LocalDateTime.now());
            memoryMapper.insert(exists);
        } else {
            exists.setSummary(summary);
            exists.setActivityCount(countTodayActivities());
            memoryMapper.updateById(exists);
        }
        record(p, "memory", null, null, null, summary, raw, "success", null, p.getStatusModel(), 0);
        // 顺手清理超过保留天数的记忆
        int days = p.getMemoryDays() == null ? 7 : p.getMemoryDays();
        memoryMapper.delete(new LambdaQueryWrapper<SiteAiMemory>()
                .lt(SiteAiMemory::getMemoryDate, today.minusDays(Math.max(1, days))));
        return 1;
    }

    private int countTodayActivities() {
        Long n = activityMapper.selectCount(new LambdaQueryWrapper<SiteAiActivity>()
                .ge(SiteAiActivity::getCreateTime, LocalDate.now().atStartOfDay())
                .ne(SiteAiActivity::getActivityType, "memory"));
        return n == null ? 0 : n.intValue();
    }

    /**
     * 她自己今天真正做过的事（写文章 / 评论 / 回复 / 状态），用于给她"真实素材"。
     *
     * 这是抑制编造的关键：以前她只能靠"想象"写今天发生了什么，就容易出现
     * "我清理缓存时在内存里发现一段旧日志"这种凭空情节；把真实活动摆在她面前，
     * 并要求只能依据这些材料写作，编造的空间就小多了。
     */
    private String myTodayActivities() {
        List<SiteAiActivity> list = activityMapper.selectList(new LambdaQueryWrapper<SiteAiActivity>()
                .ge(SiteAiActivity::getCreateTime, LocalDate.now().atStartOfDay())
                .in(SiteAiActivity::getActivityType, Arrays.asList("article", "comment", "reply", "status", "report"))
                .eq(SiteAiActivity::getStatus, "success")
                .orderByAsc(SiteAiActivity::getId));
        StringBuilder sb = new StringBuilder();
        for (SiteAiActivity a : list) {
            sb.append("· ").append(typeText(a.getActivityType()));
            if (a.getTitle() != null) {
                sb.append("（").append(a.getTitle()).append("）");
            }
            String content = a.getContent() == null ? "" : a.getContent().replaceAll("<[^>]*>", "").trim();
            if (!content.isEmpty()) {
                sb.append("：").append(trim(content, 80));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
    /** 最近几天的记忆（供写作参考） */
    private String memoriesText(SiteAiProfile p) {
        int days = p.getMemoryDays() == null ? 7 : p.getMemoryDays();
        List<SiteAiMemory> list = memoryMapper.selectList(new LambdaQueryWrapper<SiteAiMemory>()
                .ge(SiteAiMemory::getMemoryDate, LocalDate.now().minusDays(Math.max(1, days)))
                .orderByDesc(SiteAiMemory::getMemoryDate).last("limit 5"));
        if (list.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (SiteAiMemory m : list) {
            sb.append("· ").append(m.getMemoryDate()).append("：").append(m.getSummary()).append("\n");
        }
        return sb.toString();
    }

    private String todayDigest() {
        List<SiteAiActivity> list = activityMapper.selectList(new LambdaQueryWrapper<SiteAiActivity>()
                .ge(SiteAiActivity::getCreateTime, LocalDate.now().atStartOfDay())
                .in(SiteAiActivity::getActivityType, Arrays.asList("article", "comment", "reply", "status", "report"))
                .eq(SiteAiActivity::getStatus, "success")
                .orderByAsc(SiteAiActivity::getId));
        StringBuilder sb = new StringBuilder();
        for (SiteAiActivity a : list) {
            sb.append("· ").append(typeText(a.getActivityType()));
            if (a.getTitle() != null) {
                sb.append("（").append(a.getTitle()).append("）");
            }
            sb.append("：").append(trim(a.getContent(), 120)).append("\n");
        }
        return sb.toString();
    }

    private String typeText(String type) {
        if ("article".equals(type)) return "写了文章";
        if ("comment".equals(type)) return "发表了评论";
        if ("reply".equals(type)) return "回复了读者";
        if ("status".equals(type)) return "更新了状态";
        if ("report".equals(type)) return "写了运行情况";
        return type;
    }

    /**
     * 她自己最近写过的文章（含今天），标题 + 摘要，用来避免重复题材。
     * 只给标题不够——她经常换个说法写同一个题目，所以要连摘要一起给。
     */
    private String myRecentArticles() {
        List<BlogArticle> list = articleMapper.selectList(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getAiGenerated, 1)
                .eq(BlogArticle::getStatus, 1)
                .orderByDesc(BlogArticle::getCreateTime).last("limit 10"));
        StringBuilder sb = new StringBuilder();
        for (BlogArticle a : list) {
            sb.append("· ").append(a.getTitle());
            if (a.getSummary() != null && !a.getSummary().trim().isEmpty()) {
                sb.append("｜").append(trim(a.getSummary().replaceAll("<[^>]*>", ""), 60));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String recentTitles() {
        List<BlogArticle> list = articleMapper.selectList(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getStatus, 1)
                .orderByDesc(BlogArticle::getCreateTime).last("limit 10"));
        StringBuilder sb = new StringBuilder();
        for (BlogArticle a : list) {
            sb.append("· ").append(a.getTitle()).append("\n");
        }
        return sb.toString();
    }

    // ============================== 撤销 ==============================

    @Override
    public void revert(Long activityId) {
        SiteAiActivity a = activityMapper.selectById(activityId);
        if (a == null) {
            throw new BusinessException("活动记录不存在");
        }
        if (a.getRevertible() == null || a.getRevertible() != 1 || (a.getReverted() != null && a.getReverted() == 1)) {
            throw new BusinessException("这条记录不能撤销（或已经撤销过）");
        }
        if ("article".equals(a.getActivityType()) && a.getTargetId() != null) {
            BlogArticle article = articleMapper.selectById(a.getTargetId());
            if (article != null) {
                article.setStatus(0);
                articleMapper.updateById(article);
            }
        } else if (("comment".equals(a.getActivityType()) || "reply".equals(a.getActivityType()))
                && a.getTargetId() != null) {
            commentMapper.deleteById(a.getTargetId());
        } else {
            throw new BusinessException("这类记录不需要撤销");
        }
        a.setReverted(1);
        activityMapper.updateById(a);
        log.info("IRIS 活动 #{} 已撤销", activityId);
    }

    // ============================== 前台主页 ==============================

    @Override
    public Map<String, Object> portal() {
        SiteAiProfile p = profile();
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("displayName", displayName(p));
        info.put("nameEn", p.getNameEn());
        info.put("nameCn", p.getNameCn());
        info.put("modelNo", p.getModelNo());
        info.put("tagline", p.getTagline());
        info.put("avatar", p.getAvatar());
        info.put("bio", p.getBio());
        info.put("enabled", p.getEnabled());
        map.put("profile", info);
        map.put("status", latestStatus());
        List<SiteAiActivity> activities = activityMapper.selectList(new LambdaQueryWrapper<SiteAiActivity>()
                .in(SiteAiActivity::getActivityType, Arrays.asList("article", "comment", "reply", "status", "report"))
                .eq(SiteAiActivity::getStatus, "success")
                .eq(SiteAiActivity::getReverted, 0)
                .orderByDesc(SiteAiActivity::getId).last("limit 12"));
        // 前台时间线不适合直接展示 HTML：文章/日报只给纯文本摘要，避免把标签露出来
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (SiteAiActivity a : activities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("activityType", a.getActivityType());
            item.put("title", a.getTitle());
            item.put("createTime", a.getCreateTime());
            boolean html = "article".equals(a.getActivityType()) || "report".equals(a.getActivityType());
            String text = a.getContent() == null ? "" : a.getContent();
            if (html) {
                text = text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                if (text.length() > 100) {
                    text = text.substring(0, 100) + "……";
                }
            }
            item.put("content", text);
            // 文章类带上文章 id，前台可以点进文章看全文
            item.put("articleId", html ? a.getTargetId() : null);
            timeline.add(item);
        }
        map.put("activities", timeline);
        List<BlogArticle> articles = articleMapper.selectList(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getStatus, 1)
                .eq(BlogArticle::getAiGenerated, 1)
                .orderByDesc(BlogArticle::getCreateTime).last("limit 10"));
        map.put("articles", articles);
        return map;
    }

    // ============================== 工具 ==============================

    /** 当前档案是否开启站内敏感词过滤 */
    private boolean sensitiveFilterOn(SiteAiProfile p) {
        return p != null && (p.getSensitiveFilterEnabled() == null || p.getSensitiveFilterEnabled() == 1);
    }

    private String chat(Long providerId, String model, String system, String user, double temperature) {
        AiProvider provider = aiProviderService.resolveSystemProvider(providerId);
        String useModel = model == null || model.trim().isEmpty() ? null : model.trim();
        return aiProviderService.chat(provider, useModel, system, user, temperature);
    }

    /** 一次"要 JSON 字段"的调用结果 */
    private static final class JsonCall {
        /** 解析好的 JSON（两次都没解析出来时为 null） */
        JSONObject json;
        /** 原始回复；走过二次自检时会把第二次的原文也带上，便于在活动日志里回溯 */
        String raw;
        /** 是否触发过二次自检 */
        boolean retried;
    }

    /**
     * 要求模型按约定字段返回 JSON；不合格就带上"哪里不合格 + 上一次的输出"再要一次（二次自检）。
     *
     * 输出格式是三段式思考（<think> → <draft> → <final>），只有 <final> 里的 JSON 会被采用，
     * 思考与草稿永远不会流到前台；解析统一交给 SandboxReplyParser（它会处理"模型忘了写 <final>"、
     * 草稿里混了示例 JSON 等实测过的坑）。
     *
     * 两道防线：
     *   1. 严格 JSON 解析 → 失败时用宽松提取（模型把换行写成字面量 \n、少个逗号也能救回来）；
     *   2. 仍然缺字段 → 二次调用，让模型看着自己的错误输出重写一次。
     * 返回的 json 为 null 表示两次都没给出合格格式，调用方按失败处理（不要把原文发出去）。
     */
    private JsonCall chatJson(Long providerId, String model, String system, String user,
                              double temperature, String exampleJson, String... requiredFields) {
        String stagedUser = user + SiteAiPrompt.stageSection(exampleJson, requiredFields);
        JsonCall call = new JsonCall();
        call.raw = chat(providerId, model, system, stagedUser, temperature);
        call.json = parseJsonLoose(call.raw, requiredFields);
        String bad = checkFields(call.json, requiredFields);
        if (bad == null) {
            return call;
        }
        // 二次自检：把错误原因和上一次的输出一起给它，让它自己改
        String fixUser = stagedUser
                + "\n\n【上一次的输出不合格】\n" + truncateForFeedback(call.raw)
                + "\n不合格原因：" + bad
                + "\n请重新按三段式输出一遍（<think> → <draft> → <final>），"
                + "并在 <final> 里给出**一个 JSON 对象**，必须包含这些字段：" + String.join("、", requiredFields)
                + "（都是非空字符串），形如 " + exampleJson + "；不要多余字段。";
        log.info("IRIS 输出不合格（{}），已二次调用让它自检重写", bad);
        String raw2 = chat(providerId, model, system, fixUser, temperature);
        call.retried = true;
        call.raw = call.raw + "\n\n--- 二次自检重写 ---\n" + raw2;
        JSONObject again = parseJsonLoose(raw2, requiredFields);
        String bad2 = checkFields(again, requiredFields);
        if (bad2 == null) {
            call.json = again;
            return call;
        }
        log.warn("IRIS 二次自检后仍不合格（{}），本次不发布", bad2);
        call.json = null;
        return call;
    }

    /** 先取 <final> 段 → 严格解析 → 宽松提取 */
    private JSONObject parseJsonLoose(String raw, String... fields) {
        JSONObject obj = parseJson(raw);
        if (checkFields(obj, fields) == null) {
            return obj;
        }
        // 三段式回复里草稿段也可能有 JSON 示例，所以宽松提取只在「终稿段」里找
        String finalBlock = SandboxReplyParser.extractFinalBlock(raw);
        JSONObject loose = SiteAiPrompt.looseJson(finalBlock == null ? raw : finalBlock, fields);
        return loose != null ? loose : obj;
    }

    /** 检查必需字段；返回不合格原因（合格返回 null） */
    private String checkFields(JSONObject obj, String... requiredFields) {
        if (obj == null) {
            return "没有解析到 JSON 对象";
        }
        for (String field : requiredFields) {
            if (SiteAiPrompt.jsonField(obj, field) == null) {
                return "缺少字段 " + field + "（或它不是非空字符串）";
            }
        }
        return null;
    }

    /** 二次自检时把上一次的输出带回去，太长就截断（文章可能上万字） */
    private String truncateForFeedback(String raw) {
        String text = raw == null ? "（空）" : raw.trim();
        return text.length() <= 1200 ? text : text.substring(0, 1200) + "\n…（上一次输出过长，已截断）";
    }

    /**
     * 敏感词命中时返回命中的片段（便于在活动日志里看清到底拦了什么），没命中返回 null。
     * 是否启用由「敏感词过滤」开关决定：关掉后完全交给 AI 服务商自身的判断。
     */
    private String sensitiveHit(String text) {
        if (text == null || text.trim().isEmpty() || !sensitiveFilterOn(profile())) {
            return null;
        }
        String filtered = sensitiveWordFilter.filter(text);
        if (filtered.equals(text)) {
            return null;
        }
        // 找出第一处差异，取原文附近的一小段作为"命中提示"
        int index = firstDiff(text, filtered);
        int from = Math.max(0, index - 3);
        int to = Math.min(text.length(), index + 5);
        String hit = text.substring(from, to).replaceAll("\\s+", " ").trim();
        return hit.isEmpty() ? "敏感词" : hit;
    }

    private int firstDiff(String a, String b) {
        int len = Math.min(a.length(), b.length());
        for (int i = 0; i < len; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return i;
            }
        }
        return len;
    }

    /**
     * 取回复里的最终 JSON：优先三段式的 <final> 段（{@link SandboxReplyParser} 会处理
     * "模型忘了写 <final>"、"草稿段里也有示例 JSON"这些情况），再退回"整段里最外层的 JSON"。
     */
    private JSONObject parseJson(String raw) {
        String json = SandboxReplyParser.extractFinalJson(raw);
        if (json == null) {
            String text = plain(raw);
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            json = text.substring(start, end + 1);
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** 去掉 ``` 代码块标记与 <final> 之类的标签，方便取纯文本 */
    private String plain(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("```json", "").replace("```", "").trim();
        text = text.replaceAll("</?(final|draft|review|think|recap)>", "").trim();
        return text;
    }

    private String trim(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private SiteAiActivity record(SiteAiProfile p, String type, String targetType, Long targetId,
                                  String title, String content, String raw, String status, String error,
                                  String model, int costMs) {
        SiteAiActivity a = new SiteAiActivity();
        a.setAiName(p.getNameEn() == null ? "IRIS" : p.getNameEn());
        a.setActivityType(type);
        a.setTargetType(targetType);
        a.setTargetId(targetId);
        a.setTitle(trim(title, 190));
        a.setContent(trim(content, 20000));
        a.setModel(model);
        a.setPromptVersion(SiteAiPrompt.VERSION);
        a.setRawResponse(raw);
        a.setStatus(status);
        a.setError(trim(error, 280));
        a.setRevertible("success".equals(status) && targetId != null ? 1 : 0);
        a.setReverted(0);
        a.setCostMs(costMs);
        a.setCreateTime(LocalDateTime.now());
        activityMapper.insert(a);
        return a;
    }

}
