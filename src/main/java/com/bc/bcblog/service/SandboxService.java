package com.bc.bcblog.service;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.dto.SandboxCharacterGenerateDTO;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxCoinLog;
import com.bc.bcblog.entity.SandboxInteraction;
import com.bc.bcblog.entity.SandboxItem;
import com.bc.bcblog.entity.SandboxLocation;
import com.bc.bcblog.entity.SandboxMemory;
import com.bc.bcblog.entity.SandboxNews;
import com.bc.bcblog.entity.SandboxQuest;
import com.bc.bcblog.entity.SandboxRelation;
import com.bc.bcblog.entity.SandboxShopItem;
import com.bc.bcblog.entity.SandboxShopOrder;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.vo.SandboxPortalVO;
import com.bc.bcblog.vo.SandboxCharacterDraftVO;
import com.bc.bcblog.vo.SandboxCoinResultVO;
import com.bc.bcblog.vo.SandboxRelationVO;
import com.bc.bcblog.vo.SandboxRunAllVO;
import com.bc.bcblog.vo.SandboxSettingVO;

import java.util.List;
import java.util.Map;

/** 沙盒世界服务：地图、角色、AI 行动与旅人低语。 */
public interface SandboxService {

    // ---------------- 世界与地图 ----------------

    /** 指定世界；worldId 为空时取第一个世界（没有则返回一个空的默认世界） */
    SandboxWorld world(Long worldId);

    /** 全部世界（后台世界管理用） */
    List<SandboxWorld> worlds();

    /** 前台可见的世界（portal_visible = 1，含已停止运行的，方便游客只看历史） */
    List<SandboxWorld> visibleWorlds();

    void saveWorld(SandboxWorld world);

    /** 删除世界：连同它的角色、地点、行动、记忆、背包、好感度、纪闻、低语、金币流水一起清掉 */
    void deleteWorld(Long worldId);

    /** 切换「是否运行」：关闭后该世界的角色不再自动行动（前台可以只看历史） */
    void setWorldEnabled(Long worldId, Integer enabled);

    /** 切换「前台是否可见」：关闭后前台世界下拉里不再出现 */
    void setWorldVisible(Long worldId, Integer visible);

    /** 某个世界的地图地点；worldId 为空时取第一个世界 */
    List<SandboxLocation> locations(Long worldId);

    SandboxLocation saveLocation(SandboxLocation location);

    void deleteLocation(Long id);

    // ---------------- 运行参数 ----------------

    SandboxSettingVO settings();

    /** 指定世界的沙盒参数（不传世界 = 全局值） */
    SandboxSettingVO settings(Long worldId);

    void saveSettings(SandboxSettingVO vo);

    /** 集市管理页专用：只写集市相关配置（世界运行参数仍归超级管理员） */
    void saveShopSettings(SandboxSettingVO vo);

    /** 行动日志页的「旅人纪闻设置」专用：只写纪闻相关配置 */
    void saveNewsSettings(SandboxSettingVO vo);

    /** 委托板管理页专用：只写委托相关配置（世界运行参数仍归超级管理员） */
    void saveQuestSettings(SandboxSettingVO vo);

    // ---------------- 角色 ----------------

    /** 某个世界的角色；worldId 为空时取第一个世界 */
    List<SandboxCharacter> characters(Long worldId);

    SandboxCharacter saveCharacter(SandboxCharacter character);

    /** AI 一键创作角色草稿：结合当前世界观、地图地点与已有角色生成，供「新增角色」表单填充 */
    SandboxCharacterDraftVO generateCharacter(SandboxCharacterGenerateDTO dto, Long worldId);

    void deleteCharacter(Long id);

    // ---------------- 行动记录 ----------------

    /** 行动记录（可按角色、按一级地点筛选；locationName 为空表示全部） */
    PageResult<SandboxAct> acts(Long characterId, String locationName, long page, long size);

    /** 行动记录：可再按世界过滤（前台/后台切换世界时用） */
    PageResult<SandboxAct> acts(Long characterId, String locationName, Long worldId, long page, long size);

    void deleteAct(Long id);

    /** 让指定角色执行一次 AI 行动，manual=true 表示管理员手动触发（不占用每日额度） */
    SandboxAct runOnce(Long characterId, boolean manual);

    /** 管理员手动解除某个角色的执行锁（正常执行完会自动释放；这个是卡住时的应急出口） */
    void unlockCharacter(Long characterId);

    /** 定时任务入口：逐个检查到期角色并执行 */
    void runScheduled();

    /** 管理员一键让全部启用角色行动一轮（不受夜间静默与每日上限限制，方便观察角色互动） */
    SandboxRunAllVO runAll();
    /** 让某个世界里所有启用的角色立刻各行动一次 */
    SandboxRunAllVO runAll(Long worldId);

    /** 查询「全员行动一轮」的进度（异步执行，前端轮询） */
    SandboxRunAllVO runAllProgress();

    // ---------------- 旅人低语 ----------------

    PageResult<SandboxInteraction> interactions(Long characterId, long page, long size);

    void deleteInteraction(Long id);

    /** 前台留言：必须登录，扣除积分 */
    SandboxInteraction whisper(Long characterId, String content);

    // ---------------- 金币 ----------------

    /** 金币流水（后台可按角色筛选） */
    PageResult<SandboxCoinLog> coinLogs(Long characterId, long page, long size);

    void deleteCoinLog(Long id);

    /** 用积分给角色贡献金币：必须登录，按后台配置的比例换算 */
    SandboxCoinResultVO contributeCoins(Long characterId, int points);

    /**
     * 金币对账修复：以金币流水为准重算角色余额，并把每条流水的"当时余额"按顺序重算。
     * 用于修复历史上因"整值回写"造成的余额丢失。
     */
    Map<String, Object> repairCoins(Long worldId);

    // ---------------- 好感度 ----------------

    /** 某个角色与其他角色的关系（前台展示，含双向好感度） */
    List<SandboxRelationVO> relationsOf(Long characterId);

    /** 好感度列表（后台，characterId 为空时查全部） */
    List<SandboxRelationVO> relationList(Long characterId);

    /** 新增或修改一条好感度（管理员） */
    void saveRelation(SandboxRelation relation);

    void deleteRelation(Long id);

    // ---------------- 每日记忆 ----------------

    /** 记忆列表（后台，characterId 为空时查全部） */
    /** 记忆分页；date 传 yyyy-MM-dd 时只看那一天的（用于确认"某天到底生成没有"） */
    PageResult<SandboxMemory> memoryPage(Long characterId, String date, long page, long size);

    /** 新增/修改一条记忆（管理员可直接编辑角色记忆） */
    void saveMemory(SandboxMemory memory);

    void deleteMemory(Long id);

    /** 定时任务入口：到点后为当天有行动的角色生成记忆总结 */
    void summarizeDaily();

    /**
     * 手动补生成指定日期（yyyy-MM-dd，空 = 今天）的记忆，用于补昨天没生成的那种情况；
     * 同一天会覆盖已有记录。
     *
     * @return 实际写入 / 覆盖的记忆条数（那天没有行动的角色不计入）
     */
    int summarizeOn(String date);

    // ---------------- 背包 ----------------

    List<SandboxItem> items(Long characterId);

    SandboxItem saveItem(SandboxItem item);

    void deleteItem(Long id);

    // ---------------- 装备栏 ----------------

    /** 后台：装备（equipped=1）或卸下（equipped=0）某件物品，服务端会校验槽位与「拿不动」并重算装备加成 */
    SandboxItem setEquip(Long itemId, Integer equipped);

    /** 后台：修复破损装备（去「破损的」前缀、清破损标记、按品质区间补回加成） */
    SandboxItem repairItem(Long itemId);

    /** 按装备栏重算某个角色的装备加成合计并写回，返回合计 */
    int recalcEquipPower(Long characterId);

    // ---------------- 旅人纪闻 ----------------

    /** 前台当天的纪闻列表（按置顶与重要度排序） */
    /** 某个世界当天的旅人纪闻 */
    List<SandboxNews> todayNews(Long worldId);

    /** 后台分页查询：date 为空时查当天，传 all 查全部 */
    PageResult<SandboxNews> newsPage(String date, long page, long size);
    /** 旅人纪闻列表：可再按世界过滤 */
    PageResult<SandboxNews> newsPage(String date, long page, long size, Long worldId);

    void saveNews(SandboxNews news);

    void deleteNews(Long id);

    /** 由 AI 生成若干条纪闻，返回实际新增条数 */
    int generateNews(Integer count, Long providerId, String model, Long worldId);

    /** 定时任务入口：按后台配置自动生成当天纪闻（失败只记日志，不抛异常） */
    void autoGenerateNews(Long worldId);

    /** 按配置的「刷新间隔 + 当天首次时间」自动生成纪闻（定时任务调用），返回刷新了几个世界 */
    int autoRefreshNews();

    // ---------------- 前台聚合 ----------------

    SandboxPortalVO portal(Long worldId);

    // ---------------- 旅人集市 ----------------

    /** 当前世界最新一批商品（前台用，带赠送记录；已下架的不返回） */
    List<SandboxShopItem> shopItems(Long worldId);

    /** 后台：最新一批商品（含已下架的，方便管理） */
    List<SandboxShopItem> shopItemsForAdmin(Long worldId);

    SandboxShopItem saveShopItem(SandboxShopItem item);

    void deleteShopItem(Long id);

    /** 立即生成一批新商品，返回新增条数 */
    int generateShopItems(Integer count, Long providerId, String model, Long worldId);

    /** 按配置的间隔自动刷新（定时任务调用），返回刷新了几个世界 */
    int autoRefreshShop();

    /** 购买并赠送给角色：扣积分、扣库存、进角色背包、写礼物与购买记录 */
    SandboxShopOrder buyShopItem(Long itemId, Long characterId);

    /** 购买记录分页 */
    PageResult<SandboxShopOrder> shopOrders(Long worldId, long page, long size);

    /** 今日集市统计：卖出件数 / 回收积分 */
    Map<String, Object> shopStats(Long worldId);

    // ---------------- 旅人委托板 ----------------

    /**
     * 前台委托板：最新一批可接 + 所有接取中 + 近三天已完成。
     * characterId 传了就给每条算出"离这个角色多少公里"，并标记他曾经放弃过哪几条。
     */
    List<SandboxQuest> questBoard(Long worldId, Long characterId);

    /**
     * 后台委托列表。
     * scope = board（默认）：只看"当前板面"——最新一批可接 + 所有接取中 + 近三天已完成，和前台完全一致；
     * scope = all：连旧批次没人接的、以及被下架的委托一起看（排查历史用）。
     * status 传具体状态（open/taken/completed/expired）时再按状态筛一层。
     */
    List<SandboxQuest> questsForAdmin(Long worldId, String status, String scope);

    /** 后台列表（按"当前板面"口径，等价于 scope = board） */
    List<SandboxQuest> questsForAdmin(Long worldId, String status);

    SandboxQuest saveQuest(SandboxQuest quest);

    void deleteQuest(Long id);

    /** 立即生成一批委托，返回新增条数（新生成条数 = 委托板总数 − 接取中条数） */
    int generateQuests(Integer count, Long providerId, String model, Long worldId);

    /** 按配置的「刷新间隔 + 当天首次时间」自动刷新委托板（定时任务调用），返回刷新了几个世界 */
    int autoRefreshQuests();

    /** 后台：重置回「可接」（清接取人、进度清零；已发过的奖励不回收） */
    SandboxQuest resetQuest(Long id);

    /**
     * 后台：下架（status = expired，等价于"刷新时被换下来"）。
     * 对「接取中」的委托也允许——会同时解除接取关系，用于收拾卡住的委托；不结算奖励。
     */
    SandboxQuest expireQuest(Long id);

    /** 后台：手动改进度与说明（只有「接取中」可改；只能往上调，最多到 99） */
    SandboxQuest setQuestProgress(Long id, Integer progress, String note);

    // ---------------- 存档 ----------------

    /** 导出存档：返回 zip 字节流（world.json + 引用到的图片） */
    byte[] exportWorld(Long worldId, boolean includeRawResponse);

    /** 清空世界进度：删除该世界的全部记录，并把角色状态与位置恢复默认（世界设定、地点、角色卡保留） */
    Map<String, Object> resetWorld(Long worldId);

    /**
     * 导入存档。
     * @param file          上传的存档（zip 或 world.json）
     * @param targetWorldId 覆盖模式下的目标世界；新建模式传 null
     * @param overwrite     true = 覆盖指定世界（先清空再写入），false = 导入成一个新世界
     */
    Map<String, Object> importWorld(org.springframework.web.multipart.MultipartFile file,
                                    Long targetWorldId, boolean overwrite);
}
