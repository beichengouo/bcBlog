package com.bc.bcblog.service;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxCoinLog;
import com.bc.bcblog.entity.SandboxInteraction;
import com.bc.bcblog.entity.SandboxLocation;
import com.bc.bcblog.entity.SandboxRelation;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.vo.SandboxPortalVO;
import com.bc.bcblog.vo.SandboxCoinResultVO;
import com.bc.bcblog.vo.SandboxRelationVO;
import com.bc.bcblog.vo.SandboxRunAllVO;
import com.bc.bcblog.vo.SandboxSettingVO;

import java.util.List;

/** 沙盒世界服务：地图、角色、AI 行动与旅人低语。 */
public interface SandboxService {

    // ---------------- 世界与地图 ----------------

    /** 当前世界（取第一个，没有则返回一个空的默认世界） */
    SandboxWorld world();

    void saveWorld(SandboxWorld world);

    List<SandboxLocation> locations();

    SandboxLocation saveLocation(SandboxLocation location);

    void deleteLocation(Long id);

    // ---------------- 运行参数 ----------------

    SandboxSettingVO settings();

    void saveSettings(SandboxSettingVO vo);

    // ---------------- 角色 ----------------

    List<SandboxCharacter> characters();

    SandboxCharacter saveCharacter(SandboxCharacter character);

    void deleteCharacter(Long id);

    // ---------------- 行动记录 ----------------

    PageResult<SandboxAct> acts(Long characterId, long page, long size);

    void deleteAct(Long id);

    /** 让指定角色执行一次 AI 行动，manual=true 表示管理员手动触发（不占用每日额度） */
    SandboxAct runOnce(Long characterId, boolean manual);

    /** 定时任务入口：逐个检查到期角色并执行 */
    void runScheduled();

    /** 管理员一键让全部启用角色行动一轮（不受夜间静默与每日上限限制，方便观察角色互动） */
    SandboxRunAllVO runAll();

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

    // ---------------- 前台聚合 ----------------

    SandboxPortalVO portal();
}
