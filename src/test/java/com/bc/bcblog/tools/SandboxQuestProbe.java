package com.bc.bcblog.tools;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxCoinLog;
import com.bc.bcblog.entity.SandboxItem;
import com.bc.bcblog.entity.SandboxLocation;
import com.bc.bcblog.entity.SandboxQuest;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.mapper.SandboxActMapper;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxCoinLogMapper;
import com.bc.bcblog.mapper.SandboxItemMapper;
import com.bc.bcblog.mapper.SandboxLocationMapper;
import com.bc.bcblog.mapper.SandboxQuestMapper;
import com.bc.bcblog.mapper.SandboxWorldMapper;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import com.bc.bcblog.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 旅人委托板的**服务端规则**探针（不调用 AI）。
 *
 * 覆盖第一批里最容易悄悄写错的几条：
 *   1. 接取：一人一委托、状态从 open 变 taken；
 *   2. 进度：只增不减、单步封顶；
 *   3. 完成校验：没到目标地区 → 压回 99% 并写明还差什么；
 *   4. 结算：真的到了地方 → 完成 + 金币进账 + 物品进背包 + 写委托流水；
 *   5. 放弃：回到可接、进度清零、记下放弃者；
 *   6. 战力钳制：手上填的 5 会被夹到目标地点的区间下限。
 *
 * 全程在一个**临时世界**里跑，跑完删干净，不碰真实数据。
 * 手动运行： mvn test "-Dtest=SandboxQuestProbe"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxQuestProbe {

    static {
        // 测试进程里禁用定时任务（见 tools/README.md）
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final String WORLD_NAME = "【探针】委托测试世界";
    private static final String SAFE_PLACE = "【探针】清风镇";
    private static final String FAR_PLACE = "【探针】远山隘口";
    private static final String HERO = "【探针】测试者";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private ConfigService configService;
    @Autowired
    private SandboxWorldMapper worldMapper;
    @Autowired
    private SandboxLocationMapper locationMapper;
    @Autowired
    private SandboxCharacterMapper characterMapper;
    @Autowired
    private SandboxQuestMapper questMapper;
    @Autowired
    private SandboxItemMapper itemMapper;
    @Autowired
    private SandboxCoinLogMapper coinLogMapper;
    @Autowired
    private SandboxActMapper actMapper;

    private int passed = 0;
    /** 报告行：控制台在 Windows 下是 GBK，容易看成乱码，所以同时写一份 UTF-8 报告 */
    private final java.util.List<String> report = new java.util.ArrayList<>();

    @Test
    void run() throws Exception {
        Long worldId = null;
        // 探针**不调 AI**：把「被拦下时让 AI 自检修正」临时关掉，否则完成校验没过时会真的发起模型请求
        String selfcheckBefore = configService.getConfigValue("sandbox_quest_selfcheck", "1");
        configService.setConfigValue("sandbox_quest_selfcheck", "0");
        try {
            worldId = setUpWorld();
            SandboxCharacter hero = setUpCharacter(worldId);

            testTakeAndProgressClamp(worldId, hero);
            testBlockedThenComplete(worldId, hero);
            testAbandon(worldId, hero);
            testItemGainParsing(worldId, hero);
            testStepCapAndChore(worldId, hero);
            testPromptBlocks(hero);
            testResetKeepsBoardIntact();
            testBatchRefresh(worldId, hero);
            testAdminGuards(worldId, hero);

            writeReport("全部通过（" + passed + " 项）");
            System.out.println("\n===== 旅人委托探针：全部通过（" + passed + " 项）=====");
        } catch (Throwable e) {
            writeReport("失败：" + e.getMessage());
            throw e;
        } finally {
            configService.setConfigValue("sandbox_quest_selfcheck", selfcheckBefore);
            if (worldId != null) {
                cleanUp(worldId);
                System.out.println("临时世界已清理（world_id=" + worldId + "）");
            }
        }
    }

    /** 接取 + 进度封顶：一次写 100 也只能涨到单步上限 */
    private void testTakeAndProgressClamp(Long worldId, SandboxCharacter hero) throws Exception {
        SandboxQuest quest = newQuest(worldId, "【探针】清除镇外的野狼", "hunt", SAFE_PLACE, 5);
        quest.setRewardCoins(25);
        quest.setRewardItems("[{\"name\":\"治疗药水\",\"rarity\":3,\"quantity\":1,\"description\":\"淡绿色的低阶恢复药\"}]");
        SandboxQuest saved = service.saveQuest(quest);
        check("战力被夹到地点区间下限（15）", Integer.valueOf(15).equals(saved.getPower()));

        // 接取
        applyQuestActions(hero, obj("quest_take", "【探针】清除镇外的野狼"));
        SandboxQuest taken = questMapper.selectById(saved.getId());
        check("接取后状态为 taken", "taken".equals(taken.getStatus()));
        check("接取人已记录", hero.getId().equals(taken.getTakerId()));

        // 再想接另一条：一人一委托，应被忽略
        SandboxQuest other = service.saveQuest(newQuest(worldId, "【探针】采些药草", "gather", SAFE_PLACE, null));
        applyQuestActions(hero, obj("quest_take", "【探针】采些药草"));
        check("一人一委托：第二条仍是 open", "open".equals(questMapper.selectById(other.getId()).getStatus()));

        // 进度：一步写 100 也只能涨到该难度的单步上限
        applyQuestActions(hero, obj("quest_progress", 100, "quest_note", "一路追着狼群打"));
        // 难度 2 → 分档上限 70（默认表 100,70,50,35,20）
        check("单步进度按难度封顶（难度2 → 70）",
                Integer.valueOf(70).equals(questMapper.selectById(saved.getId()).getProgress()));

        // 进度不允许回落
        applyQuestActions(hero, obj("quest_progress", 5, "quest_note", "好像走错路了"));
        check("进度不允许回落（仍为 70）",
                Integer.valueOf(70).equals(questMapper.selectById(saved.getId()).getProgress()));
    }

    /** 完成校验：没到地方 → 压回 99%；到了地方 → 结算奖励 */
    private void testBlockedThenComplete(Long worldId, SandboxCharacter hero) throws Exception {
        SandboxQuest quest = questMapper.selectOne(new QueryWrapper<SandboxQuest>()
                .eq("world_id", worldId).eq("title", "【探针】清除镇外的野狼").last("limit 1"));
        // 手动把进度推到 90，方便在这一步触发"到 100"的判定
        service.setQuestProgress(quest.getId(), 90, "已经找到狼群的巢穴");
        check("手动改进度生效", Integer.valueOf(90).equals(questMapper.selectById(quest.getId()).getProgress()));

        int coinsBefore = currentCoins(hero.getId());
        int itemsBefore = backpackSize(hero.getId());

        // 人还在别处 → 不该算完成
        applyQuestActions(hero, FAR_PLACE, obj("quest_progress", 100, "quest_note", "在远处把狼打退了"));
        SandboxQuest blocked = questMapper.selectById(quest.getId());
        check("没到目标地区时不算完成", "taken".equals(blocked.getStatus()));
        check("进度被压回 99%", Integer.valueOf(99).equals(blocked.getProgress()));
        check("进度说明写明了还差什么",
                blocked.getProgressNote() != null && blocked.getProgressNote().contains("还没到"));

        // 人到了目标地区 → 完成并结算
        applyQuestActions(hero, SAFE_PLACE, obj("quest_progress", 100, "quest_note", "三只野狼都被解决，狼牙也带回来了"));
        SandboxQuest done = questMapper.selectById(quest.getId());
        check("到地方后完成", "completed".equals(done.getStatus()));
        check("完成后进度为 100", Integer.valueOf(100).equals(done.getProgress()));
        check("完成时间已写", done.getCompletedAt() != null);
        check("金币奖励已到账（+25）", currentCoins(hero.getId()) == coinsBefore + 25);
        check("奖励物品已进背包", backpackSize(hero.getId()) == itemsBefore + 1);
        check("奖励物品带上了品质（稀有=3）", backpackRarity(hero.getId(), "治疗药水") == 3);
        check("写了委托金币流水", hasQuestCoinLog(hero.getId()));
        check("委托的奖励列表带品质字段",
                questMapper.selectById(quest.getId()) != null
                        && service.questsForAdmin(worldId, "all").stream()
                        .filter(q -> q.getId().equals(quest.getId()))
                        .findFirst()
                        .map(q -> q.getRewards() != null && !q.getRewards().isEmpty()
                                && Integer.valueOf(3).equals(q.getRewards().get(0).getRarity()))
                        .orElse(false));
        check("完成的两道校验写入完成经过", done.getCompletionNote() != null && !done.getCompletionNote().isEmpty());
    }

    /** 放弃：回到可接、进度清零、记下放弃者；gather 类还必须真的拿到东西才算完成 */
    private void testAbandon(Long worldId, SandboxCharacter hero) throws Exception {
        SandboxQuest quest = questMapper.selectOne(new QueryWrapper<SandboxQuest>()
                .eq("world_id", worldId).eq("title", "【探针】采些药草").last("limit 1"));
        applyQuestActions(hero, obj("quest_take", "【探针】采些药草"));
        check("第二条可以接取", "taken".equals(questMapper.selectById(quest.getId()).getStatus()));

        // 采集类：进度到 100 但 items_change 没有正数 → 不算完成
        service.setQuestProgress(quest.getId(), 90, "找到一片药草地");
        applyQuestActions(hero, SAFE_PLACE, obj("quest_progress", 100, "quest_note", "只看了看，还没采"));
        SandboxQuest notDone = questMapper.selectById(quest.getId());
        check("采集类没拿到东西不算完成", "taken".equals(notDone.getStatus()));
        check("采集类被压回 99%", Integer.valueOf(99).equals(notDone.getProgress()));

        // 放弃
        applyQuestActions(hero, obj("quest_abandon", true, "quest_note", "药草太难找，先去做别的"));
        SandboxQuest abandoned = questMapper.selectById(quest.getId());
        check("放弃后回到可接", "open".equals(abandoned.getStatus()));
        check("放弃后进度清零", Integer.valueOf(0).equals(abandoned.getProgress()));
        check("放弃后清空接取人", abandoned.getTakerId() == null);
        check("记下了放弃者", abandoned.getAbandonedBy() != null
                && abandoned.getAbandonedBy().contains(HERO));
    }

    /**
     * 单步上限按难度分档 + 杂活不要求物品 + 被截断时不静默。
     *
     * 三个场景都来自实测（芙萝蕾拉那条「清点并保养后勤库房武器」）：
     *   ① 难度 1 的杂活本该一步做完，旧规则却卡在 55%；
     *   ② 杂活被要求"必须拿到物品"，实际靠 AI 顺手买的面包才勉强过校验；
     *   ③ AI 声称完成后被上限截断时，服务端既不写原因也不自检，剧情与状态就岔开了。
     *
     * 这里把自检开关关掉（探针不调 AI），只验证服务端自己的判断与文案。
     */
    private void testStepCapAndChore(Long worldId, SandboxCharacter hero) throws Exception {
        {
            // 场景 ①：难度 1 的杂活，人在目标地区 → 允许一步做完并结算
            SandboxQuest easy = service.saveQuest(newQuest(worldId, "【探针】清点库房（一步做完）",
                    "chore", SAFE_PLACE, null));
            easy.setDifficulty(1);
            easy.setRewardCoins(8);
            service.saveQuest(easy);
            applyQuestActions(hero, obj("quest_take", "【探针】清点库房（一步做完）"));
            applyQuestActions(hero, SAFE_PLACE,
                    obj("quest_progress", 100, "quest_note", "清点完毕，领到了报酬"));
            SandboxQuest done = questMapper.selectById(easy.getId());
            check("① 难度 1 的委托可以一步做完", "completed".equals(done.getStatus()));
            check("① 完成后进度为 100", Integer.valueOf(100).equals(done.getProgress()));

            // 场景 ②：难度 3 的委托一步报 100 → 被分档上限（50）截断，
            // 而且要写清"还剩多少"（以前是静默截断，进度说明还留着 AI 那句"完成并领到报酬"）
            SandboxQuest hard = service.saveQuest(newQuest(worldId, "【探针】棘手的活（会被截断）",
                    "chore", SAFE_PLACE, null));
            hard.setDifficulty(3);
            service.saveQuest(hard);
            applyQuestActions(hero, obj("quest_take", "【探针】棘手的活（会被截断）"));
            applyQuestActions(hero, SAFE_PLACE,
                    obj("quest_progress", 100, "quest_note", "完成了，领到了报酬"));
            SandboxQuest capped = questMapper.selectById(hard.getId());
            check("② 难度 3 一步最多推进 50%", Integer.valueOf(50).equals(capped.getProgress()));
            check("② 被截断时不算完成（奖励不发）", "taken".equals(capped.getStatus()));
            check("② 进度说明写清了还剩多少", capped.getProgressNote() != null
                    && capped.getProgressNote().contains("还剩"));
            check("② 进度说明不再留着「领到了报酬」这句",
                    capped.getProgressNote() != null && !capped.getProgressNote().contains("领到了报酬"));
            // 这条还挂在身上（一人一委托），先放弃掉，才能继续测下面的用例
            applyQuestActions(hero, obj("quest_abandon", true, "quest_note", "这条先放一放"));

            // 场景 ③：杂活不要求物品（items_change 为空也能完成）
            SandboxQuest chore = service.saveQuest(newQuest(worldId, "【探针】誊抄账本（不产出物品）",
                    "chore", SAFE_PLACE, null));
            chore.setDifficulty(1);
            service.saveQuest(chore);
            applyQuestActions(hero, obj("quest_take", "【探针】誊抄账本（不产出物品）"));
            applyQuestActions(hero, SAFE_PLACE,
                    obj("quest_progress", 100, "quest_note", "誊抄完成，交回协会"));
            check("③ 杂活没有物品增量也能完成",
                    "completed".equals(questMapper.selectById(chore.getId()).getStatus()));

            // 场景 ④：采集类仍然必须真的拿到东西（不能被这次改动放松）
            SandboxQuest gather = service.saveQuest(newQuest(worldId, "【探针】采药（仍要求物品）",
                    "gather", SAFE_PLACE, null));
            gather.setDifficulty(1);
            service.saveQuest(gather);
            applyQuestActions(hero, obj("quest_take", "【探针】采药（仍要求物品）"));
            applyQuestActions(hero, SAFE_PLACE, obj("quest_progress", 100, "quest_note", "还没采到"));
            check("④ 采集类没拿到东西依然不算完成",
                    "taken".equals(questMapper.selectById(gather.getId()).getStatus()));
            check("④ 采集类被压回 99%",
                    Integer.valueOf(99).equals(questMapper.selectById(gather.getId()).getProgress()));
            applyQuestActions(hero, obj("quest_abandon", true, "quest_note", "先不采了"));
        }
    }

    /**
     * items_change 的写法兼容 —— 这条是真实 AI 联调里踩出来的坑：
     * AI 有时写 {"银叶草": {"quantity": 2}}（而不是 {"delta": 2}）。
     * 入包逻辑认 quantity（背包里确实多了一株），但完成校验原先只认 delta，
     * 于是采集类委托会卡在 99% 永远做不完。现在两边必须用同一个解析器。
     */
    private void testItemGainParsing(Long worldId, SandboxCharacter hero) throws Exception {
        SandboxQuest quest = service.saveQuest(newQuest(worldId, "【探针】采药（写法兼容）",
                "gather", SAFE_PLACE, null));
        applyQuestActions(hero, obj("quest_take", "【探针】采药（写法兼容）"));
        check("写法兼容用例：委托已接取", "taken".equals(questMapper.selectById(quest.getId()).getStatus()));
        service.setQuestProgress(quest.getId(), 99, "就剩最后一份");

        cn.hutool.json.JSONObject items = new cn.hutool.json.JSONObject();
        items.set("银叶草", new cn.hutool.json.JSONObject()
                .set("quantity", 2).set("description", "刚从石缝里采的"));
        applyQuestActions(hero, SAFE_PLACE,
                obj("quest_progress", 100, "quest_note", "三份都采齐了", "items_change", items));
        check("items_change 写成 quantity 也能算作「拿到了东西」（不再卡 99%）",
                "completed".equals(questMapper.selectById(quest.getId()).getStatus()));
    }

    /**
     * 提示词注入：没有进行中委托时列可接清单 + 「刚刚完成的委托」；
     * 接取之后只给「当前委托」（一人一委托，其余不再展示）。
     */
    private void testPromptBlocks(SandboxCharacter hero) throws Exception {
        String open = appendQuestPrompt(hero);
        check("提示词里列出了可接委托", open.contains("【旅人委托板】") && open.contains("【探针】采些药草"));
        check("可接清单带上了报酬", open.contains("报酬"));
        check("提示词里有接取指引（quest_take）", open.contains("quest_take"));
        // 这一段取的是**最近**完成的那一条，而本探针前面完成过好几条委托
        // （野狼 / 清点库房 / 誊抄账本…），写死具体标题会变成随机失败。
        // 这里只断言"这一段确实渲染出来了、并且是完成口径的说明"。
        check("提示词里提到刚刚完成的委托",
                open.contains("【刚刚完成的委托】") && open.contains("已经结算过了"));

        applyQuestActions(hero, obj("quest_take", "【探针】采些药草"));
        String current = appendQuestPrompt(hero);
        check("接取后变成【当前委托】", current.contains("【当前委托】") && current.contains("【探针】采些药草"));
        check("当前委托带进度与奔波时长", current.contains("当前进度：") && current.contains("奔波了"));
        check("一人一委托：不再列其它可接委托", !current.contains("【旅人委托板】"));
    }

    /**
     * 「重新上板」不能把其他在板上的委托挤下板（用户实测报的第一个问题）。
     *
     * 单独用一个临时世界跑，避免其它用例插入的批次干扰判断：
     * 甲挂在 10 分钟前那一批（它就是最新批），乙挂在 30 分钟前那一批。
     * 把乙重新上板之后，甲必须还在板上——修复前这里会把甲的批次"顶掉"。
     */
    private void testResetKeepsBoardIntact() throws Exception {
        Long wid = setUpWorld();
        try {
            SandboxQuest a = insertQuest(wid, "【探针】板面·甲", LocalDateTime.now().minusMinutes(10), "open", "admin", 1);
            SandboxQuest b = insertQuest(wid, "【探针】板面·乙", LocalDateTime.now().minusMinutes(30), "open", "admin", 1);
            check("① 甲在板上", Boolean.TRUE.equals(onBoardFlag(wid, a.getId())));
            check("① 乙（旧批次）不在板上", Boolean.FALSE.equals(onBoardFlag(wid, b.getId())));

            service.resetQuest(b.getId());

            check("① 重新上板后，原来在板上的甲没有被挤下去", Boolean.TRUE.equals(onBoardFlag(wid, a.getId())));
            check("① 重新上板的乙加入了同一批",
                    questMapper.selectById(b.getId()).getBatchTime()
                            .equals(questMapper.selectById(a.getId()).getBatchTime()));
            check("① 两条都在板上", Boolean.TRUE.equals(onBoardFlag(wid, a.getId()))
                    && Boolean.TRUE.equals(onBoardFlag(wid, b.getId())));
        } finally {
            cleanUp(wid);
        }
    }

    /**
     * 刷新委托板（这是用户报过的坑："我设 5 条，怎么生成完变成 10 条"）。
     *
     * 三件事必须成立：
     *   ① 后台默认只看「当前板面」（最新一批可接 + 接取中 + 近三天已完成），旧批次没人接的不列出来；
     *   ② 生成新一批时，上一批"没人接、没置顶、AI 生成"的委托会被下架（expired），
     *      但**管理员手写的**和**置顶的**要留下，接取中的更不能动；
     *   ③ 被下架的委托「重新上板」后，批次时间要跟到当前批次，否则它回到 open 也显示不到板上。
     */
    private void testBatchRefresh(Long worldId, SandboxCharacter hero) throws Exception {
        LocalDateTime oldBatch = LocalDateTime.now().minusDays(1);
        SandboxQuest oldAi = insertQuest(worldId, "【探针】旧批次·AI 没人接", oldBatch, "open", "ai", 0);
        SandboxQuest oldAdmin = insertQuest(worldId, "【探针】旧批次·管理员手写", oldBatch, "open", "admin", 0);
        SandboxQuest oldPinned = insertQuest(worldId, "【探针】旧批次·AI 但置顶", oldBatch, "open", "ai", 1);
        SandboxQuest oldTaken = insertQuest(worldId, "【探针】旧批次·已被接取", oldBatch, "taken", "ai", 0);
        // 给它挂上真实的接取人，后面"下架会解除接取关系"才是有意义的断言
        questMapper.update(null, new UpdateWrapper<SandboxQuest>()
                .eq("id", oldTaken.getId())
                .set("taker_id", hero.getId())
                .set("taker_name", HERO));
        // 再来一条"今天刚刷新出来"的，让"最新一批"落在今天
        SandboxQuest newBatch = insertQuest(worldId, "【探针】新批次·今天刷出来的", LocalDateTime.now(), "open", "ai", 0);

        List<SandboxQuest> board = service.questsForAdmin(worldId, "all", "board");
        check("当前板面不列旧批次没人接的委托", !containsTitle(board, "【探针】旧批次·AI 没人接"));
        check("当前板面保留接取中的委托", containsTitle(board, "【探针】旧批次·已被接取"));
        check("当前板面保留近三天已完成的委托", containsTitle(board, "【探针】清除镇外的野狼"));
        check("全部（含历史）里能看到旧批次", containsTitle(service.questsForAdmin(worldId, "all", "all"),
                "【探针】旧批次·AI 没人接"));

        // 模拟"刷新委托板"：把没人接的 AI 委托下架。
        // 注意这里是 2 条：旧批次那条 + 我刚插的"今天"那条——线上是在**写入新批次之前**调用，
        // 那时所有 open 的 AI 委托本来就都属于上一批，所以只会命中上一批。
        int expired = (int) call("expirePreviousBatch", new Class[]{Long.class}, worldId);
        check("刷新时只下架「AI 生成 + 没人接 + 没置顶」的", expired == 2);
        check("AI 没人接的已下架", "expired".equals(questMapper.selectById(oldAi.getId()).getStatus()));
        check("管理员手写的不受影响", "open".equals(questMapper.selectById(oldAdmin.getId()).getStatus()));
        check("置顶的不受影响", "open".equals(questMapper.selectById(oldPinned.getId()).getStatus()));
        check("接取中的不受影响", "taken".equals(questMapper.selectById(oldTaken.getId()).getStatus()));
        check("已下架的能在「已下架」筛选里找到",
                containsTitle(service.questsForAdmin(worldId, "expired", "all"), "【探针】旧批次·AI 没人接"));

        // 重新上板：批次时间要跟到当前，否则回到 open 也上不了板
        service.resetQuest(oldAi.getId());
        SandboxQuest back = questMapper.selectById(oldAi.getId());
        check("重新上板后状态回到可接", "open".equals(back.getStatus()));
        check("重新上板后进到当前批次", back.getBatchTime() != null
                && back.getBatchTime().isAfter(oldBatch));
        check("重新上板后出现在当前板面",
                containsTitle(service.questsForAdmin(worldId, "all", "board"), "【探针】旧批次·AI 没人接"));

        // ---- 「在板上」标记：后台显示的必须和前台真实情况一致（用户就是被这里绕晕的）----
        List<SandboxQuest> boardRows = service.questsForAdmin(worldId, "all", "board");
        check("当前板面里的每一条都标着「在板上」",
                boardRows.stream().allMatch(q -> Boolean.TRUE.equals(q.getOnBoard())));
        check("被换下的委托标着「不在板上」", Boolean.FALSE.equals(onBoardFlag(worldId, newBatch.getId())));
        check("旧批次没人接的委托也标着「不在板上」（状态还是可接）",
                "open".equals(questMapper.selectById(oldAdmin.getId()).getStatus())
                        && Boolean.FALSE.equals(onBoardFlag(worldId, oldAdmin.getId())));
        check("接取中的那条标着「在板上」", Boolean.TRUE.equals(onBoardFlag(worldId, oldTaken.getId())));

        // ---- 手动下架 / 重新上板 ----
        SandboxQuest fresh = service.saveQuest(newQuest(worldId, "【探针】手动下架用", "chore", SAFE_PLACE, null));
        check("新加的委托默认就在板上", Boolean.TRUE.equals(onBoardFlag(worldId, fresh.getId())));
        service.expireQuest(fresh.getId());
        check("手动下架后不在板上", Boolean.FALSE.equals(onBoardFlag(worldId, fresh.getId())));
        check("手动下架后状态为已下架",
                "expired".equals(questMapper.selectById(fresh.getId()).getStatus()));
        service.resetQuest(fresh.getId());
        check("重新上板后又回到板上", Boolean.TRUE.equals(onBoardFlag(worldId, fresh.getId())));
        // 接取中的现在允许下架（这是收拾卡住委托的唯一出口）：会解除接取关系，但不发奖励
        int coinsBeforeExpire = currentCoins(hero.getId());
        service.expireQuest(oldTaken.getId());
        SandboxQuest expiredTaken = questMapper.selectById(oldTaken.getId());
        check("下架接取中的委托会解除接取关系", expiredTaken.getTakerId() == null);
        check("下架接取中的委托不发奖励", currentCoins(hero.getId()) == coinsBeforeExpire);
        check("下架接取中的委托后状态为已下架", "expired".equals(expiredTaken.getStatus()));
    }

    /** 后台列表里某条委托的「在板上」标记 */
    private Boolean onBoardFlag(Long worldId, Long id) {
        return service.questsForAdmin(worldId, "all", "all").stream()
                .filter(q -> q.getId().equals(id)).findFirst()
                .map(SandboxQuest::getOnBoard).orElse(null);
    }

    /**
     * 管理员操作的边界（这几条都是用户实测报出来的坑）：
     *   ① 「重新上板」不能把其他在板上的委托挤下板；
     *   ② 改进度只对"接取中"开放，而且最多 99%；
     *   ③ 没有接取人的委托不会被结算、更不会发奖励（旧的「强制完成」已删除）；
     *   ④ 角色放弃的委托要回到当前批次，不能凭空消失。
     */
    private void testAdminGuards(Long worldId, SandboxCharacter hero) throws Exception {
        // ① 先把 hero 手上那条清掉，顺便验证"下架接取中的"不会发奖励
        SandboxQuest taken = questMapper.selectOne(new QueryWrapper<SandboxQuest>()
                .eq("world_id", worldId).eq("status", "taken").last("limit 1"));
        int coinsBeforeExpire = currentCoins(hero.getId());
        service.expireQuest(taken.getId());
        check("① 下架接取中的委托会解除接取关系",
                questMapper.selectById(taken.getId()).getTakerId() == null);
        check("① 下架接取中的委托不发奖励", currentCoins(hero.getId()) == coinsBeforeExpire);

        // ② 没人接的委托不能改进度
        SandboxQuest open = questMapper.selectOne(new QueryWrapper<SandboxQuest>()
                .eq("world_id", worldId).eq("status", "open").last("limit 1"));
        boolean rejected = false;
        try {
            service.setQuestProgress(open.getId(), 50, "试着改一下");
        } catch (Exception e) {
            rejected = true;
        }
        check("② 没人接的委托不能改进度", rejected);
        boolean expiredRejected = false;
        try {
            service.setQuestProgress(taken.getId(), 50, "试着改一下");
        } catch (Exception e) {
            expiredRejected = true;
        }
        check("② 已下架的委托不能改进度", expiredRejected);

        // 接一条新的：改进度上限 99
        SandboxQuest target = insertQuest(worldId, "【探针】边界·接取后再放弃",
                LocalDateTime.now(), "open", "admin", 1);
        applyQuestActions(hero, obj("quest_take", "【探针】边界·接取后再放弃"));
        check("② 新一批的委托可以正常接取", "taken".equals(questMapper.selectById(target.getId()).getStatus()));
        service.setQuestProgress(target.getId(), 100, "管理员想直接拉满");
        check("② 改进度最多只能到 99",
                Integer.valueOf(99).equals(questMapper.selectById(target.getId()).getProgress()));

        // ③ 没有接取人的委托：结算必须直接失败，且不发奖励
        SandboxQuest noTaker = questMapper.selectOne(new QueryWrapper<SandboxQuest>()
                .eq("world_id", worldId).eq("status", "open").ne("id", target.getId()).last("limit 1"));
        int coinsBefore = currentCoins(hero.getId());
        boolean settled = settleDirectly(noTaker, hero);
        check("③ 没有接取人的委托不会被结算", !settled);
        check("③ 没有接取人的委托不会变成已完成",
                "open".equals(questMapper.selectById(noTaker.getId()).getStatus()));
        check("③ 没有接取人的委托不会发奖励", currentCoins(hero.getId()) == coinsBefore);

        // ④ 放弃后回当前批次（否则会落在旧批次、凭空消失）
        applyQuestActions(hero, obj("quest_abandon", true, "quest_note", "先去做别的"));
        SandboxQuest abandoned = questMapper.selectById(target.getId());
        check("④ 放弃后回到可接", "open".equals(abandoned.getStatus()));
        check("④ 放弃后仍在板上（没有被落到旧批次）",
                Boolean.TRUE.equals(onBoardFlag(worldId, target.getId())));
    }

    /** 直接调私有的 completeQuest，模拟"绕过角色行动去结算一条没人接的委托" */
    private boolean settleDirectly(SandboxQuest quest, SandboxCharacter hero) throws Exception {
        Class<?> resultType = Class.forName("com.bc.bcblog.service.impl.SandboxServiceImpl$QuestActResult");
        Method method = SandboxServiceImpl.class.getDeclaredMethod("completeQuest",
                SandboxQuest.class, SandboxCharacter.class, String.class, LocalDateTime.class, resultType);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        return (Boolean) method.invoke(target == null ? service : target,
                quest, hero, "试着结算", LocalDateTime.now(), null);
    }

    private SandboxQuest insertQuest(Long worldId, String title, LocalDateTime batch, String status,
                                     String source, int pinned) {
        SandboxQuest quest = newQuest(worldId, title, "chore", SAFE_PLACE, null);
        quest.setBatchTime(batch);
        quest.setStatus(status);
        quest.setSource(source);
        quest.setPinned(pinned);
        quest.setCreateTime(batch);
        return service.saveQuest(quest);
    }

    private boolean containsTitle(List<SandboxQuest> list, String title) {
        return list.stream().anyMatch(q -> title.equals(q.getTitle()));
    }

    /** 反射调用服务里的私有方法（探针专用） */
    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        return method.invoke(target == null ? service : target, args);
    }

    // ---------------- 以下都是探针的脚手架 ----------------

    private Long setUpWorld() {
        SandboxWorld world = new SandboxWorld();
        world.setName(WORLD_NAME);
        world.setDescription("（探针自动创建，跑完即删）");
        world.setWorldPrompt("剑与魔法的世界。");
        world.setEnabled(0);
        world.setPortalVisible(0);
        world.setMapImage("");
        worldMapper.insert(world);
        insertLocation(world.getId(), SAFE_PLACE, 20, 20, 1, 15, 30);
        insertLocation(world.getId(), FAR_PLACE, 80, 80, 3, 40, 60);
        return world.getId();
    }

    private void insertLocation(Long worldId, String name, int x, int y, int danger, int powerMin, int powerMax) {
        SandboxLocation location = new SandboxLocation();
        location.setWorldId(worldId);
        location.setName(name);
        location.setX(x);
        location.setY(y);
        location.setWidth(10);
        location.setHeight(10);
        location.setDangerLevel(danger);
        location.setPowerMin(powerMin);
        location.setPowerMax(powerMax);
        location.setSortOrder(0);
        locationMapper.insert(location);
    }

    private SandboxCharacter setUpCharacter(Long worldId) {
        SandboxCharacter hero = new SandboxCharacter();
        hero.setWorldId(worldId);
        hero.setName(HERO);
        hero.setTitle("测试者");
        hero.setPersona("（探针角色）");
        hero.setAppearance("");
        hero.setX(22);
        hero.setY(22);
        hero.setLocationName(SAFE_PLACE);
        hero.setSubLocation("镇口");
        hero.setStatusJson("{\"体力\":90,\"魔力\":60,\"饥饿度\":20,\"心情\":\"平静\",\"伤势\":\"无恙\"}");
        hero.setCoins(0);
        hero.setCombatPower(12);
        hero.setEnabled(1);
        hero.setTemperature(new java.math.BigDecimal("0.9"));
        hero.setIntervalMin(45);
        hero.setIntervalMax(75);
        characterMapper.insert(hero);
        return hero;
    }

    private SandboxQuest newQuest(Long worldId, String title, String type, String location, Integer power) {
        SandboxQuest quest = new SandboxQuest();
        quest.setWorldId(worldId);
        // 不设批次时间：交给 saveQuest 按线上规则挂到"当前这一批"（没有批次才用现在）。
        // 以前这里写 now()，而 batch_time 存库精度是秒——跨秒创建的委托各自成一批，
        // "最新一批"就只剩最后一条，探针偶尔会误报（跑得慢一点就中招）
        quest.setBatchTime(null);
        quest.setTitle(title);
        quest.setDescription("（探针委托）");
        quest.setQuestType(type);
        quest.setDifficulty(2);
        quest.setLocationName(location);
        quest.setTarget("（探针目标）");
        quest.setPower(power);
        quest.setRewardCoins(10);
        quest.setProgress(0);
        quest.setStatus("open");
        quest.setSource("admin");
        quest.setPinned(0);
        quest.setEnabled(1);
        return quest;
    }

    private JSONObject obj(Object... kv) {
        JSONObject json = new JSONObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            json.set(String.valueOf(kv[i]), kv[i + 1]);
        }
        return json;
    }

    private void applyQuestActions(SandboxCharacter hero, JSONObject obj) throws Exception {
        applyQuestActions(hero, hero.getLocationName(), obj);
    }

    private void applyQuestActions(SandboxCharacter hero, String locationName, JSONObject obj) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod("applyQuestActions",
                SandboxCharacter.class, JSONObject.class, String.class, LocalDateTime.class,
                SandboxAct.class, boolean.class);
        method.setAccessible(true);
        // 传一个临时 act：自检修正会改写它的 actions/summary，探针不落库也无所谓
        SandboxAct act = new SandboxAct();
        act.setLocationName(locationName);
        act.setSubLocation(hero.getSubLocation());
        act.setActions("（探针步骤）");
        act.setSummary("（探针步骤）");
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        method.invoke(target == null ? service : target, hero, obj, locationName, LocalDateTime.now(), act, true);
    }

    /** 调一次私有的 appendQuestPrompt，拿到"这一步会注入给 AI 的委托段落" */
    private String appendQuestPrompt(SandboxCharacter hero) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod("appendQuestPrompt",
                StringBuilder.class, SandboxCharacter.class);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        StringBuilder sb = new StringBuilder();
        method.invoke(target == null ? service : target, sb, hero);
        return sb.toString();
    }

    private int currentCoins(Long characterId) {
        SandboxCharacter fresh = characterMapper.selectById(characterId);
        return fresh.getCoins() == null ? 0 : fresh.getCoins();
    }

    private int backpackSize(Long characterId) {
        List<SandboxItem> items = itemMapper.selectList(new QueryWrapper<SandboxItem>()
                .eq("character_id", characterId));
        return items.size();
    }

    /** 背包里某个物品的品质（找不到返回 -1），用来核对"委托奖励的品质有没有真的带进背包" */
    private int backpackRarity(Long characterId, String name) {
        SandboxItem item = itemMapper.selectOne(new QueryWrapper<SandboxItem>()
                .eq("character_id", characterId).eq("name", name).last("limit 1"));
        return item == null || item.getRarity() == null ? -1 : item.getRarity();
    }

    private boolean hasQuestCoinLog(Long characterId) {
        return coinLogMapper.selectCount(new QueryWrapper<SandboxCoinLog>()
                .eq("character_id", characterId).eq("type", "quest")) > 0;
    }

    private void check(String what, boolean ok) {
        if (ok) {
            passed++;
            report.add("- ✅ " + what);
            System.out.println("  ✅ " + what);
        } else {
            report.add("- ❌ " + what);
            writeReport("失败：" + what);
            throw new AssertionError("探针失败：" + what);
        }
    }

    /** 把探针结果写成 UTF-8 报告，方便在编辑器里核对 */
    private void writeReport(String title) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("target", "probe");
            java.nio.file.Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder("# 旅人委托探针报告\n\n");
            sb.append("结果：").append(title).append("\n\n");
            for (String line : report) {
                sb.append(line).append("\n");
            }
            java.nio.file.Files.write(dir.resolve("quest-probe.md"),
                    sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("报告：target/probe/quest-probe.md");
        } catch (Exception ignored) {
            // 写报告失败不影响探针本身
        }
    }

    /** 只删这个临时世界的数据，按 world_id 兜底清一遍 */
    private void cleanUp(Long worldId) {
        questMapper.delete(new QueryWrapper<SandboxQuest>().eq("world_id", worldId));
        actMapper.delete(new QueryWrapper<SandboxAct>().eq("world_id", worldId));
        itemMapper.delete(new QueryWrapper<SandboxItem>().eq("world_id", worldId));
        coinLogMapper.delete(new QueryWrapper<SandboxCoinLog>().eq("world_id", worldId));
        characterMapper.delete(new QueryWrapper<SandboxCharacter>().eq("world_id", worldId));
        locationMapper.delete(new QueryWrapper<SandboxLocation>().eq("world_id", worldId));
        worldMapper.deleteById(worldId);
    }
}
