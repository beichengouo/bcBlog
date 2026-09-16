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

    void saveSettings(SandboxSettingVO vo);

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

    /** 定时任务入口：逐个检查到期角色并执行 */
    void runScheduled();

    /** 管理员一键让全部启用角色行动一轮（不受夜间静默与每日上限限制，方便观察角色互动） */
    SandboxRunAllVO runAll();
    /** 让某个世界里所有启用的角色立刻各行动一次 */
    SandboxRunAllVO runAll(Long worldId);

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
    PageResult<SandboxMemory> memoryPage(Long characterId, long page, long size);

    /** 新增/修改一条记忆（管理员可直接编辑角色记忆） */
    void saveMemory(SandboxMemory memory);

    void deleteMemory(Long id);

    /** 定时任务入口：到点后为当天有行动的角色生成记忆总结 */
    void summarizeDaily();

    /** 手动补生成指定日期（yyyy-MM-dd）的记忆，用于修正或测试；同一天会覆盖已有记录 */
    void summarizeOn(String date);

    // ---------------- 背包 ----------------

    List<SandboxItem> items(Long characterId);

    SandboxItem saveItem(SandboxItem item);

    void deleteItem(Long id);

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
}
