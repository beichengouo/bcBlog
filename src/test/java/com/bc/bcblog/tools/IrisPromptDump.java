package com.bc.bcblog.tools;

import com.bc.bcblog.common.SiteAiPrompt;
import org.junit.jupiter.api.Test;

/** 只打印提示词，不调用任何模型：看看现在发给 IRIS 的提示词长什么样 */
class IrisPromptDump {

    @Test
    void dump() {
        String system = SiteAiPrompt.system("伊莉丝 IRIS",
                "{\"通称\":\"伊莉丝\",\"说话方式\":\"像在汇报运行状态\"}", "（主人暂时没有额外要求）");
        System.out.println("================ SYSTEM ================");
        System.out.println(system);
        System.out.println("================ USER（回复读者） ================");
        System.out.println(SiteAiPrompt.replyUser("《Git 分支管理入门》——讲清概念与常见坑",
                "伊莉丝，你昨天那篇我看不懂，分支和标签到底有啥区别？")
                + SiteAiPrompt.stageSection("{\"reply\": \"你的回复\"}", "reply"));
        System.out.println("================ USER（写文章，末尾片段） ================");
        String article = SiteAiPrompt.articleUser("技术教程优先", "不要编造", "《旧文A》", "《旧文B》", "（无记忆）", "（今天没有活动）")
                + SiteAiPrompt.stageSection(
                "{\"title\": \"文章标题\", \"summary\": \"一句话摘要\", \"content\": \"<p>正文（HTML）</p>\"}",
                "title", "content");
        System.out.println(article.substring(Math.max(0, article.length() - 700)));
    }
}
