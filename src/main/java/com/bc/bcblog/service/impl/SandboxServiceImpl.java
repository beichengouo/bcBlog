package com.bc.bcblog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.component.SensitiveWordFilter;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxCoinLog;
import com.bc.bcblog.entity.SandboxInteraction;
import com.bc.bcblog.entity.SandboxLocation;
import com.bc.bcblog.entity.SandboxRelation;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SandboxActMapper;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxCoinLogMapper;
import com.bc.bcblog.mapper.SandboxInteractionMapper;
import com.bc.bcblog.mapper.SandboxLocationMapper;
import com.bc.bcblog.mapper.SandboxRelationMapper;
import com.bc.bcblog.mapper.SandboxWorldMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.AiProviderService;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.PointService;
import com.bc.bcblog.service.SandboxService;
import com.bc.bcblog.vo.SandboxCharacterVO;
import com.bc.bcblog.vo.SandboxCoinResultVO;
import com.bc.bcblog.vo.SandboxPortalVO;
import com.bc.bcblog.vo.SandboxRelationVO;
import com.bc.bcblog.vo.SandboxRunAllVO;
import com.bc.bcblog.vo.SandboxSettingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 沙盒世界服务实现。
 *
 * 提示词思路参考酒馆（SillyTavern）：角色卡 + 世界书 + 当前状态 + 最近记忆，四段拼装，
 * 并要求 AI 只输出一个 JSON，服务端解析后落库，前台据此更新角色位置与时间线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxServiceImpl implements SandboxService {

    /** 拼提示词时携带的最近行动条数，控制 token 消耗 */
    private static final int PROMPT_ACT_LIMIT = 12;
    /** 前台角色档案面板展示的最近行动条数 */
    private static final int PORTAL_ACT_LIMIT = 5;
    /** 前台角色档案面板展示的最近金币流水条数 */
    private static final int PORTAL_COIN_LOG_LIMIT = 5;
    /** 拼提示词时携带的旅人低语条数 */
    private static final int PROMPT_WHISPER_LIMIT = 5;
    /** 提示词里默认的世界设定，管理员没填时使用 */
    private static final String DEFAULT_WORLD_PROMPT =
            "这是一个剑与魔法的幻想世界。有村庄、森林、魔法塔、遗迹与旅人酒馆；"
                    + "普通人过着平凡的生活，冒险者与魔法使则在各地旅行。世界整体平静但偶尔有异变。";
    /** 提示词里默认的角色要求，管理员没填人设时使用 */
    private static final String DEFAULT_PERSONA =
            "（管理员暂未填写人设，请根据角色名字与这个世界观，自行合理设定一个性格鲜明的角色）";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 需要统一成 0~100 数值的标准状态项 */
    private static final List<String> STANDARD_STATUS_KEYS = Arrays.asList("体力", "魔力", "饥饿度", "饱食度");
    /** 好感度上下限 */
    private static final int FAVOR_MIN = -100;
    private static final int FAVOR_MAX = 100;
    /** 单次行动好感度变化上限，避免 AI 一次给出夸张数值 */
    private static final int FAVOR_STEP_MAX = 20;

    private final SandboxWorldMapper worldMapper;
    private final SandboxLocationMapper locationMapper;
    private final SandboxCharacterMapper characterMapper;
    private final SandboxActMapper actMapper;
    private final SandboxInteractionMapper interactionMapper;
    private final SandboxCoinLogMapper coinLogMapper;
    private final SandboxRelationMapper relationMapper;
    private final AiProviderService aiProviderService;
    private final ConfigService configService;
    private final PointService pointService;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final SysUserMapper userMapper;

    private final Random random = new Random();

    // ============================== 世界与地图 ==============================

    @Override
    public SandboxWorld world() {
        SandboxWorld w = worldMapper.selectOne(new LambdaQueryWrapper<SandboxWorld>()
                .orderByAsc(SandboxWorld::getId)
                .last("limit 1"));
        if (w == null) {
            // 还没有创建过世界时，返回一个空壳给后台表单使用
            w = new SandboxWorld();
            w.setName("");
            w.setEnabled(1);
        }
        return w;
    }

    @Override
    public void saveWorld(SandboxWorld world) {
        if (world.getName() == null) {
            world.setName("");
        }
        if (world.getEnabled() == null) {
            world.setEnabled(1);
        }
        if (world.getId() == null) {
            worldMapper.insert(world);
        } else {
            worldMapper.updateById(world);
        }
    }

    @Override
    public List<SandboxLocation> locations() {
        return locationMapper.selectList(new LambdaQueryWrapper<SandboxLocation>()
                .orderByAsc(SandboxLocation::getSortOrder)
                .orderByAsc(SandboxLocation::getId));
    }

    @Override
    public SandboxLocation saveLocation(SandboxLocation location) {
        if (location.getName() == null || location.getName().trim().isEmpty()) {
            throw new BusinessException("地点名称不能为空");
        }
        location.setName(location.getName().trim());
        // 只对显式传入的坐标做范围修正，避免部分更新时把已有坐标重置
        if (location.getX() != null) {
            location.setX(clamp(location.getX()));
        }
        if (location.getY() != null) {
            location.setY(clamp(location.getY()));
        }
        if (location.getId() == null) {
            location.setX(location.getX() == null ? 50 : location.getX());
            location.setY(location.getY() == null ? 50 : location.getY());
            if (location.getSortOrder() == null) {
                location.setSortOrder(0);
            }
            if (location.getWorldId() == null) {
                location.setWorldId(worldId());
            }
            locationMapper.insert(location);
        } else {
            locationMapper.updateById(location);
        }
        return location;
    }

    @Override
    public void deleteLocation(Long id) {
        locationMapper.deleteById(id);
    }

    // ============================== 运行参数 ==============================

    @Override
    public SandboxSettingVO settings() {
        SandboxSettingVO vo = new SandboxSettingVO();
        vo.setEnabled(configService.getConfigValue("sandbox_enabled", "0"));
        vo.setIntervalMin(configService.getConfigValue("sandbox_interval_min", "45"));
        vo.setIntervalMax(configService.getConfigValue("sandbox_interval_max", "75"));
        vo.setNightStart(configService.getConfigValue("sandbox_night_start", "02:00"));
        vo.setNightEnd(configService.getConfigValue("sandbox_night_end", "07:00"));
        vo.setDailyLimit(configService.getConfigValue("sandbox_daily_limit", "12"));
        vo.setWhisperPoints(configService.getConfigValue("sandbox_whisper_points", "1"));
        vo.setVerifyEnabled(configService.getConfigValue("sandbox_verify_enabled", "0"));
        vo.setBatchWindowMinutes(configService.getConfigValue("sandbox_batch_window_minutes", "5"));
        vo.setChainMaxDepth(configService.getConfigValue("sandbox_chain_max_depth", "1"));
        vo.setChainLimitPerRound(configService.getConfigValue("sandbox_chain_limit_per_round", "3"));
        vo.setReactionCooldownMinutes(configService.getConfigValue("sandbox_reaction_cooldown_minutes", "15"));
        return vo;
    }

    @Override
    public void saveSettings(SandboxSettingVO vo) {
        writeSetting("sandbox_enabled", vo.getEnabled());
        writeSetting("sandbox_interval_min", vo.getIntervalMin());
        writeSetting("sandbox_interval_max", vo.getIntervalMax());
        writeSetting("sandbox_night_start", vo.getNightStart());
        writeSetting("sandbox_night_end", vo.getNightEnd());
        writeSetting("sandbox_daily_limit", vo.getDailyLimit());
        writeSetting("sandbox_whisper_points", vo.getWhisperPoints());
        writeSetting("sandbox_verify_enabled", vo.getVerifyEnabled());
        writeSetting("sandbox_batch_window_minutes", vo.getBatchWindowMinutes());
        writeSetting("sandbox_chain_max_depth", vo.getChainMaxDepth());
        writeSetting("sandbox_chain_limit_per_round", vo.getChainLimitPerRound());
        writeSetting("sandbox_reaction_cooldown_minutes", vo.getReactionCooldownMinutes());
    }

    private void writeSetting(String key, String value) {
        if (value == null) {
            return;
        }
        configService.setConfigValue(key, value.trim());
    }

    // ============================== 角色 ==============================

    @Override
    public List<SandboxCharacter> characters() {
        return characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>()
                .orderByAsc(SandboxCharacter::getId));
    }

    @Override
    public SandboxCharacter saveCharacter(SandboxCharacter character) {
        if (character.getName() == null || character.getName().trim().isEmpty()) {
            throw new BusinessException("角色名不能为空");
        }
        character.setName(character.getName().trim());
        // 只对显式传入的值做范围修正，避免部分更新（例如只切换启用状态）时覆盖其它字段
        if (character.getX() != null) {
            character.setX(clamp(character.getX()));
        }
        if (character.getY() != null) {
            character.setY(clamp(character.getY()));
        }
        if (character.getTemperature() != null) {
            if (character.getTemperature().compareTo(BigDecimal.ZERO) < 0) {
                character.setTemperature(BigDecimal.ZERO);
            }
            if (character.getTemperature().compareTo(new BigDecimal("2")) > 0) {
                character.setTemperature(new BigDecimal("2"));
            }
        }
        if (character.getIntervalMin() != null && character.getIntervalMin() < 1) {
            character.setIntervalMin(1);
        }
        if (character.getIntervalMin() != null && character.getIntervalMax() != null
                && character.getIntervalMax() < character.getIntervalMin()) {
            character.setIntervalMax(character.getIntervalMin());
        }
        if (character.getId() == null) {
            // 新建角色时才补齐默认值
            character.setX(character.getX() == null ? 50 : character.getX());
            character.setY(character.getY() == null ? 50 : character.getY());
            if (character.getWorldId() == null) {
                character.setWorldId(worldId());
            }
            if (character.getTemperature() == null) {
                character.setTemperature(new BigDecimal("0.90"));
            }
            if (character.getIntervalMin() == null) {
                character.setIntervalMin(45);
            }
            if (character.getIntervalMax() == null) {
                character.setIntervalMax(75);
            }
            if (character.getEnabled() == null) {
                character.setEnabled(1);
            }
            character.setLastError(null);
            character.setNextRunTime(nextRunTime(character, LocalDateTime.now()));
            characterMapper.insert(character);
        } else {
            SandboxCharacter before = characterMapper.selectById(character.getId());
            characterMapper.updateById(character);
            // 管理员直接改金币时补一条流水，方便以后追溯
            if (before != null && character.getCoins() != null) {
                int oldCoins = before.getCoins() == null ? 0 : before.getCoins();
                if (character.getCoins() != oldCoins) {
                    addCoinLog(character.getId(), null, null, "admin",
                            character.getCoins() - oldCoins, 0, character.getCoins(), "管理员调整金币");
                }
            }
        }
        return character;
    }

    @Override
    public void deleteCharacter(Long id) {
        // 角色删除时一并清理它的行动记录、低语与金币流水，避免留下孤儿数据
        actMapper.delete(new LambdaQueryWrapper<SandboxAct>().eq(SandboxAct::getCharacterId, id));
        interactionMapper.delete(new LambdaQueryWrapper<SandboxInteraction>()
                .eq(SandboxInteraction::getCharacterId, id));
        coinLogMapper.delete(new LambdaQueryWrapper<SandboxCoinLog>()
                .eq(SandboxCoinLog::getCharacterId, id));
        // 角色之间的好感度两个方向都要清掉
        relationMapper.delete(new LambdaQueryWrapper<SandboxRelation>()
                .eq(SandboxRelation::getCharacterId, id)
                .or()
                .eq(SandboxRelation::getTargetId, id));
        characterMapper.deleteById(id);
    }

    // ============================== 行动记录 ==============================

    @Override
    public PageResult<SandboxAct> acts(Long characterId, long page, long size) {
        LambdaQueryWrapper<SandboxAct> wrapper = new LambdaQueryWrapper<SandboxAct>()
                .eq(characterId != null, SandboxAct::getCharacterId, characterId)
                .orderByDesc(SandboxAct::getCreateTime)
                .orderByDesc(SandboxAct::getId);
        IPage<SandboxAct> result = actMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public void deleteAct(Long id) {
        actMapper.deleteById(id);
    }

    @Override
    public SandboxAct runOnce(Long characterId, boolean manual) {
        // 管理员点「立即执行」：第一次行动失败时直接把原因抛给后台，成功后继续处理回应回合
        Set<Long> planned = new HashSet<>();
        planned.add(characterId);
        SandboxAct act = runOnce(characterId, manual, false, null);
        triggerReactions(act, 0, planned, new AtomicInteger());
        return act;
    }

    /**
     * 执行一次行动并处理回应链（调度用：单个角色失败只记日志，不影响其他角色）。
     */
    private SandboxAct runWithChain(Long characterId, boolean manual, boolean reaction, SandboxAct trigger,
                                    int depth, Set<Long> planned, AtomicInteger reactionCount) {
        SandboxAct act;
        try {
            act = runOnce(characterId, manual, reaction, trigger);
        } catch (Exception e) {
            log.warn("沙盒角色 #{} 行动失败：{}", characterId, e.getMessage());
            return null;
        }
        triggerReactions(act, depth, planned, reactionCount);
        return act;
    }

    /**
     * 根据互动结果，让被互动的角色立即行动一次（回应回合）。
     *
     * 四层防循环：
     *   1. 深度上限 sandbox_chain_max_depth（默认 1，只回应一轮）；
     *   2. 每轮次数上限 sandbox_chain_limit_per_round（默认 3）；
     *   3. 冷却时间 sandbox_reaction_cooldown_minutes（默认 15 分钟，刚行动过的不再立即回应）；
     *   4. planned 记录本轮已排队的角色，避免同一角色连着行动两次。
     */
    private void triggerReactions(SandboxAct act, int depth, Set<Long> planned, AtomicInteger reactionCount) {
        if (act == null) {
            return;
        }
        int maxDepth = Math.max(0, intConfig("sandbox_chain_max_depth", 1));
        if (maxDepth == 0 || depth >= maxDepth) {
            return;
        }
        String companions = act.getCompanions();
        if (companions == null || companions.trim().isEmpty()) {
            return;
        }
        int limit = Math.max(1, intConfig("sandbox_chain_limit_per_round", 3));
        int cooldown = Math.max(0, intConfig("sandbox_reaction_cooldown_minutes", 15));
        List<SandboxCharacter> worldCharacters = otherCharacters(act.getCharacterId());
        LocalDateTime now = LocalDateTime.now();
        for (String raw : companions.split("、")) {
            SandboxCharacter target = findByName(worldCharacters, raw.trim());
            if (target == null) {
                continue;
            }
            if (reactionCount.get() >= limit) {
                log.info("沙盒本轮回应次数已达上限（{}），其余互动不再立即回应", limit);
                return;
            }
            if (planned.contains(target.getId())) {
                // 对方本轮本来就要行动，不重复触发
                continue;
            }
            if (target.getEnabled() == null || target.getEnabled() != 1) {
                continue;
            }
            if (inNight(now.toLocalTime()) || dailyLimitReached(target.getId())) {
                continue;
            }
            if (cooldown > 0 && target.getLastRunTime() != null
                    && target.getLastRunTime().isAfter(now.minusMinutes(cooldown))) {
                log.info("沙盒角色「{}」刚行动过，跳过立即回应", target.getName());
                continue;
            }
            planned.add(target.getId());
            reactionCount.incrementAndGet();
            log.info("沙盒角色被互动触发回应回合：{} → {}", act.getCharacterId(), target.getName());
            runWithChain(target.getId(), false, true, act, depth + 1, planned, reactionCount);
        }
    }

    /**
     * 单次行动（不含回应链）。
     *
     * @param manual   是否管理员手动触发（不占每日额度）
     * @param reaction 是否是「被其他角色互动触发」的回应回合
     * @param trigger  触发这次回应的那条行动记录，会作为上下文写进提示词
     */
    private SandboxAct runOnce(Long characterId, boolean manual, boolean reaction, SandboxAct trigger) {
        SandboxCharacter character = characterMapper.selectById(characterId);
        if (character == null) {
            throw new BusinessException("角色不存在");
        }
        SandboxWorld world = world();
        List<SandboxLocation> locations = locations();
        List<SandboxAct> recent = recentActs(characterId, PROMPT_ACT_LIMIT);
        List<SandboxInteraction> whispers = recentWhispers(characterId, character.getLastRunTime());
        // 同世界的其它角色与它们最近的动静，让角色有机会相遇、互动
        List<SandboxCharacter> companions = otherCharacters(characterId);
        List<SandboxAct> companionActs = neighborActs(companions);

        String systemPrompt = buildSystemPrompt(character, world, locations, companions);
        String userPrompt = buildUserPrompt(character, recent, whispers, companions, companionActs, reaction,
                trigger == null ? null : characterNameOf(companions, trigger.getCharacterId()), trigger);

        String raw;
        try {
            double temperature = character.getTemperature() == null ? 0.9 : character.getTemperature().doubleValue();
            raw = aiProviderService.chat(character.getProviderId(), character.getModel(),
                    systemPrompt, userPrompt, temperature);
        } catch (Exception e) {
            // 失败时只记录原因，不生成记录，避免接口异常时时间线被刷屏
            String msg = e.getMessage() == null ? "AI 调用失败" : e.getMessage();
            characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                    .eq(SandboxCharacter::getId, characterId)
                    .set(SandboxCharacter::getLastError, truncate(msg, 480)));
            throw e instanceof BusinessException ? (BusinessException) e : new BusinessException(msg);
        }

        // 可选的自查（审查）：让 AI 只做 JSON 校验与数值修正，不改写剧情；默认关闭
        if ("1".equals(configService.getConfigValue("sandbox_verify_enabled", "0"))) {
            raw = verifyOutput(character, raw, locations, companions);
        }

        LocalDateTime now = LocalDateTime.now();
        SandboxAct act = new SandboxAct();
        act.setWorldId(character.getWorldId());
        act.setCharacterId(characterId);
        act.setRawResponse(raw);
        act.setManual(manual ? 1 : 0);
        act.setReaction(reaction ? 1 : 0);
        act.setCreateTime(now);

        Integer x = character.getX();
        Integer y = character.getY();
        String locationName = character.getLocationName();
        String statusJson = character.getStatusJson();
        int coins = character.getCoins() == null ? 0 : character.getCoins();
        int coinChange = 0;

        JSONObject obj = parseJson(raw);
        if (obj == null) {
            // AI 没有按格式返回：原文保存下来，方便管理员在后台看到并调整提示词
            act.setFromAi(0);
            act.setActions(truncate(sensitiveWordFilter.filter(trimToEmpty(raw)), 1000));
            act.setSummary("AI 返回内容不是约定的 JSON，已原样保存");
        } else {
            act.setFromAi(1);
            String aiLocation = obj.getStr("location");
            Integer aiX = Convert.toInt(obj.get("x"), null);
            Integer aiY = Convert.toInt(obj.get("y"), null);
            if (aiX == null || aiY == null) {
                SandboxLocation matched = matchLocation(locations, aiLocation);
                if (matched != null) {
                    aiX = matched.getX();
                    aiY = matched.getY();
                }
            }
            x = clamp(aiX == null ? x : aiX);
            y = clamp(aiY == null ? y : aiY);

            if (aiLocation == null || aiLocation.trim().isEmpty()) {
                SandboxLocation nearest = nearestLocation(locations, x, y);
                locationName = nearest == null ? locationName : nearest.getName();
            } else {
                locationName = truncate(aiLocation.trim(), 90);
            }

            act.setLocationName(locationName);
            act.setX(x);
            act.setY(y);
            act.setActions(truncate(sensitiveWordFilter.filter(joinActions(obj.getJSONArray("actions"))), 1000));
            act.setInnerVoice(truncate(sensitiveWordFilter.filter(trimToEmpty(obj.getStr("inner_voice"))), 1000));
            act.setSummary(truncate(sensitiveWordFilter.filter(trimToEmpty(obj.getStr("summary"))), 280));
            // 这一步和哪些角色互动了（只保留世界里真实存在的角色名）
            act.setCompanions(matchCompanions(obj.getJSONArray("companions"), companions));
            // 好感度变化：AI 返回 { "角色名": 3 }，服务端累加到对应关系上
            act.setFavorChange(applyFavorChanges(character, obj.get("favor_changes"), companions));

            // 状态合并：AI 没提到的状态项沿用上一次的值，避免凭空丢失
            statusJson = mergeStatus(character.getStatusJson(), obj.get("status"));
            act.setStatusJson(statusJson);

            // 金币变化：赚取为正、消耗为负，余额不允许变成负数
            coinChange = Convert.toInt(obj.get("coins_change"), 0);
            coins = Math.max(0, coins + coinChange);
        }

        if (act.getLocationName() == null) {
            act.setLocationName(locationName);
        }
        if (act.getX() == null) {
            act.setX(x);
            act.setY(y);
        }
        act.setCoinChange(coinChange);

        LocalDateTime next = nextRunTime(character, now);
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, characterId)
                .set(SandboxCharacter::getX, x)
                .set(SandboxCharacter::getY, y)
                .set(SandboxCharacter::getLocationName, locationName)
                .set(SandboxCharacter::getStatusJson, statusJson)
                .set(SandboxCharacter::getCoins, coins)
                .set(SandboxCharacter::getLastRunTime, now)
                .set(SandboxCharacter::getNextRunTime, next)
                .set(SandboxCharacter::getLastError, null));

        actMapper.insert(act);
        // 有金币变化时记一笔流水，前台和后台都能看到角色是怎么赚钱花钱的
        if (coinChange != 0) {
            addCoinLog(characterId, null, null, coinChange > 0 ? "earn" : "spend", coinChange, 0, coins,
                    blankToDefault(act.getSummary(), coinChange > 0 ? "日常赚取" : "日常花销"));
        }
        return act;
    }

    @Override
    public void runScheduled() {
        if (!"1".equals(configService.getConfigValue("sandbox_enabled", "0"))) {
            return;
        }
        List<SandboxCharacter> characters = characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getEnabled, 1));
        if (characters.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int window = Math.max(0, intConfig("sandbox_batch_window_minutes", 5));
        // 已到期或即将到期的角色合并成同一轮，让多个角色同时行动、有机会互相遇见
        LocalDateTime deadline = now.plusMinutes(window);
        List<SandboxCharacter> batch = new ArrayList<>();
        for (SandboxCharacter character : characters) {
            try {
                // 夜间静默：把执行时间推到静默结束
                if (inNight(now.toLocalTime())) {
                    updateNextRunTime(character.getId(), shiftOutOfNight(now));
                    continue;
                }
                // 达到每日上限：推到第二天
                if (dailyLimitReached(character.getId())) {
                    updateNextRunTime(character.getId(), nextMorning());
                    continue;
                }
                LocalDateTime next = character.getNextRunTime();
                if (next == null || !next.isAfter(deadline)) {
                    batch.add(character);
                }
            } catch (Exception e) {
                log.warn("沙盒角色「{}」检查行动时间失败：{}", character.getName(), e.getMessage());
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        Set<Long> planned = new HashSet<>();
        for (SandboxCharacter character : batch) {
            planned.add(character.getId());
        }
        // 依次执行同一轮：后面的角色能看到本轮前面角色刚刚发生的事，从而产生互动；
        // 如果产生了互动且对方本轮没排上，会立刻给对方一个「回应回合」（受深度/次数/冷却限制）
        AtomicInteger reactionCount = new AtomicInteger();
        for (SandboxCharacter character : batch) {
            runWithChain(character.getId(), false, false, null, 0, planned, reactionCount);
        }
    }

    @Override
    public SandboxRunAllVO runAll() {
        SandboxRunAllVO vo = new SandboxRunAllVO();
        List<SandboxCharacter> characters = characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getEnabled, 1)
                .orderByAsc(SandboxCharacter::getId));
        vo.setTotal(characters.size());
        for (SandboxCharacter character : characters) {
            try {
                // 全员一起行动，本身就已经互相可见，不再额外触发回应回合
                SandboxAct act = runOnce(character.getId(), true, false, null);
                vo.setSuccess(vo.getSuccess() + 1);
                vo.getItems().add(character.getName() + "：" + blankToDefault(act.getSummary(), "已行动"));
            } catch (Exception e) {
                vo.setFailed(vo.getFailed() + 1);
                vo.getItems().add(character.getName() + "：失败（" + e.getMessage() + "）");
            }
        }
        return vo;
    }

    // ============================== 旅人低语 ==============================

    @Override
    public PageResult<SandboxInteraction> interactions(Long characterId, long page, long size) {
        LambdaQueryWrapper<SandboxInteraction> wrapper = new LambdaQueryWrapper<SandboxInteraction>()
                .eq(characterId != null, SandboxInteraction::getCharacterId, characterId)
                .orderByDesc(SandboxInteraction::getCreateTime)
                .orderByDesc(SandboxInteraction::getId);
        IPage<SandboxInteraction> result = interactionMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public void deleteInteraction(Long id) {
        interactionMapper.deleteById(id);
    }

    @Override
    public SandboxInteraction whisper(Long characterId, String content) {
        Long userId = currentUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再留下低语");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException("低语内容不能为空");
        }
        if (text.length() > 200) {
            throw new BusinessException("低语最多 200 字");
        }
        SandboxCharacter character = characterMapper.selectById(characterId);
        if (character == null) {
            throw new BusinessException("角色不存在");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }
        text = sensitiveWordFilter.filter(text);

        // 管理员留言不消耗积分，与智库资源保持一致
        int cost = intConfig("sandbox_whisper_points", 1);
        if (isAdmin(user) || cost <= 0) {
            cost = 0;
        } else {
            pointService.deductPoints(userId, cost, "sandbox", "给「" + character.getName() + "」留下低语");
        }

        SandboxInteraction interaction = new SandboxInteraction();
        interaction.setCharacterId(characterId);
        interaction.setUserId(userId);
        interaction.setUserName(user.getNickname() == null ? user.getUsername() : user.getNickname());
        interaction.setUserAvatar(user.getAvatar());
        interaction.setContent(text);
        interaction.setPointsCost(cost);
        interaction.setCreateTime(LocalDateTime.now());
        interactionMapper.insert(interaction);
        return interaction;
    }

    // ============================== 前台聚合 ==============================

    // ============================== 金币 ==============================

    @Override
    public PageResult<SandboxCoinLog> coinLogs(Long characterId, long page, long size) {
        LambdaQueryWrapper<SandboxCoinLog> wrapper = new LambdaQueryWrapper<SandboxCoinLog>()
                .eq(characterId != null, SandboxCoinLog::getCharacterId, characterId)
                .orderByDesc(SandboxCoinLog::getCreateTime)
                .orderByDesc(SandboxCoinLog::getId);
        IPage<SandboxCoinLog> result = coinLogMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public void deleteCoinLog(Long id) {
        coinLogMapper.deleteById(id);
    }

    @Override
    public SandboxCoinResultVO contributeCoins(Long characterId, int points) {
        Long userId = currentUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再贡献金币");
        }
        SandboxCharacter character = characterMapper.selectById(characterId);
        if (character == null) {
            throw new BusinessException("角色不存在");
        }
        if (points < 1) {
            throw new BusinessException("贡献积分至少要 1");
        }
        int max = intConfig("sandbox_coin_max_points", 100);
        if (max > 0 && points > max) {
            throw new BusinessException("单次最多贡献 " + max + " 积分");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }
        int rate = Math.max(1, intConfig("sandbox_coin_rate", 10));
        int gained = points * rate;

        // 管理员不扣积分，方便后台直接调试
        boolean admin = isAdmin(user);
        if (!admin) {
            pointService.deductPoints(userId, points, "sandbox_coin",
                    "给「" + character.getName() + "」贡献 " + gained + " 金币");
        }

        int coins = (character.getCoins() == null ? 0 : character.getCoins()) + gained;
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, characterId)
                .set(SandboxCharacter::getCoins, coins));
        addCoinLog(characterId, userId, user.getNickname() == null ? user.getUsername() : user.getNickname(),
                "contribute", gained, admin ? 0 : points, coins,
                admin ? "管理员贡献金币" : "旅人用积分贡献金币");

        SysUser latest = userMapper.selectById(userId);
        SandboxCoinResultVO vo = new SandboxCoinResultVO();
        vo.setCoins(coins);
        vo.setGained(gained);
        vo.setPointsCost(admin ? 0 : points);
        vo.setPoints(latest == null || latest.getPoints() == null ? 0 : latest.getPoints());
        return vo;
    }

    /** 记录一条金币流水 */
    private void addCoinLog(Long characterId, Long userId, String userName, String type,
                            int coins, int pointsCost, int balance, String remark) {
        SandboxCoinLog log = new SandboxCoinLog();
        log.setCharacterId(characterId);
        log.setUserId(userId);
        log.setUserName(userName);
        log.setType(type);
        log.setCoins(coins);
        log.setPointsCost(pointsCost);
        log.setBalance(balance);
        log.setRemark(truncate(remark, 190));
        log.setCreateTime(LocalDateTime.now());
        coinLogMapper.insert(log);
    }

    @Override
    public SandboxPortalVO portal() {
        SandboxPortalVO vo = new SandboxPortalVO();
        vo.setEnabled("1".equals(configService.getConfigValue("sandbox_enabled", "0")));
        vo.setWhisperPoints(intConfig("sandbox_whisper_points", 1));
        vo.setCoinRate(Math.max(1, intConfig("sandbox_coin_rate", 10)));
        vo.setWorld(world());
        vo.setLocations(locations());
        List<SandboxCharacterVO> list = new ArrayList<>();
        for (SandboxCharacter character : characters()) {
            list.add(toVO(character));
        }
        vo.setCharacters(list);
        return vo;
    }

    private SandboxCharacterVO toVO(SandboxCharacter character) {
        SandboxCharacterVO vo = new SandboxCharacterVO();
        vo.setId(character.getId());
        vo.setName(character.getName());
        vo.setTitle(character.getTitle());
        vo.setAvatar(character.getAvatar());
        vo.setAppearance(character.getAppearance());
        vo.setX(character.getX());
        vo.setY(character.getY());
        vo.setLocationName(character.getLocationName());
        vo.setEnabled(character.getEnabled());
        vo.setStatus(parseStatus(character.getStatusJson()));
        vo.setCoins(character.getCoins() == null ? 0 : character.getCoins());
        vo.setNextRunTime(character.getNextRunTime());
        vo.setLastRunTime(character.getLastRunTime());
        vo.setRelations(relationsOf(character.getId()));
        List<SandboxCoinLog> coinLogs = coinLogMapper.selectList(new LambdaQueryWrapper<SandboxCoinLog>()
                .eq(SandboxCoinLog::getCharacterId, character.getId())
                .orderByDesc(SandboxCoinLog::getCreateTime)
                .orderByDesc(SandboxCoinLog::getId)
                .last("limit " + PORTAL_COIN_LOG_LIMIT));
        Collections.reverse(coinLogs);
        vo.setRecentCoins(coinLogs);
        List<SandboxAct> acts = recentActs(character.getId(), PORTAL_ACT_LIMIT);
        // 前台按时间正序展示，最近一条在最下面
        Collections.reverse(acts);
        vo.setRecentActs(acts);
        return vo;
    }

    private Map<String, Object> parseStatus(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            for (String key : obj.keySet()) {
                map.put(key, obj.get(key));
            }
        } catch (Exception ignored) {
            // 状态解析失败不影响其它展示
        }
        return map;
    }

    /** 合并状态：AI 返回的项覆盖旧值，AI 没提到的项沿用旧值 */
    private String mergeStatus(String oldJson, Object aiStatus) {
        Map<String, Object> merged = parseStatus(oldJson);
        if (aiStatus instanceof JSONObject) {
            JSONObject obj = (JSONObject) aiStatus;
            for (String key : obj.keySet()) {
                merged.put(key, normalizeStatusValue(key, obj.get(key)));
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        return truncate(JSONUtil.toJsonStr(merged), 1000);
    }

    /** 标准状态项统一成 0~100 的整数，避免 AI 给出离谱数值 */
    private Object normalizeStatusValue(String key, Object value) {
        if (value instanceof Number && STANDARD_STATUS_KEYS.contains(key)) {
            int num = ((Number) value).intValue();
            return Math.max(0, Math.min(100, num));
        }
        return value;
    }

    /** 给 AI 的完整时间描述：日期 + 星期 + 时刻 + 时段（北京时间） */
    private String timeText(LocalDateTime time) {
        String[] weeks = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        String week = weeks[time.getDayOfWeek().getValue() - 1];
        return time.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm"))
                + "（" + week + "，" + periodOfDay(time.toLocalTime()) + "，北京时间 UTC+8）";
    }

    /** 把一天切成几个时段，让 AI 更容易写出符合时间的行为 */
    private String periodOfDay(LocalTime time) {
        int hour = time.getHour();
        if (hour < 5) {
            return "深夜";
        }
        if (hour < 8) {
            return "清晨";
        }
        if (hour < 11) {
            return "上午";
        }
        if (hour < 13) {
            return "中午";
        }
        if (hour < 17) {
            return "下午";
        }
        if (hour < 19) {
            return "傍晚";
        }
        if (hour < 22) {
            return "晚上";
        }
        return "深夜";
    }

    // ============================== 提示词组装 ==============================

    /** 系统提示词：角色卡 + 世界书 + 地图地点 + 其他居民 + 输出格式要求 */
    private String buildSystemPrompt(SandboxCharacter c, SandboxWorld w, List<SandboxLocation> locations,
                                     List<SandboxCharacter> companions) {
        StringBuilder sb = new StringBuilder();
        sb.append("你正在扮演「").append(blankToDefault(w.getName(), "无名世界")).append("」世界里的一个角色，")
                .append("用第一人称继续他在这个世界的生活。请严格遵守角色设定，不要跳出角色，也不要提及自己是 AI。\n\n");

        sb.append("【角色卡】\n");
        sb.append("姓名：").append(c.getName()).append("\n");
        if (notBlank(c.getTitle())) {
            sb.append("称号：").append(c.getTitle()).append("\n");
        }
        if (notBlank(c.getAppearance())) {
            sb.append("外貌：").append(c.getAppearance()).append("\n");
        }
        sb.append("人设：\n").append(blankToDefault(c.getPersona(), DEFAULT_PERSONA)).append("\n\n");

        sb.append("【世界设定】\n").append(blankToDefault(w.getWorldPrompt(), DEFAULT_WORLD_PROMPT)).append("\n\n");

        sb.append("【地图地点】x 是左右方向、y 是上下方向，取值 0~100（地图百分比）：\n");
        if (locations.isEmpty()) {
            sb.append("- （管理员还没有在地图上添加地点，可以自行合理地设定一个符合世界观的去处）\n");
        } else {
            for (SandboxLocation location : locations) {
                sb.append("- ").append(location.getName())
                        .append("（x=").append(location.getX()).append(", y=").append(location.getY()).append("）");
                if (notBlank(location.getDescription())) {
                    sb.append("：").append(location.getDescription());
                }
                sb.append("\n");
            }
        }

        sb.append("\n【世界里的其他居民】你们生活在同一个世界里，可能在同一地点相遇、交谈、同行或互相影响：\n");
        if (companions.isEmpty()) {
            sb.append("- （目前世上只有你一个角色，可以自由探索）\n");
        } else {
            for (SandboxCharacter other : companions) {
                sb.append("- ").append(other.getName());
                if (notBlank(other.getTitle())) {
                    sb.append("（").append(other.getTitle()).append("）");
                }
                sb.append("：当前在").append(blankToDefault(other.getLocationName(), "某处"))
                        .append("（x=").append(other.getX() == null ? 50 : other.getX())
                        .append(", y=").append(other.getY() == null ? 50 : other.getY()).append("）");
                if (notBlank(other.getAppearance())) {
                    sb.append("；").append(truncate(other.getAppearance(), 60));
                }
                sb.append("\n");
            }
        }

        sb.append("\n【输出要求】\n")
                .append("你必须严格只输出一个 JSON 对象，不要输出任何解释、前言、后缀，也不要使用 Markdown 代码块标记。JSON 结构如下：\n")
                .append("{\"location\":\"这一步所处的地点名称，尽量使用【地图地点】里的名字\",\"x\":35,\"y\":62,")
                .append("\"actions\":[\"具体动作一\",\"具体动作二\"],\"inner_voice\":\"角色此刻的心里话（第一人称，一句话）\",")
                .append("\"status\":{\"体力\":80,\"魔力\":45,\"饥饿度\":30,\"心情\":\"平静\"},")
                .append("\"coins_change\":0,\"companions\":[],\"favor_changes\":{\"角色名\":3},")
                .append("\"summary\":\"30 字以内概括这一步\"}\n")
                .append("要求：\n")
                .append("1. actions 写 1~3 条具体、有画面感的动作。\n")
                .append("2. status 必须包含体力、魔力、饥饿度（0~100 的整数）与心情（简短词语），可以再补充其它状态项；")
                .append("体力与魔力会随活动增减，饥饿度随时间上升、吃东西后下降。\n")
                .append("3. coins_change 是这一步的金币变化（整数）：赚钱填正数（例如接委托 +20、卖草药 +6），")
                .append("花钱填负数（例如住店 -5、买面包 -2），没有变化填 0；身上金币不够时不要消费超过余额。\n")
                .append("4. 行动必须符合当前时间与时段：深夜多是休息或守夜，清晨适合起床准备，用餐时间可以吃饭，")
                .append("白天适合赶路、做工或交易；不要让角色在深夜做白天才合理的事。\n")
                .append("5. companions 是角色名数组：如果这一步你与【世界里的其他居民】在同一地点相遇、交谈、")
                .append("同行或互相影响，就把他们的名字填进去（名字必须与上面列出的完全一致），没有就填 []。\n")
                .append("6. favor_changes 表示这一步你对某个角色的好感度变化，格式是 {\"角色名\": 变化量}：")
                .append("只有当这一步真的和对方发生了互动（并且已把对方写进 companions）时才填，否则填 {}；")
                .append("名字必须与【世界里的其他居民】里列出的完全一致，不要用称号、不要编造；")
                .append("变化幅度只能是 -10~+10 的整数，日常小事 ±1~3，重要事件才用 ±5~10；")
                .append("还要参考【你与其他角色的关系】里的当前好感度：接近 100 时不要再给正数，接近 -100 时不要再给负数；")
                .append("并且这一步做了什么必须写在 actions 里，不允许出现「好感变了但行动里看不出来」的情况。\n")
                .append("7. 整体风格温和、日常、有生活感，避免暴力与不适内容。");
        return sb.toString();
    }

    /** 用户提示词：当前状态 + 最近记忆 + 其他居民的动静 + 旅人的话 + 本次指令 */
    private String buildUserPrompt(SandboxCharacter c, List<SandboxAct> recent, List<SandboxInteraction> whispers,
                                   List<SandboxCharacter> companions, List<SandboxAct> companionActs,
                                   boolean reaction, String triggerName, SandboxAct trigger) {
        StringBuilder sb = new StringBuilder();
        LocalDateTime now = LocalDateTime.now();
        sb.append("【当前状态】\n")
                .append("现在时间：").append(timeText(now)).append("\n")
                .append("当前位置：").append(blankToDefault(c.getLocationName(), "尚未确定"))
                .append("（x=").append(c.getX() == null ? 50 : c.getX())
                .append(", y=").append(c.getY() == null ? 50 : c.getY()).append("）\n")
                .append("身上金币：").append(c.getCoins() == null ? 0 : c.getCoins()).append(" 枚\n");
        Map<String, Object> status = parseStatus(c.getStatusJson());
        if (!status.isEmpty()) {
            sb.append("当前状态：").append(JSONUtil.toJsonStr(status)).append("\n");
        }
        // 回应回合：把「刚刚发生了什么」明确写出来，避免被搭话的一方毫不知情
        if (reaction && trigger != null) {
            sb.append("\n【刚刚发生的事】").append(blankToDefault(triggerName, "另一位居民"))
                    .append(" 于 ").append(trigger.getCreateTime() == null ? ""
                            : trigger.getCreateTime().format(DATE_TIME_FORMATTER))
                    .append(" 在 ").append(blankToDefault(trigger.getLocationName(), "某处")).append("：\n")
                    .append(blankToDefault(trigger.getActions(), blankToDefault(trigger.getSummary(), "")))
                    .append("\n请自然地回应这件事：可以直接搭话、可以并肩行动、也可以只是心里想一想；")
                    .append("不必强行改变你原本的打算，也不要重复上面已经写过的动作。\n");
        }
        sb.append("\n【最近行动】按时间从早到晚排列，最后一条是刚刚发生的：\n");
        if (recent.isEmpty()) {
            sb.append("（这是角色在这个世界的第一步，可以自由展开）\n");
        } else {
            List<SandboxAct> ordered = new ArrayList<>(recent);
            Collections.reverse(ordered);
            for (SandboxAct act : ordered) {
                sb.append("- ").append(act.getCreateTime() == null ? "" : act.getCreateTime().format(DATE_TIME_FORMATTER))
                        .append(" 在").append(blankToDefault(act.getLocationName(), "某处")).append("：")
                        .append(blankToDefault(act.getSummary(), blankToDefault(act.getActions(), "")))
                        .append("\n");
            }
        }
        if (!companionActs.isEmpty()) {
            sb.append("\n【其他居民最近的动静】\n");
            List<SandboxAct> ordered = new ArrayList<>(companionActs);
            ordered.sort((a, b) -> {
                LocalDateTime ta = a.getCreateTime() == null ? LocalDateTime.MIN : a.getCreateTime();
                LocalDateTime tb = b.getCreateTime() == null ? LocalDateTime.MIN : b.getCreateTime();
                return ta.compareTo(tb);
            });
            for (SandboxAct act : ordered) {
                sb.append("- ").append(characterNameOf(companions, act.getCharacterId()))
                        .append(" 于 ").append(act.getCreateTime() == null ? ""
                                : act.getCreateTime().format(DATE_TIME_FORMATTER))
                        .append(" 在").append(blankToDefault(act.getLocationName(), "某处")).append("：")
                        .append(blankToDefault(act.getSummary(), blankToDefault(act.getActions(), "")))
                        .append("\n");
            }
            sb.append("（如果你们恰好都在同一地点或附近，可以自然地遇见、打招呼、结伴或交换物品，")
                    .append("并把对方名字填进 companions；不必强行互动）\n");
        }
        List<String> relationLines = relationLines(c.getId(), companions);
        if (!relationLines.isEmpty()) {
            sb.append("\n【你与其他角色的关系】好感度 -100~100（0 是陌生，越高越亲近，负数表示反感）：\n");
            for (String line : relationLines) {
                sb.append("- ").append(line).append("\n");
            }
        }
        if (!whispers.isEmpty()) {
            sb.append("\n【旅人的低语】最近有人对角色说：\n");
            List<SandboxInteraction> ordered = new ArrayList<>(whispers);
            Collections.reverse(ordered);
            for (SandboxInteraction whisper : ordered) {
                sb.append("- ").append(blankToDefault(whisper.getUserName(), "一位旅人"))
                        .append("：").append(whisper.getContent()).append("\n");
            }
            sb.append("（角色可以自然地在心里或行动上回应，也可以选择忽略，不要生硬地复述原话）\n");
        }
        sb.append("\n【本次要求】\n请推进 1 步剧情：角色可以移动到一个新的地点、留在原地做一件事，或与这个世界里的事物互动；")
                .append("不要重复最近已经做过的内容，要有新的细节。最后按约定的 JSON 结构输出。");
        return sb.toString();
    }

    // ============================== 内部工具方法 ==============================

    private List<SandboxAct> recentActs(Long characterId, int limit) {
        return actMapper.selectList(new LambdaQueryWrapper<SandboxAct>()
                .eq(SandboxAct::getCharacterId, characterId)
                .orderByDesc(SandboxAct::getCreateTime)
                .orderByDesc(SandboxAct::getId)
                .last("limit " + limit));
    }

    /** 同一世界里其它已启用的角色 */
    private List<SandboxCharacter> otherCharacters(Long characterId) {
        return characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>()
                .ne(SandboxCharacter::getId, characterId)
                .eq(SandboxCharacter::getEnabled, 1)
                .orderByAsc(SandboxCharacter::getId));
    }

    /** 其它角色最近的一条行动，用于提示词里的「其他居民最近的动静」 */
    private List<SandboxAct> neighborActs(List<SandboxCharacter> companions) {
        List<SandboxAct> list = new ArrayList<>();
        for (SandboxCharacter other : companions) {
            list.addAll(recentActs(other.getId(), 1));
        }
        return list;
    }

    private String characterNameOf(List<SandboxCharacter> companions, Long characterId) {
        for (SandboxCharacter other : companions) {
            if (other.getId().equals(characterId)) {
                return other.getName();
            }
        }
        return "某位居民";
    }

    /** 把 AI 返回的 companions 收敛为世界里真实存在的角色名，避免出现编造的名字 */
    private String matchCompanions(JSONArray array, List<SandboxCharacter> companions) {
        if (array == null || array.isEmpty() || companions.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String name = array.getStr(i);
            if (name == null) {
                continue;
            }
            String target = name.trim();
            for (SandboxCharacter other : companions) {
                if (other.getName() != null && other.getName().equals(target) && !names.contains(other.getName())) {
                    names.add(other.getName());
                }
            }
        }
        if (names.isEmpty()) {
            return null;
        }
        return truncate(String.join("、", names), 190);
    }

    // ============================== 好感度 ==============================

    /** 好感度区间对应的文字等级 */
    private String favorLevel(Integer favor) {
        int value = favor == null ? 0 : favor;
        if (value <= -60) {
            return "敌视";
        }
        if (value <= -20) {
            return "反感";
        }
        if (value < 20) {
            return "陌生";
        }
        if (value < 40) {
            return "相识";
        }
        if (value < 60) {
            return "友好";
        }
        if (value < 80) {
            return "亲近";
        }
        return "挚友";
    }

    private int clampFavor(int value) {
        return Math.max(FAVOR_MIN, Math.min(FAVOR_MAX, value));
    }

    private SandboxCharacter findByName(List<SandboxCharacter> characters, String name) {
        for (SandboxCharacter character : characters) {
            if (character.getName() != null && character.getName().equals(name)) {
                return character;
            }
        }
        return null;
    }

    private SandboxRelation findRelation(List<SandboxRelation> rows, Long holderId, Long targetId) {
        for (SandboxRelation row : rows) {
            if ((holderId == null || holderId.equals(row.getCharacterId()))
                    && targetId.equals(row.getTargetId())) {
                return row;
            }
        }
        return null;
    }

    @Override
    public List<SandboxRelationVO> relationsOf(Long characterId) {
        List<SandboxRelationVO> list = new ArrayList<>();
        SandboxCharacter me = characterMapper.selectById(characterId);
        if (me == null) {
            return list;
        }
        List<SandboxCharacter> others = characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>()
                .ne(SandboxCharacter::getId, characterId)
                .orderByAsc(SandboxCharacter::getId));
        List<SandboxRelation> mine = relationMapper.selectList(new LambdaQueryWrapper<SandboxRelation>()
                .eq(SandboxRelation::getCharacterId, characterId));
        List<SandboxRelation> theirs = relationMapper.selectList(new LambdaQueryWrapper<SandboxRelation>()
                .eq(SandboxRelation::getTargetId, characterId));
        for (SandboxCharacter other : others) {
            SandboxRelation relation = findRelation(mine, null, other.getId());
            SandboxRelation reverse = findRelation(theirs, other.getId(), characterId);
            int favor = relation == null || relation.getFavor() == null ? 0 : relation.getFavor();
            int reverseFavor = reverse == null || reverse.getFavor() == null ? 0 : reverse.getFavor();
            SandboxRelationVO vo = new SandboxRelationVO();
            vo.setId(relation == null ? null : relation.getId());
            vo.setCharacterId(characterId);
            vo.setCharacterName(me.getName());
            vo.setTargetId(other.getId());
            vo.setTargetName(other.getName());
            vo.setTargetTitle(other.getTitle());
            vo.setTargetAvatar(other.getAvatar());
            vo.setTargetLocation(other.getLocationName());
            vo.setFavor(favor);
            vo.setFavorLevel(favorLevel(favor));
            vo.setLastChange(relation == null ? null : relation.getLastChange());
            vo.setLastChangeTime(relation == null ? null : relation.getLastChangeTime());
            vo.setReverseFavor(reverseFavor);
            vo.setReverseFavorLevel(favorLevel(reverseFavor));
            vo.setRemark(relation == null ? null : relation.getRemark());
            list.add(vo);
        }
        return list;
    }

    /** 提示词里的关系摘要：只列出有记录的（好感不为 0 或有备注） */
    private List<String> relationLines(Long characterId, List<SandboxCharacter> companions) {
        List<String> lines = new ArrayList<>();
        if (companions.isEmpty()) {
            return lines;
        }
        List<SandboxRelation> rows = relationMapper.selectList(new LambdaQueryWrapper<SandboxRelation>()
                .eq(SandboxRelation::getCharacterId, characterId));
        for (SandboxCharacter other : companions) {
            SandboxRelation row = findRelation(rows, null, other.getId());
            if (row == null) {
                continue;
            }
            int favor = row.getFavor() == null ? 0 : row.getFavor();
            if (favor == 0 && !notBlank(row.getRemark())) {
                continue;
            }
            StringBuilder line = new StringBuilder(other.getName());
            line.append("：好感 ").append(favor).append("（").append(favorLevel(favor)).append("）");
            if (notBlank(row.getRemark())) {
                line.append("；印象：").append(truncate(row.getRemark(), 40));
            }
            lines.add(line.toString());
        }
        return lines;
    }

    @Override
    public List<SandboxRelationVO> relationList(Long characterId) {
        List<SandboxRelation> rows = relationMapper.selectList(new LambdaQueryWrapper<SandboxRelation>()
                .eq(characterId != null, SandboxRelation::getCharacterId, characterId)
                .orderByAsc(SandboxRelation::getCharacterId)
                .orderByAsc(SandboxRelation::getTargetId));
        Map<Long, String> names = new LinkedHashMap<>();
        for (SandboxCharacter character : characterMapper.selectList(null)) {
            names.put(character.getId(), character.getName());
        }
        List<SandboxRelationVO> list = new ArrayList<>();
        for (SandboxRelation row : rows) {
            int favor = row.getFavor() == null ? 0 : row.getFavor();
            SandboxRelationVO vo = new SandboxRelationVO();
            vo.setId(row.getId());
            vo.setCharacterId(row.getCharacterId());
            vo.setCharacterName(names.get(row.getCharacterId()));
            vo.setTargetId(row.getTargetId());
            vo.setTargetName(names.get(row.getTargetId()));
            vo.setFavor(favor);
            vo.setFavorLevel(favorLevel(favor));
            vo.setLastChange(row.getLastChange());
            vo.setLastChangeTime(row.getLastChangeTime());
            SandboxRelation reverse = relationMapper.selectOne(new LambdaQueryWrapper<SandboxRelation>()
                    .eq(SandboxRelation::getCharacterId, row.getTargetId())
                    .eq(SandboxRelation::getTargetId, row.getCharacterId())
                    .last("limit 1"));
            int reverseFavor = reverse == null || reverse.getFavor() == null ? 0 : reverse.getFavor();
            vo.setReverseFavor(reverseFavor);
            vo.setReverseFavorLevel(favorLevel(reverseFavor));
            vo.setRemark(row.getRemark());
            vo.setUpdateTime(row.getUpdateTime());
            list.add(vo);
        }
        return list;
    }

    @Override
    public void saveRelation(SandboxRelation relation) {
        if (relation.getCharacterId() == null || relation.getTargetId() == null) {
            throw new BusinessException("请选择角色与对象");
        }
        if (relation.getCharacterId().equals(relation.getTargetId())) {
            throw new BusinessException("角色不能和自己建立好感度");
        }
        if (characterMapper.selectById(relation.getCharacterId()) == null
                || characterMapper.selectById(relation.getTargetId()) == null) {
            throw new BusinessException("角色不存在");
        }
        int favor = clampFavor(relation.getFavor() == null ? 0 : relation.getFavor());
        SandboxRelation exists = relationMapper.selectOne(new LambdaQueryWrapper<SandboxRelation>()
                .eq(SandboxRelation::getCharacterId, relation.getCharacterId())
                .eq(SandboxRelation::getTargetId, relation.getTargetId())
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (exists == null) {
            relation.setId(null);
            relation.setFavor(favor);
            if (relation.getWorldId() == null) {
                relation.setWorldId(worldId());
            }
            relation.setCreateTime(now);
            relation.setUpdateTime(now);
            relationMapper.insert(relation);
        } else {
            relationMapper.update(null, new LambdaUpdateWrapper<SandboxRelation>()
                    .eq(SandboxRelation::getId, exists.getId())
                    .set(SandboxRelation::getFavor, favor)
                    .set(SandboxRelation::getRemark, relation.getRemark())
                    .set(SandboxRelation::getUpdateTime, now));
        }
    }

    @Override
    public void deleteRelation(Long id) {
        relationMapper.deleteById(id);
    }

    /** 应用 AI 返回的好感度变化，返回给前台展示的文字，例如「零 +3、小埋 -2」 */
    private String applyFavorChanges(SandboxCharacter character, Object favorObj, List<SandboxCharacter> companions) {
        if (!(favorObj instanceof JSONObject) || companions.isEmpty()) {
            return null;
        }
        JSONObject obj = (JSONObject) favorObj;
        List<String> changes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (String key : obj.keySet()) {
            String name = key == null ? "" : key.trim();
            SandboxCharacter other = findByName(companions, name);
            if (other == null) {
                continue;
            }
            int delta = Convert.toInt(obj.get(key), 0);
            if (delta == 0) {
                continue;
            }
            // 单次变化限制在 ±20，避免 AI 一次给太夸张的数值
            delta = Math.max(-FAVOR_STEP_MAX, Math.min(FAVOR_STEP_MAX, delta));
            SandboxRelation relation = relationMapper.selectOne(new LambdaQueryWrapper<SandboxRelation>()
                    .eq(SandboxRelation::getCharacterId, character.getId())
                    .eq(SandboxRelation::getTargetId, other.getId())
                    .last("limit 1"));
            int base = relation == null || relation.getFavor() == null ? 0 : relation.getFavor();
            int favor = clampFavor(base + delta);
            // 真实生效的变化：到达上下限后会被截断，行动记录只记实际生效的值
            int effective = favor - base;
            if (relation == null) {
                relation = new SandboxRelation();
                relation.setWorldId(character.getWorldId());
                relation.setCharacterId(character.getId());
                relation.setTargetId(other.getId());
                relation.setFavor(favor);
                relation.setLastChange(effective);
                relation.setLastChangeTime(now);
                relation.setCreateTime(now);
                relation.setUpdateTime(now);
                relationMapper.insert(relation);
            } else {
                relationMapper.update(null, new LambdaUpdateWrapper<SandboxRelation>()
                        .eq(SandboxRelation::getId, relation.getId())
                        .set(SandboxRelation::getFavor, favor)
                        .set(SandboxRelation::getLastChange, effective)
                        .set(SandboxRelation::getLastChangeTime, now)
                        .set(SandboxRelation::getUpdateTime, now));
            }
            if (effective == 0) {
                // 已经到达上限/下限，数值没有真正变化，就不写进记录，避免前后台对不上
                continue;
            }
            changes.add(other.getName() + " " + (effective > 0 ? "+" : "") + effective + "（→" + favor + "）");
        }
        return changes.isEmpty() ? null : truncate(String.join("、", changes), 190);
    }

    /**
     * 可选的自查（审查）：把 AI 的输出再交给一次 AI 只做 JSON 校验与数值修正，不改写剧情。
     * 需要在后台把 sandbox_verify_enabled 设为 1 才生效（会翻倍消耗 token）。
     * 任何失败都会退回原始输出，不影响正常流程。
     */
    private String verifyOutput(SandboxCharacter character, String raw, List<SandboxLocation> locations,
                                List<SandboxCharacter> companions) {
        if (raw == null || raw.trim().isEmpty()) {
            return raw;
        }
        try {
            StringBuilder sys = new StringBuilder();
            sys.append("你是一个 JSON 校验器，只做校验与修正，不要改写故事内容，也不要新增剧情。\n")
                    .append("输入是某个角色扮演输出的 JSON，请检查后只输出修正过的 JSON：\n")
                    .append("1. 必须是合法 JSON，字段包含 location、x、y、actions、inner_voice、status、")
                    .append("coins_change、companions、favor_changes、summary；\n")
                    .append("2. location 必须是【可用地点】里的名字，x/y 为 0~100 的整数；\n")
                    .append("3. status 里体力、魔力、饥饿度必须是 0~100 的整数；\n")
                    .append("4. coins_change 必须与 actions 描述的收支一致，没有花钱或赚钱就改成 0；\n")
                    .append("5. companions 只能填【可用角色名】里的名字；favor_changes 的键也只能是这些名字，")
                    .append("并且必须与 companions 和 actions 的描述相符，幅度限制在 -10~+10，")
                    .append("还要保证加上当前好感度后仍在 -100~100 之内；\n")
                    .append("6. 不要编造任何角色名或地点名。修正完成后只输出 JSON，不要输出解释。\n");

            StringBuilder user = new StringBuilder();
            user.append("【可用地点】");
            if (locations.isEmpty()) {
                user.append("（无）");
            }
            for (SandboxLocation location : locations) {
                user.append(location.getName()).append(" ");
            }
            user.append("\n【可用角色名】");
            if (companions.isEmpty()) {
                user.append("（无）");
            }
            for (SandboxCharacter other : companions) {
                user.append(other.getName()).append(" ");
            }
            user.append("\n【当前好感度】");
            List<String> lines = relationLines(character.getId(), companions);
            user.append(lines.isEmpty() ? "（暂无记录，均为 0）" : String.join("；", lines));
            user.append("\n【角色输出】\n").append(raw);

            String content = aiProviderService.chat(character.getProviderId(), character.getModel(),
                    sys.toString(), user.toString(), 0.2);
            return content == null || content.trim().isEmpty() ? raw : content;
        } catch (Exception e) {
            log.warn("沙盒输出自查失败，改用原始输出：{}", e.getMessage());
            return raw;
        }
    }

    /** 取角色上次行动之后收到的低语；第一次行动时取最近几条 */
    private List<SandboxInteraction> recentWhispers(Long characterId, LocalDateTime after) {
        LambdaQueryWrapper<SandboxInteraction> wrapper = new LambdaQueryWrapper<SandboxInteraction>()
                .eq(SandboxInteraction::getCharacterId, characterId)
                .gt(after != null, SandboxInteraction::getCreateTime, after)
                .orderByDesc(SandboxInteraction::getCreateTime)
                .orderByDesc(SandboxInteraction::getId)
                .last("limit " + PROMPT_WHISPER_LIMIT);
        return interactionMapper.selectList(wrapper);
    }

    /** 剥离 Markdown 代码块并截取 JSON 主体 */
    private JSONObject parseJson(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            if (firstLine >= 0) {
                text = text.substring(firstLine + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return JSONUtil.parseObj(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private String joinActions(JSONArray actions) {
        if (actions == null || actions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < actions.size(); i++) {
            Object item = actions.get(i);
            String text = item == null ? "" : String.valueOf(item).trim();
            if (text.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(text);
        }
        return sb.toString();
    }

    private SandboxLocation matchLocation(List<SandboxLocation> locations, String name) {
        if (name == null) {
            return null;
        }
        String target = name.trim();
        for (SandboxLocation location : locations) {
            if (location.getName() != null && location.getName().equals(target)) {
                return location;
            }
        }
        // 允许「晨雾森林·东侧小径」这类带后缀的写法
        for (SandboxLocation location : locations) {
            if (location.getName() != null && !location.getName().isEmpty() && target.contains(location.getName())) {
                return location;
            }
        }
        return null;
    }

    private SandboxLocation nearestLocation(List<SandboxLocation> locations, Integer x, Integer y) {
        SandboxLocation nearest = null;
        double best = Double.MAX_VALUE;
        for (SandboxLocation location : locations) {
            if (location.getX() == null || location.getY() == null) {
                continue;
            }
            double distance = Math.pow(location.getX() - x, 2) + Math.pow(location.getY() - y, 2);
            if (distance < best) {
                best = distance;
                nearest = location;
            }
        }
        return nearest;
    }

    /** 计算下一次行动时间：在角色配置的间隔区间内随机，并避开夜间静默 */
    private LocalDateTime nextRunTime(SandboxCharacter character, LocalDateTime from) {
        int min = character.getIntervalMin() == null ? intConfig("sandbox_interval_min", 45) : character.getIntervalMin();
        int max = character.getIntervalMax() == null ? intConfig("sandbox_interval_max", 75) : character.getIntervalMax();
        if (min < 1) {
            min = 1;
        }
        if (max < min) {
            max = min;
        }
        int minutes = min + (max > min ? random.nextInt(max - min + 1) : 0);
        return shiftOutOfNight(from.plusMinutes(minutes));
    }

    private void updateNextRunTime(Long characterId, LocalDateTime time) {
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, characterId)
                .set(SandboxCharacter::getNextRunTime, time));
    }

    private boolean dailyLimitReached(Long characterId) {
        int limit = intConfig("sandbox_daily_limit", 12);
        if (limit <= 0) {
            return false;
        }
        Long count = actMapper.selectCount(new LambdaQueryWrapper<SandboxAct>()
                .eq(SandboxAct::getCharacterId, characterId)
                .eq(SandboxAct::getManual, 0)
                .ge(SandboxAct::getCreateTime, LocalDate.now().atStartOfDay()));
        return count != null && count >= limit;
    }

    private boolean inNight(LocalTime time) {
        LocalTime start = timeConfig("sandbox_night_start", LocalTime.of(2, 0));
        LocalTime end = timeConfig("sandbox_night_end", LocalTime.of(7, 0));
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }

    /** 把落在夜间静默里的时间推到静默结束 */
    private LocalDateTime shiftOutOfNight(LocalDateTime time) {
        LocalTime start = timeConfig("sandbox_night_start", LocalTime.of(2, 0));
        LocalTime end = timeConfig("sandbox_night_end", LocalTime.of(7, 0));
        if (start.equals(end)) {
            return time;
        }
        LocalTime current = time.toLocalTime();
        if (start.isBefore(end)) {
            if (!current.isBefore(start) && current.isBefore(end)) {
                return LocalDateTime.of(time.toLocalDate(), end);
            }
            return time;
        }
        if (!current.isBefore(start)) {
            return LocalDateTime.of(time.toLocalDate().plusDays(1), end);
        }
        if (current.isBefore(end)) {
            return LocalDateTime.of(time.toLocalDate(), end);
        }
        return time;
    }

    private LocalDateTime nextMorning() {
        return LocalDateTime.of(LocalDate.now().plusDays(1),
                timeConfig("sandbox_night_end", LocalTime.of(7, 0)));
    }

    private int intConfig(String key, int defaultValue) {
        try {
            return Integer.parseInt(configService.getConfigValue(key, String.valueOf(defaultValue)).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private LocalTime timeConfig(String key, LocalTime defaultValue) {
        try {
            return LocalTime.parse(configService.getConfigValue(key, defaultValue.format(TIME_FORMATTER)).trim(),
                    TIME_FORMATTER);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Long worldId() {
        SandboxWorld w = world();
        return w.getId() == null ? 1L : w.getId();
    }

    private Long currentUserId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAdmin(SysUser user) {
        String role = user.getRole();
        return "SUPER".equals(role) || "ADMIN1".equals(role) || "ADMIN2".equals(role);
    }

    private int clamp(Integer value) {
        int v = value == null ? 50 : value;
        if (v < 0) {
            return 0;
        }
        return Math.min(v, 100);
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String trimToEmpty(String text) {
        return text == null ? "" : text.trim();
    }

    private boolean notBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }

    private String blankToDefault(String text, String defaultValue) {
        return notBlank(text) ? text.trim() : defaultValue;
    }
}
