package com.bc.bcblog.tools;

import com.bc.bcblog.entity.SiteAiProfile;
import com.bc.bcblog.service.SiteAiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 临时探针：改「每天最多几篇」/ 改写作窗口后，当天写作计划是否立刻重排。
 *
 * 口径：
 *   · 计划总格数 = 新的「每天最多几篇」（已经写完的格子算在里面，只补差额）；
 *   · 还没写的格子（pending）时间必须落在新的写作窗口里；
 *   · 已写完的格子保留原时间（那是今天真实写过的记录）。
 * 跑完会把后台参数和原始计划 JSON 都还原。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IrisPlanProbe {

    static { System.setProperty("bcblog.sandbox.scheduler.disabled", "true"); }

    @Autowired private SiteAiService siteAiService;

    private int fail = 0;
    private void ok(String m) { System.out.println("  ok   " + m); }
    private void bad(String m) { System.out.println("  FAIL " + m); fail++; }

    private String plan() {
        Map<String, Object> o = siteAiService.overview();
        return o.get("plan") == null ? "" : String.valueOf(o.get("plan"));
    }

    private int slotCount(String plan) {
        if (plan == null || plan.isEmpty()) return 0;
        return plan.split("、").length;
    }

    /** 重排后不该再往过去排时间（否则会一口气连写好几篇） */
    private boolean pendingNotPast(String plan) {
        String nowHm = LocalTime.now().minusMinutes(1).format(DateTimeFormatter.ofPattern("HH:mm"));
        for (String part : plan.split("、")) {
            if (part.isEmpty() || !part.contains("(pending)")) continue;
            String time = part.split("\\(")[0];
            if (time.compareTo(nowHm) < 0) {
                System.out.println("      （过去的时间点：" + time + " < " + nowHm + "）");
                return false;
            }
        }
        return true;
    }

    /** pending 的格子是否都在 [start, end] 窗口内 */
    private boolean pendingInWindow(String plan, String start, String end) {
        for (String part : plan.split("、")) {
            if (part.isEmpty() || !part.contains("(pending)")) continue;
            String time = part.split("\\(")[0];
            if (time.compareTo(start) < 0 || time.compareTo(end) > 0) return false;
        }
        return true;
    }

    private int doneCount(String plan) {
        int n = 0;
        for (String part : plan.split("、")) if (part.contains("(done)")) n++;
        return n;
    }

    private void check(String tag, String plan, int expectCount, String start, String end) {
        System.out.println("   [" + tag + "] " + plan);
        int slots = slotCount(plan);
        int done = doneCount(plan);
        if (slots != expectCount) {
            bad(tag + "：计划格数 " + slots + " ≠ 配置的 " + expectCount);
        } else {
            ok(tag + "：计划重排为 " + slots + " 格（其中已完成 " + done + " 格）");
        }
        if (!pendingInWindow(plan, start, end)) {
            bad(tag + "：有 pending 时段落在写作窗口 " + start + "~" + end + " 之外");
        } else {
            ok(tag + "：待写时段都在窗口 " + start + "~" + end + " 内");
        }
        if (!pendingNotPast(plan)) {
            bad(tag + "：重排后还排了已经过去的时间点（会连写好几篇）");
        } else {
            ok(tag + "：待写时段都在当前时间之后");
        }
    }

    @Test
    void probe() {
        SiteAiProfile origin = siteAiService.profile();
        Integer originLimit = origin.getArticleDailyLimit();
        String originStart = origin.getArticleWindowStart();
        String originEnd = origin.getArticleWindowEnd();
        Integer originRandom = origin.getArticleRandom();
        String originPlan = origin.getArticlePlanJson();
        System.out.println("   原始配置：每天 " + originLimit + " 篇，窗口 " + originStart + "~" + originEnd
                + "，random=" + originRandom);
        try {
            SiteAiProfile p = siteAiService.profile();
            p.setArticleDailyLimit(5);
            p.setArticleWindowStart("09:00");
            p.setArticleWindowEnd("22:00");
            p.setArticleRandom(1);
            siteAiService.saveProfile(p);
            check("改成 5 篇", plan(), 5, "09:00", "22:00");

            p = siteAiService.profile();
            p.setArticleDailyLimit(3);
            siteAiService.saveProfile(p);
            check("改成 3 篇", plan(), 3, "09:00", "22:00");

            p = siteAiService.profile();
            p.setArticleDailyLimit(2);
            p.setArticleWindowStart("10:00");
            p.setArticleWindowEnd("18:00");
            siteAiService.saveProfile(p);
            check("改成 2 篇 + 窗口 10:00~18:00", plan(), 2, "10:00", "18:00");

            p = siteAiService.profile();
            p.setArticleDailyLimit(4);
            p.setArticleWindowStart("9:00");
            p.setArticleWindowEnd("23:00");
            siteAiService.saveProfile(p);
            String once = plan();
            check("改成 4 篇 + 非补零写法 9:00（窗口 9:00~23:00）", once, 4, "09:00", "23:00");
            String twice = plan();
            if (once.equals(twice)) {
                ok("配置没动时计划稳定（不会每刷一次就重排）：" + twice);
            } else {
                bad("配置没动但计划每次都变：" + once + " -> " + twice);
            }
        } finally {
            SiteAiProfile p = siteAiService.profile();
            p.setArticleDailyLimit(originLimit);
            p.setArticleWindowStart(originStart);
            p.setArticleWindowEnd(originEnd);
            p.setArticleRandom(originRandom);
            p.setArticlePlanJson(originPlan);
            siteAiService.saveProfile(p);
            System.out.println("   [还原] " + plan());
        }
        System.out.println(fail == 0 ? "PROBE RESULT: 全部通过" : "PROBE RESULT: 失败 " + fail + " 项");
        if (fail > 0) { throw new AssertionError("有 " + fail + " 项不符合预期"); }
    }
}
