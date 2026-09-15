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
import com.bc.bcblog.dto.SandboxCharacterGenerateDTO;
import com.bc.bcblog.component.SensitiveWordFilter;
import com.bc.bcblog.component.AuditContext;
import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxCoinLog;
import com.bc.bcblog.entity.SandboxInteraction;
import com.bc.bcblog.entity.SandboxItem;
import com.bc.bcblog.entity.SandboxLocation;
import com.bc.bcblog.entity.SandboxMemory;
import com.bc.bcblog.entity.SandboxNews;
import com.bc.bcblog.entity.SandboxRelation;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SandboxActMapper;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxCoinLogMapper;
import com.bc.bcblog.mapper.SandboxInteractionMapper;
import com.bc.bcblog.mapper.SandboxItemMapper;
import com.bc.bcblog.mapper.SandboxLocationMapper;
import com.bc.bcblog.mapper.SandboxMemoryMapper;
import com.bc.bcblog.mapper.SandboxNewsMapper;
import com.bc.bcblog.mapper.SandboxRelationMapper;
import com.bc.bcblog.mapper.SandboxWorldMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.AiProviderService;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.PointService;
import com.bc.bcblog.service.SandboxService;
import com.bc.bcblog.vo.SandboxCharacterVO;
import com.bc.bcblog.vo.SandboxCharacterDraftVO;
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
    /** 前台角色档案面板展示的最近记忆条数 */
    private static final int PORTAL_MEMORY_LIMIT = 3;
    /** 单次行动物品数量变化上限 */
    private static final int ITEM_STEP_MAX = 9;
    /** 背包最多保留的物品种类 */
    private static final int ITEM_KIND_MAX = 20;
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
    private final SandboxMemoryMapper memoryMapper;
    private final SandboxItemMapper itemMapper;
    private final SandboxNewsMapper newsMapper;
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
        // 区域宽高：0 表示单点，限制在 0~100
        if (location.getWidth() != null) {
            location.setWidth(Math.max(0, Math.min(100, location.getWidth())));
        }
        if (location.getHeight() != null) {
            location.setHeight(Math.max(0, Math.min(100, location.getHeight())));
        }
        if (location.getId() == null) {
            location.setX(location.getX() == null ? 50 : location.getX());
            location.setY(location.getY() == null ? 50 : location.getY());
            // 新地点默认给一块区域，方便直接拖动缩放
            location.setWidth(location.getWidth() == null ? 12 : location.getWidth());
            location.setHeight(location.getHeight() == null ? 7 : location.getHeight());
            if (location.getSortOrder() == null) {
                location.setSortOrder(0);
            }
            if (location.getWorldId() == null) {
                location.setWorldId(worldId());
            }
        }
        // 区域不能超出地图边界
        if (location.getX() != null && location.getWidth() != null && location.getWidth() > 0) {
            location.setX(Math.min(location.getX(), 100 - location.getWidth()));
        }
        if (location.getY() != null && location.getHeight() != null && location.getHeight() > 0) {
            location.setY(Math.min(location.getY(), 100 - location.getHeight()));
        }
        if (location.getId() == null) {
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
        vo.setMemoryEnabled(configService.getConfigValue("sandbox_memory_enabled", "1"));
        vo.setMemoryTime(configService.getConfigValue("sandbox_memory_time", "23:50"));
        vo.setMemoryPromptDays(configService.getConfigValue("sandbox_memory_prompt_days", "5"));
        vo.setMemoryDeleteActs(configService.getConfigValue("sandbox_memory_delete_acts", "0"));
        vo.setAiIntervalEnabled(configService.getConfigValue("sandbox_ai_interval_enabled", "1"));
        vo.setAiIntervalMin(configService.getConfigValue("sandbox_ai_interval_min", "15"));
        vo.setAiIntervalMax(configService.getConfigValue("sandbox_ai_interval_max", "720"));
        vo.setNewsTitle(configService.getConfigValue("sandbox_news_title", "旅人纪闻"));
        vo.setNewsEnabled(configService.getConfigValue("sandbox_news_enabled", "1"));
        vo.setNewsPerGenerate(configService.getConfigValue("sandbox_news_per_generate", "3"));
        vo.setNewsProviderId(configService.getConfigValue("sandbox_news_provider_id", ""));
        vo.setNewsModel(configService.getConfigValue("sandbox_news_model", ""));
        vo.setNewsPromptExtra(configService.getConfigValue("sandbox_news_prompt_extra", ""));
        vo.setNewsAutoEnabled(configService.getConfigValue("sandbox_news_auto_enabled", "1"));
        vo.setNewsAutoTime(configService.getConfigValue("sandbox_news_auto_time", "07:00"));
        vo.setSystemModel(configService.getConfigValue("sandbox_system_model", ""));
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
        writeSetting("sandbox_memory_enabled", vo.getMemoryEnabled());
        writeSetting("sandbox_memory_time", vo.getMemoryTime());
        writeSetting("sandbox_memory_prompt_days", vo.getMemoryPromptDays());
        writeSetting("sandbox_memory_delete_acts", vo.getMemoryDeleteActs());
        writeSetting("sandbox_ai_interval_enabled", vo.getAiIntervalEnabled());
        writeSetting("sandbox_ai_interval_min", vo.getAiIntervalMin());
        writeSetting("sandbox_ai_interval_max", vo.getAiIntervalMax());
        writeSetting("sandbox_news_title", vo.getNewsTitle());
        writeSetting("sandbox_news_enabled", vo.getNewsEnabled());
        writeSetting("sandbox_news_per_generate", vo.getNewsPerGenerate());
        writeSetting("sandbox_news_provider_id", vo.getNewsProviderId());
        writeSetting("sandbox_news_model", vo.getNewsModel());
        writeSetting("sandbox_news_prompt_extra", vo.getNewsPromptExtra());
        writeSetting("sandbox_news_auto_enabled", vo.getNewsAutoEnabled());
        writeSetting("sandbox_news_auto_time", vo.getNewsAutoTime());
        writeSetting("sandbox_system_model", vo.getSystemModel());
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
    public SandboxCharacterDraftVO generateCharacter(SandboxCharacterGenerateDTO dto) {
        if (dto == null || dto.getRequirement() == null || dto.getRequirement().trim().isEmpty()) {
            throw new BusinessException("请先输入你的角色需求");
        }
        SandboxWorld world = world();
        List<SandboxLocation> locations = locations();
        List<SandboxCharacter> exists = characters();

        AiProvider provider = aiProviderService.resolveManualProvider(dto.getProviderId());
        String raw;
        AuditContext.manual("沙盒·AI 创作角色");
        try {
            raw = aiProviderService.chat(provider, dto.getModel(),
                    buildDraftSystemPrompt(),
                    buildDraftUserPrompt(world, locations, exists, dto.getRequirement().trim()), 0.9);
        } finally {
            AuditContext.clear();
        }
        JSONObject obj = parseJson(raw);
        if (obj == null) {
            throw new BusinessException("AI 返回内容无法解析成 JSON，请重试或换一个模型");
        }
        String name = truncate(trimToEmpty(obj.getStr("name")), 90);
        if (name.isEmpty()) {
            throw new BusinessException("AI 没有给出角色名，请重试或换一个模型");
        }

        SandboxCharacterDraftVO vo = new SandboxCharacterDraftVO();
        vo.setName(name);
        vo.setTitle(truncate(trimToEmpty(obj.getStr("title")), 90));
        vo.setAppearance(truncate(trimToEmpty(obj.getStr("appearance")), 480));
        vo.setPersona(truncate(trimToEmpty(obj.getStr("persona")), 2000));

        // 初始地点：能对上地图里的地点就用它的坐标，对不上则保留 AI 给的名字与坐标
        String locationName = truncate(trimToEmpty(obj.getStr("location")), 90);
        SandboxLocation matched = matchLocation(locations, locationName);
        if (matched != null) {
            vo.setLocationName(matched.getName());
            vo.setX(matched.getX());
            vo.setY(matched.getY());
        } else {
            vo.setLocationName(locationName.isEmpty() ? null : locationName);
            vo.setX(clamp(Convert.toInt(obj.get("x"), 50)));
            vo.setY(clamp(Convert.toInt(obj.get("y"), 50)));
        }
        vo.setSubLocation(truncate(trimToEmpty(obj.getStr("sub_location")), 90));

        // 初始状态：标准项限幅到 0~100，其余键原样保留
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("体力", 100);
        status.put("魔力", 100);
        status.put("饥饿度", 20);
        status.put("心情", "平静");
        Object rawStatus = obj.get("status");
        if (rawStatus instanceof JSONObject) {
            JSONObject statusObj = (JSONObject) rawStatus;
            for (String key : statusObj.keySet()) {
                if (key == null || key.trim().isEmpty()) {
                    continue;
                }
                status.put(key.trim(), normalizeStatusValue(key.trim(), statusObj.get(key)));
            }
        }
        vo.setStatus(status);

        int coins = Convert.toInt(obj.get("coins"), 10);
        vo.setCoins(Math.max(0, Math.min(100, coins)));

        // 初始物品：最多 6 件，数量 1~5，品质按 AI 给的或按名字推断
        List<SandboxItem> items = new ArrayList<>();
        JSONArray itemArray = obj.getJSONArray("items");
        if (itemArray != null) {
            for (int i = 0; i < itemArray.size() && items.size() < 6; i++) {
                Object node = itemArray.get(i);
                if (!(node instanceof JSONObject)) {
                    continue;
                }
                JSONObject itemObj = (JSONObject) node;
                String itemName = truncate(trimToEmpty(itemObj.getStr("name")), 60);
                if (itemName.isEmpty()) {
                    continue;
                }
                SandboxItem item = new SandboxItem();
                item.setName(itemName);
                item.setQuantity(Math.max(1, Math.min(5, Convert.toInt(itemObj.get("quantity"), 1))));
                Integer rarity = Convert.toInt(itemObj.get("rarity"), null);
                item.setRarity(rarity == null ? inferRarity(itemName) : Math.max(1, Math.min(5, rarity)));
                item.setDescription(truncate(trimToEmpty(itemObj.getStr("description")), 200));
                items.add(item);
            }
        }
        vo.setItems(items);
        vo.setRaw(truncate(raw, 4000));
        return vo;
    }

    /** AI 生成角色草稿的系统提示词 */
    private String buildDraftSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位二次元幻想世界的角色设计师。请根据世界观与管理员的需求设计一个新角色，")
                .append("并给出可以直接开局的初始设定。\n")
                .append("你必须严格只输出一个 JSON 对象，不要输出解释、前言、后缀，也不要使用 Markdown 代码块标记。JSON 结构如下：\n")
                .append("{\"name\":\"角色名（2~4 个字）\",\"title\":\"称号（6~12 字）\",")
                .append("\"appearance\":\"外貌描述（40~80 字）\",")
                .append("\"persona\":\"人设（200~400 字，用 \\n 分行，包含身份、性格、说话方式、目标、能力、禁忌）\",")
                .append("\"location\":\"初始所在地点，必须从【地图地点】里选一个\",")
                .append("\"sub_location\":\"初始所在的小地方，自己创作，4~12 字\",")
                .append("\"status\":{\"体力\":100,\"魔力\":100,\"饥饿度\":20,\"心情\":\"平静\"},")
                .append("\"coins\":10,")
                .append("\"items\":[{\"name\":\"干粮\",\"quantity\":2,\"rarity\":1,\"description\":\"用油纸包着的干粮\"}]}\n")
                .append("要求：\n")
                .append("1. 角色名不要与【已有角色】重复，人设也不要去撞已有角色的定位与身份；\n")
                .append("2. 必须符合【世界观】的风格；location 只能从【地图地点】里挑一个；\n")
                .append("3. status 里体力、魔力、饥饿度是 0~100 的整数，心情用简短词语；\n")
                .append("4. coins 是初始金币，0~30 之间的整数；\n")
                .append("5. items 是背包里的初始物品，2~4 件，都是符合身份的日常小物件；")
                .append("rarity 用 1~5（1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说），不要给神器；\n")
                .append("6. 不要输出立绘、绘图关键词、英文名或任何与 JSON 无关的内容。");
        return sb.toString();
    }

    /** AI 生成角色草稿的用户提示词：世界观 + 地图地点 + 已有角色 + 管理员需求 */
    private String buildDraftUserPrompt(SandboxWorld world, List<SandboxLocation> locations,
                                        List<SandboxCharacter> exists, String requirement) {
        StringBuilder sb = new StringBuilder();
        sb.append("【世界观】\n").append(blankToDefault(world.getWorldPrompt(), DEFAULT_WORLD_PROMPT)).append("\n\n");
        sb.append("【地图地点】");
        if (locations.isEmpty()) {
            sb.append("（管理员还没有添加地点，可以自行设定一个合理的地点名）");
        } else {
            List<String> names = new ArrayList<>();
            for (SandboxLocation location : locations) {
                String text = location.getName();
                if (notBlank(location.getDescription())) {
                    text += "（" + truncate(location.getDescription(), 30) + "）";
                }
                names.add(text);
            }
            sb.append(String.join("、", names));
        }
        sb.append("\n【已有角色】");
        if (exists.isEmpty()) {
            sb.append("（暂无）");
        } else {
            List<String> characters = new ArrayList<>();
            for (SandboxCharacter character : exists) {
                String text = character.getName();
                if (notBlank(character.getTitle())) {
                    text += "（" + character.getTitle() + "）";
                }
                characters.add(text);
            }
            sb.append(String.join("、", characters));
        }
        sb.append("\n\n【管理员需求】\n").append(requirement);
        return sb.toString();
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
        // 背包物品与每日记忆一起清理
        itemMapper.delete(new LambdaQueryWrapper<SandboxItem>().eq(SandboxItem::getCharacterId, id));
        memoryMapper.delete(new LambdaQueryWrapper<SandboxMemory>().eq(SandboxMemory::getCharacterId, id));
        characterMapper.deleteById(id);
    }

    // ============================== 行动记录 ==============================

    @Override
    public PageResult<SandboxAct> acts(Long characterId, String locationName, long page, long size) {
        LambdaQueryWrapper<SandboxAct> wrapper = new LambdaQueryWrapper<SandboxAct>()
                .eq(characterId != null, SandboxAct::getCharacterId, characterId)
                .eq(locationName != null && !locationName.trim().isEmpty(),
                        SandboxAct::getLocationName, locationName == null ? null : locationName.trim())
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
        // 长期记忆（每日总结）与背包，都会写进提示词
        List<SandboxMemory> memories = recentMemories(characterId, intConfig("sandbox_memory_prompt_days", 5));
        List<SandboxItem> backpack = items(characterId);
        // 今天的旅人纪闻：行动时会参考，但不强制参与
        List<SandboxNews> news = todayNews();

        String systemPrompt = buildSystemPrompt(character, world, locations, companions);
        String userPrompt = buildUserPrompt(character, recent, whispers, companions, companionActs, reaction,
                trigger == null ? null : characterNameOf(companions, trigger.getCharacterId()), trigger,
                memories, backpack, news);

        String raw;
        try {
            double temperature = character.getTemperature() == null ? 0.9 : character.getTemperature().doubleValue();
            // 手动执行用发起人自己的服务商，定时执行用系统服务商
            AiProvider provider = manual
                    ? aiProviderService.resolveManualProvider(character.getProviderId())
                    : aiProviderService.resolveSystemProvider(character.getProviderId());
            if (manual) {
                AuditContext.manual(reaction ? "沙盒·回应回合" : "沙盒·立即执行一次");
            } else {
                AuditContext.schedule(reaction ? "沙盒·自动回应" : "沙盒·自动行动");
            }
            try {
                // 需要「下次行动间隔 + 原因」：若 AI 漏给会自动重试（最多 3 次）；
                // 开启输出自查时，自查也放在这个循环里，先自查再检查字段，避免自查删掉的字段没人救
                raw = callAiWithInterval(character, provider, systemPrompt, userPrompt, temperature,
                        locations, companions);
            } finally {
                AuditContext.clear();
            }
        } catch (Exception e) {
            // 失败时只记录原因，不生成记录，避免接口异常时时间线被刷屏
            String msg = e.getMessage() == null ? "AI 调用失败" : e.getMessage();
            characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                    .eq(SandboxCharacter::getId, characterId)
                    .set(SandboxCharacter::getLastError, truncate(msg, 480)));
            throw e instanceof BusinessException ? (BusinessException) e : new BusinessException(msg);
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
        String subLocation = character.getSubLocation();
        String statusJson = character.getStatusJson();
        int coins = character.getCoins() == null ? 0 : character.getCoins();
        int coinChange = 0;
        Integer aiNextMinutes = null;
        String aiNextReason = null;

        JSONObject obj = parseJson(raw);
        if (obj == null) {
            // AI 没有按格式返回：原文保存下来，方便管理员在后台看到并调整提示词
            act.setFromAi(0);
            // 注意：沙盒角色的 AI 回复不做敏感词过滤，保持原文（过滤会误伤正常词汇）
            act.setActions(truncate(trimToEmpty(raw), 1000));
            act.setSummary("AI 返回内容不是约定的 JSON，已原样保存");
        } else {
            act.setFromAi(1);
            String aiLocation = obj.getStr("location");
            Integer aiX = Convert.toInt(obj.get("x"), null);
            Integer aiY = Convert.toInt(obj.get("y"), null);
            // 1) 先按名字找地点；名字对不上时，看坐标落在哪个区域里
            SandboxLocation target = matchLocation(locations, aiLocation);
            if (target == null && aiX != null && aiY != null) {
                target = locationAtPoint(locations, clamp(aiX), clamp(aiY));
            }
            // 2) 坐标缺省时用地点中心补齐
            if (target != null && (aiX == null || aiY == null)) {
                aiX = centerX(target);
                aiY = centerY(target);
            }
            // 3) 把坐标夹进所选地点的区域，保证角色确实落在这一片地区里
            if (target != null && aiX != null && aiY != null) {
                int[] fixed = clampToArea(target, clamp(aiX), clamp(aiY));
                aiX = fixed[0];
                aiY = fixed[1];
            }
            x = clamp(aiX == null ? x : aiX);
            y = clamp(aiY == null ? y : aiY);

            if (target != null) {
                locationName = target.getName();
            } else if (aiLocation == null || aiLocation.trim().isEmpty()) {
                SandboxLocation nearest = nearestLocation(locations, x, y);
                locationName = nearest == null ? locationName : nearest.getName();
            } else {
                locationName = truncate(aiLocation.trim(), 90);
            }

            // 二级地点：AI 自行创作；没给且一级地点没变就沿用上一次，换地方了则清空
            String previousLocation = character.getLocationName();
            boolean locationChanged = previousLocation == null
                    ? locationName != null
                    : !previousLocation.equals(locationName);
            String aiSubLocation = obj.getStr("sub_location");
            if (aiSubLocation != null && !aiSubLocation.trim().isEmpty()) {
                subLocation = truncate(aiSubLocation.trim(), 90);
            } else if (locationChanged) {
                subLocation = null;
            }

            act.setLocationName(locationName);
            act.setSubLocation(subLocation);
            act.setX(x);
            act.setY(y);
            // 沙盒角色行动是 AI 创作内容，按管理员设置不做敏感词过滤，保持原文
            act.setActions(truncate(joinActions(obj.getJSONArray("actions")), 1000));
            act.setInnerVoice(truncate(trimToEmpty(obj.getStr("inner_voice")), 1000));
            act.setSummary(truncate(trimToEmpty(obj.getStr("summary")), 280));
            // 这一步和哪些角色互动了（只保留世界里真实存在的角色名）
            act.setCompanions(matchCompanions(obj.getJSONArray("companions"), companions));
            // 好感度变化：AI 返回 { "角色名": 3 }，服务端累加到对应关系上
            act.setFavorChange(applyFavorChanges(character, obj.get("favor_changes"), companions));
            // 物品变化：AI 返回 { "物品名": 1 }，正为获得、负为消耗
            act.setItemChange(applyItemChanges(character, obj.get("items_change")));
            // 由 AI 决定下一次隔多久再行动（例如睡一觉就是几小时）
            aiNextMinutes = Convert.toInt(obj.get("next_after_minutes"), null);
            aiNextReason = truncate(trimToEmpty(obj.getStr("next_after_reason")), 40);
            // 重试后仍然没给原因时，补一个默认原因，避免前台只显示时间
            if (aiNextReason == null || aiNextReason.isEmpty()) {
                aiNextReason = "稍作停留";
            }
            act.setNextAfterMinutes(aiNextMinutes == null || aiNextMinutes < 0 ? 0 : aiNextMinutes);
            act.setNextAfterReason(aiNextReason == null || aiNextReason.isEmpty() ? null : aiNextReason);
            // 这一步参考/听说了哪几条纪闻
            act.setNewsRef(matchNewsRefs(obj.getJSONArray("news_refs"), news));

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

        LocalDateTime next = resolveNextRunTime(character, now, aiNextMinutes);
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, characterId)
                .set(SandboxCharacter::getX, x)
                .set(SandboxCharacter::getY, y)
                .set(SandboxCharacter::getLocationName, locationName)
                .set(SandboxCharacter::getSubLocation, subLocation)
                .set(SandboxCharacter::getStatusJson, statusJson)
                .set(SandboxCharacter::getCoins, coins)
                .set(SandboxCharacter::getLastRunTime, now)
                .set(SandboxCharacter::getNextRunTime, next)
                .set(SandboxCharacter::getNextReason, aiNextReason == null || aiNextReason.isEmpty() ? null : aiNextReason)
                .set(SandboxCharacter::getLastError, null));

        actMapper.insert(act);
        // 有金币变化时记一笔流水，前台和后台都能看到角色是怎么赚钱花钱的
        if (coinChange != 0) {
            addCoinLog(characterId, null, null, coinChange > 0 ? "earn" : "spend", coinChange, 0, coins,
                    blankToDefault(act.getSummary(), coinChange > 0 ? "日常赚取" : "日常花销"));
        }
        return act;
    }

    /**
     * 调用 AI 生成行动，并要求带上「下次行动间隔 + 原因」。
     * 如果 AI 漏掉这两个字段，会自动重试（最多 3 次）；仍未给出时返回「字段最全」的那一次结果，
     * 由调用方按默认随机间隔兜底，并补一个默认原因。
     *
     * 重试时会把「上一次到底缺了哪个字段」明确写进提示词：系统提示词里追加一段强制修正说明，
     * 用户提示词的**开头和结尾**各放一次提醒（模型对首尾内容最敏感），并把字段位置也点明。
     *
     * 执行顺序：主调用 →（可选）输出自查 → 字段完整性检查。自查排在检查之前，
     * 这样即使自查把字段改没了，也会被下面的检查发现并触发重试，而不是直接落库。
     */
    private String callAiWithInterval(SandboxCharacter character, AiProvider provider,
                                      String systemPrompt, String userPrompt, double temperature,
                                      List<SandboxLocation> locations, List<SandboxCharacter> companions) {
        boolean verifyEnabled = "1".equals(configService.getConfigValue("sandbox_verify_enabled", "0"));
        // 记下主调用的审计标签，自查时临时换成「输出自查」，查完再还原，保证日志能区分两种调用
        String mainAction = AuditContext.action();
        boolean mainScheduled = AuditContext.isSchedule();

        String raw = null;
        // 兜底：记录「字段最全」的那一次输出，最后全都缺字段时用它（比直接用最后一次更合理）
        String best = null;
        int bestMissing = Integer.MAX_VALUE;
        // 上一次输出缺失的字段说明，第一次调用时为空（首次使用原始提示词）
        List<String> missing = Collections.emptyList();
        for (int attempt = 1; attempt <= 3; attempt++) {
            boolean retry = attempt > 1 && !missing.isEmpty();
            String attemptSystem = retry ? systemPrompt + buildRetrySystemSuffix(missing) : systemPrompt;
            String attemptUser = retry ? buildRetryUserPrompt(userPrompt, missing) : userPrompt;
            raw = aiProviderService.chat(provider, character.getModel(),
                    attemptSystem, attemptUser, temperature);
            String candidate = raw;
            if (verifyEnabled) {
                // 自查单独打审计标签，方便和主调用区分
                if (mainScheduled) {
                    AuditContext.schedule("沙盒·输出自查");
                } else {
                    AuditContext.manual("沙盒·输出自查");
                }
                try {
                    candidate = verifyOutput(character, raw, locations, companions);
                } finally {
                    // 还原主调用的审计标签，避免后续重试被记成自查
                    if (mainScheduled) {
                        AuditContext.schedule(mainAction);
                    } else {
                        AuditContext.manual(mainAction);
                    }
                }
            }
            missing = findMissingIntervalFields(parseJson(candidate));
            if (missing.isEmpty()) {
                return candidate;
            }
            if (missing.size() < bestMissing) {
                best = candidate;
                bestMissing = missing.size();
            }
            if (attempt < 3) {
                log.warn("沙盒角色「{}」第 {} 次输出缺少字段（{}），准备重试", character.getName(), attempt,
                        String.join("、", missing));
            } else {
                log.warn("沙盒角色「{}」重试 {} 次后仍缺少字段（{}），改用默认间隔兜底", character.getName(), attempt,
                        String.join("、", missing));
            }
        }
        return best == null ? raw : best;
    }

    /**
     * 检查 AI 输出的「下次行动间隔」相关字段是否齐全。
     * 返回缺失字段的中文说明列表；返回空列表表示字段齐全（可以正常入库）。
     */
    private List<String> findMissingIntervalFields(JSONObject obj) {
        List<String> missing = new ArrayList<>();
        if (obj == null) {
            // 连 JSON 都没解析出来，属于更严重的情况，单独提示
            missing.add("整段输出根本不是合法的 JSON 对象（被多余文字包裹、缺少花括号或使用了代码块标记）");
            return missing;
        }
        Integer minutes = Convert.toInt(obj.get("next_after_minutes"), null);
        if (minutes == null || minutes <= 0) {
            missing.add("next_after_minutes（下一次行动间隔的分钟数，必须是大于 0 的整数）");
        }
        if (!notBlank(obj.getStr("next_after_reason"))) {
            missing.add("next_after_reason（这段时间在做什么的简短说明，2~6 个字）");
        }
        return missing;
    }

    /** 重试时追加到系统提示词末尾的强制修正说明：点名缺失字段 + 给出写法示例 */
    private String buildRetrySystemSuffix(List<String> missing) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【上一次输出不合格 · 本次必须修正】\n");
        sb.append("你上一次的回复缺少下列必填内容：\n");
        for (String item : missing) {
            sb.append("  - ").append(item).append("\n");
        }
        sb.append("这两个字段都不是可选项，也不能写 null、不能省略、不能留空：\n");
        sb.append("  · next_after_minutes：大于 0 的整数分钟数，例如 45、120、360、540；\n");
        sb.append("  · next_after_reason：2~6 个字的中文短语，说明这段时间在做什么，例如「睡觉」「赶路」「研究符文」「吃午饭」。\n");
        sb.append("请重新输出**完整**的 JSON（其余字段一个都不能少）：把 next_after_minutes 和 next_after_reason ")
                .append("放在整个 JSON 的**最前面**，再依次写 location、actions 等字段，间隔要与你的行动相符。");
        return sb.toString();
    }

    /**
     * 重试时的用户提示词：在原文开头和结尾各插一段提醒。
     * 放在开头是因为模型对提示词首尾最敏感，放在结尾是为了防止长上下文冲淡要求。
     */
    private String buildRetryUserPrompt(String userPrompt, List<String> missing) {
        StringBuilder head = new StringBuilder();
        head.append("【重要提醒 · 请先读这段】\n");
        head.append("你上一次的输出缺少以下必填字段：").append(String.join("；", missing)).append("。\n");
        head.append("这次必须在 JSON 的**最前面**先写出这两个字段，再写 location 等其余字段：\n");
        head.append("  \"next_after_minutes\": <大于 0 的整数分钟>,\n");
        head.append("  \"next_after_reason\": \"<2~6 个字，说明这段时间在做什么>\",\n");
        head.append("缺任何一个都会导致本次输出被判为失败并再次重试。\n\n");

        String tail = "\n\n【再次强调】本次输出必须以 "
                + "{\"next_after_minutes\":<整数>,\"next_after_reason\":\"<简短原因>\", 开头，"
                + "并且只输出这一段 JSON，不要有任何解释文字或代码块标记。";
        // 长上文容易被模型忽略，因此提醒放在开头和结尾各一次
        return head + userPrompt + tail;
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
        vo.setNewsTitle(configService.getConfigValue("sandbox_news_title", "旅人纪闻"));
        vo.setNews(todayNews());
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
        vo.setSubLocation(character.getSubLocation());
        vo.setEnabled(character.getEnabled());
        vo.setStatus(parseStatus(character.getStatusJson()));
        vo.setCoins(character.getCoins() == null ? 0 : character.getCoins());
        vo.setNextRunTime(character.getNextRunTime());
        vo.setNextReason(character.getNextReason());
        vo.setLastRunTime(character.getLastRunTime());
        vo.setRelations(relationsOf(character.getId()));
        vo.setItems(items(character.getId()));
        List<SandboxMemory> memories = memoryMapper.selectList(new LambdaQueryWrapper<SandboxMemory>()
                .eq(SandboxMemory::getCharacterId, character.getId())
                .orderByDesc(SandboxMemory::getMemoryDate)
                .last("limit " + PORTAL_MEMORY_LIMIT));
        vo.setRecentMemories(memories);
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
    /** 事件与角色的距离描述，用自然语言表达，避免生硬数字 */
    private String distanceText(int x1, int y1, int x2, int y2) {
        double dx = x1 - x2;
        double dy = (y1 - y2) * 9.0 / 16.0;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= 6) {
            return "就在同一个地区";
        }
        if (distance <= 20) {
            return "大约半天路程";
        }
        if (distance <= 40) {
            return "大约一两天路程";
        }
        return "非常遥远";
    }

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
                int lx = location.getX() == null ? 50 : location.getX();
                int ly = location.getY() == null ? 50 : location.getY();
                int lw = location.getWidth() == null ? 0 : location.getWidth();
                int lh = location.getHeight() == null ? 0 : location.getHeight();
                sb.append("- ").append(location.getName());
                if (lw > 0 && lh > 0) {
                    sb.append("（区域 x ").append(lx).append("~").append(lx + lw)
                            .append("、y ").append(ly).append("~").append(ly + lh)
                            .append("；中心 x=").append(lx + lw / 2).append(", y=").append(ly + lh / 2).append("）");
                } else {
                    sb.append("（坐标 x=").append(lx).append(", y=").append(ly).append("）");
                }
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
                sb.append("：当前在").append(blankToDefault(placeText(other.getLocationName(), other.getSubLocation()), "某处"))
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
                .append("{\"next_after_minutes\":45,\"next_after_reason\":\"稍作停留\",")
                .append("\"location\":\"这一步所处的地点名称，尽量使用【地图地点】里的名字\",")
                .append("\"sub_location\":\"这一步具体所在的小地方（自己创作）\",\"x\":35,\"y\":62,")
                .append("\"actions\":[\"具体动作一\",\"具体动作二\"],\"inner_voice\":\"角色此刻的心里话（第一人称，一句话）\",")
                .append("\"status\":{\"体力\":80,\"魔力\":45,\"饥饿度\":30,\"心情\":\"平静\"},")
                .append("\"coins_change\":0,\"companions\":[],\"favor_changes\":{\"角色名\":3},")
                .append("\"items_change\":{\"物品名\":1},")
                .append("\"news_refs\":[],\"summary\":\"30 字以内概括这一步\"}\n")
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
                .append("7. items_change 表示背包物品的变化，格式是 {\"物品名\": 数量变化}：")
                .append("获得东西填正数（例如采到草药 2、买到干粮 1），用掉或丢失填负数（例如吃掉干粮 -1）；")
                .append("没有变化填 {}。物品是具体的小东西（干粮、草药、萤石灯、旧地图、戒指……），")
                .append("金币请写在 coins_change 里、不要当成物品；物品名要简短且与 actions 描述一致，")
                .append("单次数量变化不超过 ±9，不要凭空得到贵重或神器的东西。\n")
                .append("8. 背包里已有的物品可以继续使用或送人；【最近的记忆】是你对过去几天的印象，")
                .append("请保持人设与记忆连贯，不要做出与记忆矛盾的事。\n")
                .append("9. 整体风格温和、日常、有生活感，避免暴力与不适内容。\n")
                .append("10. sub_location 是二级地点：请根据你所在的一级地点，自行创作一个具体的小地方，")
                .append("例如「东侧集市」「城墙下的旧书摊」「酒馆二层」「长满萤石的树洞」；")
                .append("要求 4~12 个字、具体可感、与一级地点的风格一致，不要直接重复一级地点名，")
                .append("也不要写「某处」「附近」这类空泛的词；换了地方就换一个新的小地点，留在原地可以沿用同一个。");
        sb.append("\n11. x / y 必须写在你所选地点的范围内（地点后面标注了区域范围），")
                .append("不要越界到别的地区；如果只是在这个地区里走动，仍然用同一个地点名。");
        sb.append("\n12. next_after_minutes 由你自己决定下一次行动隔多久（整数分钟，")
                .append(intConfig("sandbox_ai_interval_min", 15)).append("~")
                .append(intConfig("sandbox_ai_interval_max", 720)).append(" 之间），")
                .append("表示你觉得过多久才会开始下一步；即使拿不准，也要结合行动内容给一个合理的估计值，不要留空。")
                .append("next_after_reason 用 2~6 个字说明这段时间在做什么（例如「睡觉」「赶路」「研究符文」），")
                .append("同样不能留空。");
        sb.append("\n    这两个字段都是**必填项**：next_after_minutes 必须大于 0，next_after_reason 必须填写，")
                .append("并且间隔要和你的行动相符。几种常见行为的参考间隔：\n")
                .append("    · 睡觉 / 过夜休息：360~600 分钟（午睡、打盹 30~90 分钟）\n")
                .append("    · 长途赶路 / 出海：120~240 分钟\n")
                .append("    · 专注做事（研究符文、熬药、读书）：60~180 分钟\n")
                .append("    · 吃饭 / 逛街 / 采买：15~45 分钟\n")
                .append("    · 短暂交谈 / 试探搭话：10~30 分钟\n")
                .append("    · 警戒 / 守夜 / 等待：30~90 分钟\n")
                .append("    不要为了省事给一个和行动无关的短间隔（例如「睡觉」却只隔 15 分钟）。");
        sb.append("\n13. news_refs 是数组：如果你这一步听说了、议论了或关注了【今日要闻】里的某条事件，")
                .append("就把那条事件的原句填进去（必须与上面列出的标题完全一致），没有就填 []；")
                .append("听说并不代表一定要参与。");
        sb.append("\n14. 背包管理：每次行动都顺便看一眼背包——能用掉的就用掉（吃掉干粮、喝掉药水等，")
                .append("让饥饿度或体力、魔力得到恢复），用不上的可以丢掉或送人（在 items_change 里写负数，")
                .append("数量减到 0 会自动从背包移除）；不要长期囤积用不上的东西，也不要一次丢光所有物资。");
        return sb.toString();
    }

    /** 用户提示词：当前状态 + 最近记忆 + 其他居民的动静 + 旅人的话 + 本次指令 */
    private String buildUserPrompt(SandboxCharacter c, List<SandboxAct> recent, List<SandboxInteraction> whispers,
                                   List<SandboxCharacter> companions, List<SandboxAct> companionActs,
                                   boolean reaction, String triggerName, SandboxAct trigger,
                                   List<SandboxMemory> memories, List<SandboxItem> backpack,
                                   List<SandboxNews> news) {
        StringBuilder sb = new StringBuilder();
        LocalDateTime now = LocalDateTime.now();
        sb.append("【当前状态】\n")
                .append("现在时间：").append(timeText(now)).append("\n")
                .append("当前位置：").append(blankToDefault(placeText(c.getLocationName(), c.getSubLocation()), "尚未确定"))
                .append("（x=").append(c.getX() == null ? 50 : c.getX())
                .append(", y=").append(c.getY() == null ? 50 : c.getY()).append("）\n")
                .append("身上金币：").append(c.getCoins() == null ? 0 : c.getCoins()).append(" 枚\n");
        Map<String, Object> status = parseStatus(c.getStatusJson());
        if (!status.isEmpty()) {
            sb.append("当前状态：").append(JSONUtil.toJsonStr(status)).append("\n");
        }
        // 背包：把物品清单明确列出来，AI 才知道自己身上有什么
        sb.append("背包物品：");
        if (backpack.isEmpty()) {
            sb.append("（空空如也）\n");
        } else {
            List<String> itemTexts = new ArrayList<>();
            for (SandboxItem item : backpack) {
                itemTexts.add(item.getName() + " x" + (item.getQuantity() == null ? 1 : item.getQuantity()));
            }
            sb.append(String.join("、", itemTexts)).append("\n");
            if (backpack.size() >= 12) {
                sb.append("（背包已经有 ").append(backpack.size())
                        .append(" 种物品，比较满了：这一步可以顺手用掉、送人或丢掉一些不常用的东西）\n");
            }
        }
        if (!memories.isEmpty()) {
            sb.append("\n【最近的记忆】按时间从早到晚，这是你对过去几天的印象：\n");
            List<SandboxMemory> ordered = new ArrayList<>(memories);
            ordered.sort((a, b) -> {
                LocalDate da = a.getMemoryDate() == null ? LocalDate.MIN : a.getMemoryDate();
                LocalDate db = b.getMemoryDate() == null ? LocalDate.MIN : b.getMemoryDate();
                return da.compareTo(db);
            });
            for (SandboxMemory memory : ordered) {
                sb.append("- ").append(memory.getMemoryDate()).append("：")
                        .append(blankToDefault(memory.getSummary(), "（这天没有留下什么印象）"))
                        .append("\n");
            }
        }
        // 回应回合：把「刚刚发生了什么」明确写出来，避免被搭话的一方毫不知情
        if (reaction && trigger != null) {
            sb.append("\n【刚刚发生的事】").append(blankToDefault(triggerName, "另一位居民"))
                    .append(" 于 ").append(trigger.getCreateTime() == null ? ""
                            : trigger.getCreateTime().format(DATE_TIME_FORMATTER))
                    .append(" 在 ").append(blankToDefault(placeText(trigger.getLocationName(), trigger.getSubLocation()), "某处")).append("：\n")
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
                        .append(" 在").append(blankToDefault(placeText(act.getLocationName(), act.getSubLocation()), "某处")).append("：")
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
                        .append(" 在").append(blankToDefault(placeText(act.getLocationName(), act.getSubLocation()), "某处")).append("：")
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
        if (!news.isEmpty()) {
            sb.append("\n【今日要闻】世界上今天发生的事（你可以听说、议论、担心，也可以决定前往，但不一定要参与）：\n");
            int myX = c.getX() == null ? 50 : c.getX();
            int myY = c.getY() == null ? 50 : c.getY();
            for (SandboxNews item : news) {
                sb.append("- ").append(item.getTitle());
                if (notBlank(item.getLocationName())) {
                    sb.append("（发生在").append(item.getLocationName());
                    if (item.getX() != null && item.getY() != null) {
                        sb.append("，距离你").append(distanceText(myX, myY, item.getX(), item.getY()));
                    }
                    sb.append("）");
                }
                sb.append("\n");
            }
            sb.append("（请结合距离与自身状态——体力、魔力、金币、正在做的事——决定是否关注或前往；")
                    .append("也可以完全不理会，只在心里想一想。）\n");
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

    /** 把 AI 返回的 news_refs 收敛为今天真实存在的纪闻标题，避免编造 */
    private String matchNewsRefs(JSONArray array, List<SandboxNews> news) {
        if (array == null || array.isEmpty() || news == null || news.isEmpty()) {
            return null;
        }
        List<String> hits = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String text = array.getStr(i);
            if (text == null) {
                continue;
            }
            String target = text.trim();
            if (target.isEmpty()) {
                continue;
            }
            for (SandboxNews item : news) {
                String title = item.getTitle();
                if (title == null || hits.contains(title)) {
                    continue;
                }
                // 兼容 AI 只写事件关键词的情况
                if (title.equals(target) || title.contains(target) || target.contains(title)) {
                    hits.add(title);
                }
            }
        }
        return hits.isEmpty() ? null : truncate(String.join("、", hits), 290);
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
            vo.setTargetLocation(placeText(other.getLocationName(), other.getSubLocation()));
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

    // ============================== 背包 ==============================

    @Override
    public List<SandboxItem> items(Long characterId) {
        if (characterId == null) {
            return new ArrayList<>();
        }
        return itemMapper.selectList(new LambdaQueryWrapper<SandboxItem>()
                .eq(SandboxItem::getCharacterId, characterId)
                .orderByAsc(SandboxItem::getId));
    }

    @Override
    public SandboxItem saveItem(SandboxItem item) {
        if (item.getCharacterId() == null) {
            throw new BusinessException("请选择角色");
        }
        if (item.getName() == null || item.getName().trim().isEmpty()) {
            throw new BusinessException("物品名称不能为空");
        }
        item.setName(item.getName().trim());
        if (item.getQuantity() == null || item.getQuantity() < 1) {
            item.setQuantity(1);
        }
        // 品质限幅 1~5；调用方没传时不覆盖已有品质，新建时按物品名推断
        Integer rarity = item.getRarity() == null ? null : Math.max(1, Math.min(5, item.getRarity()));
        String icon = item.getIcon() == null ? null : item.getIcon().trim();
        SandboxItem exists = itemMapper.selectOne(new LambdaQueryWrapper<SandboxItem>()
                .eq(SandboxItem::getCharacterId, item.getCharacterId())
                .eq(SandboxItem::getName, item.getName())
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (exists == null) {
            if (item.getWorldId() == null) {
                item.setWorldId(worldId());
            }
            item.setRarity(rarity == null ? inferRarity(item.getName()) : rarity);
            item.setIcon(icon == null || icon.isEmpty() ? null : icon);
            item.setId(null);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            itemMapper.insert(item);
            return item;
        }
        // 只更新调用方显式传过来的字段，避免改数量时把图标/品质/说明清空
        LambdaUpdateWrapper<SandboxItem> wrapper = new LambdaUpdateWrapper<SandboxItem>()
                .eq(SandboxItem::getId, exists.getId())
                .set(SandboxItem::getQuantity, item.getQuantity())
                .set(SandboxItem::getUpdateTime, now);
        if (rarity != null) {
            wrapper.set(SandboxItem::getRarity, rarity);
        }
        if (icon != null) {
            wrapper.set(SandboxItem::getIcon, icon.isEmpty() ? null : icon);
        }
        if (item.getDescription() != null) {
            wrapper.set(SandboxItem::getDescription, item.getDescription());
        }
        itemMapper.update(null, wrapper);
        item.setId(exists.getId());
        item.setRarity(rarity == null ? exists.getRarity() : rarity);
        item.setIcon(icon == null ? exists.getIcon() : icon);
        return item;
    }

    @Override
    public void deleteItem(Long id) {
        itemMapper.deleteById(id);
    }

    // ============================== 旅人纪闻 ==============================

    @Override
    public List<SandboxNews> todayNews() {
        return newsMapper.selectList(new LambdaQueryWrapper<SandboxNews>()
                .eq(SandboxNews::getNewsDate, LocalDate.now())
                .eq(SandboxNews::getEnabled, 1)
                .orderByDesc(SandboxNews::getPinned)
                .orderByDesc(SandboxNews::getLevel)
                .orderByDesc(SandboxNews::getId)
                .last("limit 10"));
    }

    @Override
    public PageResult<SandboxNews> newsPage(String date, long page, long size) {
        LambdaQueryWrapper<SandboxNews> wrapper = new LambdaQueryWrapper<SandboxNews>()
                .orderByDesc(SandboxNews::getNewsDate)
                .orderByDesc(SandboxNews::getPinned)
                .orderByDesc(SandboxNews::getId);
        if (date == null || date.trim().isEmpty()) {
            wrapper.eq(SandboxNews::getNewsDate, LocalDate.now());
        } else if (!"all".equalsIgnoreCase(date.trim())) {
            try {
                wrapper.eq(SandboxNews::getNewsDate, LocalDate.parse(date.trim()));
            } catch (Exception e) {
                throw new BusinessException("日期格式应为 yyyy-MM-dd");
            }
        }
        IPage<SandboxNews> result = newsMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public void saveNews(SandboxNews news) {
        if (news.getTitle() == null || news.getTitle().trim().isEmpty()) {
            throw new BusinessException("事件内容不能为空");
        }
        news.setTitle(news.getTitle().trim());
        if (news.getNewsDate() == null) {
            news.setNewsDate(LocalDate.now());
        }
        if (news.getLevel() == null || news.getLevel() < 1 || news.getLevel() > 3) {
            news.setLevel(1);
        }
        if (news.getPinned() == null) {
            news.setPinned(0);
        }
        if (news.getEnabled() == null) {
            news.setEnabled(1);
        }
        if (news.getWorldId() == null) {
            news.setWorldId(worldId());
        }
        if (news.getSource() == null || news.getSource().trim().isEmpty()) {
            news.setSource("admin");
        }
        // 补坐标：优先匹配地图地点的区域中心
        if (news.getX() == null || news.getY() == null) {
            SandboxLocation matched = matchLocation(locations(), news.getLocationName());
            if (matched != null) {
                news.setX(centerX(matched));
                news.setY(centerY(matched));
                news.setLocationName(matched.getName());
            }
        }
        if (news.getId() == null) {
            news.setCreateTime(LocalDateTime.now());
            newsMapper.insert(news);
        } else {
            newsMapper.updateById(news);
        }
    }

    @Override
    public void deleteNews(Long id) {
        newsMapper.deleteById(id);
    }

    @Override
    public int generateNews(Integer count, Long providerId, String model) {
        int size = count == null ? intConfig("sandbox_news_per_generate", 3) : count;
        size = Math.max(1, Math.min(10, size));
        Long provider = providerId;
        if (provider == null) {
            String configured = configService.getConfigValue("sandbox_news_provider_id", "");
            provider = configured == null || configured.trim().isEmpty() ? null : Convert.toLong(configured.trim(), null);
        }
        String useModel = model;
        if (useModel == null || useModel.trim().isEmpty()) {
            useModel = configService.getConfigValue("sandbox_news_model", "");
        }
        if (useModel == null || useModel.trim().isEmpty()) {
            throw new BusinessException("请先选择生成事件使用的模型");
        }

        SandboxWorld world = world();
        List<SandboxLocation> locations = locations();
        // 最近的世界动向：只取少量角色行动概括，作为氛围参考
        List<String> recentMoves = new ArrayList<>();
        for (SandboxCharacter character : characters()) {
            List<SandboxAct> acts = recentActs(character.getId(), 1);
            for (SandboxAct act : acts) {
                String text = blankToDefault(act.getSummary(), blankToDefault(act.getActions(), ""));
                if (notBlank(text)) {
                    recentMoves.add(character.getName() + " 近日在" + blankToDefault(act.getLocationName(), "某处")
                            + "：" + truncate(text.replace("\n", " "), 60));
                }
            }
        }
        List<String> existing = new ArrayList<>();
        for (SandboxNews news : todayNews()) {
            existing.add(news.getTitle());
        }

        String systemPrompt = buildNewsSystemPrompt(size);
        String userPrompt = buildNewsUserPrompt(world, locations, recentMoves, existing);
        AiProvider newsProvider = aiProviderService.resolveManualProvider(provider);
        if (!AuditContext.isSet()) {
            AuditContext.manual("沙盒·生成旅人纪闻");
        }
        String raw;
        try {
            raw = aiProviderService.chat(newsProvider, useModel.trim(), systemPrompt, userPrompt, 0.95);
        } finally {
            AuditContext.clear();
        }
        JSONObject obj = parseJson(raw);
        if (obj == null) {
            throw new BusinessException("AI 返回内容无法解析成 JSON，请重试或换一个模型");
        }
        JSONArray events = obj.getJSONArray("events");
        if (events == null || events.isEmpty()) {
            throw new BusinessException("AI 没有生成任何事件，请重试或调整附加要求");
        }

        // 生成前顺手清掉过期纪闻（默认只保留当天）
        cleanupExpiredNews();

        int created = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < events.size() && created < size; i++) {
            Object node = events.get(i);
            if (!(node instanceof JSONObject)) {
                continue;
            }
            JSONObject item = (JSONObject) node;
            String title = truncate(trimToEmpty(item.getStr("title")), 190);
            if (title.isEmpty() || existing.contains(title)) {
                continue;
            }
            SandboxNews news = new SandboxNews();
            news.setWorldId(world.getId() == null ? 1L : world.getId());
            news.setTitle(title);
            news.setContent(truncate(trimToEmpty(item.getStr("content")), 490));
            String locationName = truncate(trimToEmpty(item.getStr("location")), 90);
            SandboxLocation matched = matchLocation(locations, locationName);
            if (matched != null) {
                news.setLocationName(matched.getName());
                news.setX(centerX(matched));
                news.setY(centerY(matched));
            } else {
                news.setLocationName(locationName.isEmpty() ? null : locationName);
                news.setX(clamp(Convert.toInt(item.get("x"), 50)));
                news.setY(clamp(Convert.toInt(item.get("y"), 50)));
            }
            int level = Convert.toInt(item.get("level"), 1);
            news.setLevel(Math.max(1, Math.min(3, level)));
            news.setSource("ai");
            news.setNewsDate(LocalDate.now());
            news.setPinned(0);
            news.setEnabled(1);
            news.setCreateTime(now);
            newsMapper.insert(news);
            existing.add(title);
            created++;
        }
        if (created == 0) {
            throw new BusinessException("生成的事件与今天已有的重复，请调整附加要求后重试");
        }
        log.info("旅人纪闻生成完成，新增 {} 条", created);
        return created;
    }

    @Override
    public void autoGenerateNews() {
        if (!"1".equals(configService.getConfigValue("sandbox_news_enabled", "1"))) {
            return;
        }
        boolean preset = AuditContext.isSet();
        if (!preset) {
            AuditContext.schedule("沙盒·自动生成旅人纪闻");
        }
        try {
            int created = generateNews(null, null, null);
            log.info("旅人纪闻自动生成完成，新增 {} 条", created);
        } catch (Exception e) {
            log.warn("旅人纪闻自动生成失败：{}", e.getMessage());
        } finally {
            if (!preset) {
                AuditContext.clear();
            }
        }
    }

    /** 清理超过保留天数的纪闻（默认保留当天） */
    private void cleanupExpiredNews() {
        int keepDays = Math.max(1, intConfig("cleanup_sandbox_news_days", 1));
        newsMapper.delete(new LambdaQueryWrapper<SandboxNews>()
                .lt(SandboxNews::getNewsDate, LocalDate.now().minusDays(keepDays - 1L)));
    }

    /** 生成事件的系统提示词 */
    private String buildNewsSystemPrompt(int count) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位奇幻世界的编年史官。请为今天的世界写出 ").append(count)
                .append(" 条「旅人纪闻」，内容是这个世界里自然发生的大事，写成适合快报的一句话。\n")
                .append("你必须严格只输出一个 JSON 对象，不要输出解释、前后缀或 Markdown 代码块标记。JSON 结构如下：\n")
                .append("{\"events\":[{\"title\":\"一句话事件（20~40 字）\",\"content\":\"补充说明（40~120 字）\",")
                .append("\"location\":\"发生地点，必须从【地图地点】里选一个\",\"level\":1}]}\n")
                .append("要求：\n")
                .append("1. level 表示重要度：1 普通 / 2 重要 / 3 重大；\n")
                .append("2. 事件之间要各不相同、有画面感、符合世界观，可以是节庆、天象、商队、遗迹异动、灾害、")
                .append("物价波动、贵族动向、怪物出没等；\n")
                .append("3. location 只能从给定地点里选；\n")
                .append("4. 这些事件是「世界本身发生的事」，**不要围绕某个具体角色的私人行为来写**，")
                .append("也不要写成角色日记；\n")
                .append("5. 不要使用真实世界的国家、品牌、人物或事件；\n")
                .append("6. 【最近的世界动向】只作为氛围参考，最多只让其中一条与它有一点关联，其余请完全独立创作。");
        return sb.toString();
    }

    /** 生成事件的用户提示词：世界观 + 地图地点 + 最近动向 + 已有纪闻 + 附加要求 */
    private String buildNewsUserPrompt(SandboxWorld world, List<SandboxLocation> locations,
                                       List<String> recentMoves, List<String> existing) {
        StringBuilder sb = new StringBuilder();
        sb.append("【世界观】\n").append(blankToDefault(world.getWorldPrompt(), DEFAULT_WORLD_PROMPT)).append("\n\n");
        sb.append("【地图地点】");
        if (locations.isEmpty()) {
            sb.append("（还没有地点，可以自行设定一个合理的地点名）");
        } else {
            List<String> names = new ArrayList<>();
            for (SandboxLocation location : locations) {
                names.add(location.getName());
            }
            sb.append(String.join("、", names));
        }
        sb.append("\n\n【最近的世界动向】");
        if (recentMoves.isEmpty()) {
            sb.append("（暂无）");
        } else {
            sb.append("\n");
            for (String move : recentMoves) {
                sb.append("- ").append(move).append("\n");
            }
        }
        sb.append("\n【今天已经有的纪闻】");
        if (existing.isEmpty()) {
            sb.append("（暂无，请全部新写）");
        } else {
            sb.append("\n");
            for (String title : existing) {
                sb.append("- ").append(title).append("\n");
            }
            sb.append("（不要与上面重复）");
        }
        String extra = configService.getConfigValue("sandbox_news_prompt_extra", "");
        if (extra != null && !extra.trim().isEmpty()) {
            sb.append("\n\n【附加要求】\n").append(extra.trim());
        }
        return sb.toString();
    }

    /** 应用 AI 返回的物品变化，返回给前台展示的文字，例如「获得 干粮 +1、用掉 面包 -1」 */
    private String applyItemChanges(SandboxCharacter character, Object itemObj) {
        if (!(itemObj instanceof JSONObject)) {
            return null;
        }
        JSONObject obj = (JSONObject) itemObj;
        if (obj.isEmpty()) {
            return null;
        }
        List<String> changes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        List<SandboxItem> current = items(character.getId());
        for (String key : obj.keySet()) {
            String name = key == null ? "" : key.trim();
            if (name.isEmpty() || name.length() > 60) {
                continue;
            }
            int delta = Convert.toInt(obj.get(key), 0);
            if (delta == 0) {
                continue;
            }
            delta = Math.max(-ITEM_STEP_MAX, Math.min(ITEM_STEP_MAX, delta));
            SandboxItem item = null;
            for (SandboxItem it : current) {
                if (it.getName() != null && it.getName().equals(name)) {
                    item = it;
                    break;
                }
            }
            if (item == null) {
                // 没有的东西不能减少；背包种类也做上限，避免无限膨胀
                if (delta < 0 || current.size() >= ITEM_KIND_MAX) {
                    continue;
                }
                SandboxItem created = new SandboxItem();
                created.setWorldId(character.getWorldId());
                created.setCharacterId(character.getId());
                created.setName(name);
                created.setQuantity(delta);
                // AI 新得到的物品按名字猜一个初始品质，管理员可在后台调整
                created.setRarity(inferRarity(name));
                created.setCreateTime(now);
                created.setUpdateTime(now);
                itemMapper.insert(created);
                current.add(created);
                changes.add("获得 " + name + " +" + delta);
                continue;
            }
            int quantity = (item.getQuantity() == null ? 1 : item.getQuantity()) + delta;
            if (quantity <= 0) {
                itemMapper.deleteById(item.getId());
                current.remove(item);
                changes.add("用掉 " + name + " " + delta);
            } else {
                itemMapper.update(null, new LambdaUpdateWrapper<SandboxItem>()
                        .eq(SandboxItem::getId, item.getId())
                        .set(SandboxItem::getQuantity, quantity)
                        .set(SandboxItem::getUpdateTime, now));
                item.setQuantity(quantity);
                changes.add((delta > 0 ? "获得 " : "用掉 ") + name + " " + (delta > 0 ? "+" : "") + delta);
            }
        }
        return changes.isEmpty() ? null : truncate(String.join("、", changes), 290);
    }

    /** 按物品名猜一个初始品质：1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说 */
    private int inferRarity(String name) {
        if (name == null) {
            return 1;
        }
        if (containsAny(name, "神器", "圣物", "传说", "远古", "龙", "神之", "贤者之石")) {
            return 5;
        }
        if (containsAny(name, "秘宝", "史诗", "魔法书", "秘银", "精灵", "王家", "禁书", "圣", "魔导")) {
            return 4;
        }
        if (containsAny(name, "戒指", "宝石", "水晶", "护符", "卷轴", "法杖", "魔杖", "古地图", "秘药",
                "星辉", "符文", "项链", "秘钥")) {
            return 3;
        }
        if (containsAny(name, "药水", "药剂", "草药", "干肉", "匕首", "短剑", "长剑", "斗篷", "护腕",
                "皮革", "萤石", "铁", "钥匙")) {
            return 2;
        }
        return 1;
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    // ============================== 每日记忆 ==============================

    @Override
    public PageResult<SandboxMemory> memoryPage(Long characterId, long page, long size) {
        LambdaQueryWrapper<SandboxMemory> wrapper = new LambdaQueryWrapper<SandboxMemory>()
                .eq(characterId != null, SandboxMemory::getCharacterId, characterId)
                .orderByDesc(SandboxMemory::getMemoryDate)
                .orderByAsc(SandboxMemory::getCharacterId);
        IPage<SandboxMemory> result = memoryMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public void saveMemory(SandboxMemory memory) {
        if (memory.getCharacterId() == null) {
            throw new BusinessException("请选择角色");
        }
        if (memory.getMemoryDate() == null) {
            throw new BusinessException("请选择记忆日期");
        }
        SandboxMemory exists = memoryMapper.selectOne(new LambdaQueryWrapper<SandboxMemory>()
                .eq(SandboxMemory::getCharacterId, memory.getCharacterId())
                .eq(SandboxMemory::getMemoryDate, memory.getMemoryDate())
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (exists == null) {
            if (memory.getWorldId() == null) {
                memory.setWorldId(worldId());
            }
            memory.setId(null);
            if (memory.getActCount() == null) {
                memory.setActCount(0);
            }
            if (memory.getFromAi() == null) {
                memory.setFromAi(0);
            }
            memory.setCreateTime(now);
            memory.setUpdateTime(now);
            memoryMapper.insert(memory);
        } else {
            memoryMapper.update(null, new LambdaUpdateWrapper<SandboxMemory>()
                    .eq(SandboxMemory::getId, exists.getId())
                    .set(SandboxMemory::getSummary, memory.getSummary())
                    .set(SandboxMemory::getUpdateTime, now));
        }
    }

    @Override
    public void deleteMemory(Long id) {
        memoryMapper.deleteById(id);
    }

    @Override
    public void summarizeDaily() {
        summarizeFor(LocalDate.now(), true);
    }

    @Override
    public void summarizeOn(String date) {
        LocalDate target;
        try {
            target = date == null || date.trim().isEmpty() ? LocalDate.now() : LocalDate.parse(date.trim());
        } catch (Exception e) {
            throw new BusinessException("日期格式应为 yyyy-MM-dd");
        }
        summarizeFor(target, false);
    }

    /** 生成指定日期的记忆；schedule=true 表示由定时任务触发（审计日志区分来源） */
    private void summarizeFor(LocalDate date, boolean schedule) {
        if (!"1".equals(configService.getConfigValue("sandbox_memory_enabled", "1"))) {
            return;
        }
        boolean preset = AuditContext.isSet();
        if (!preset) {
            if (schedule) {
                AuditContext.schedule("沙盒·记忆总结");
            } else {
                AuditContext.manual("沙盒·补生成记忆");
            }
        }
        try {
            for (SandboxCharacter character : characterMapper.selectList(null)) {
                try {
                    summarize(character, date);
                } catch (Exception e) {
                    log.warn("沙盒角色「{}」记忆总结失败：{}", character.getName(), e.getMessage());
                }
            }
        } finally {
            if (!preset) {
                AuditContext.clear();
            }
        }
    }

    /** 把某个角色某一天的行动总结成一条记忆（当天没有行动则跳过） */
    private void summarize(SandboxCharacter character, LocalDate date) {
        List<SandboxAct> acts = actMapper.selectList(new LambdaQueryWrapper<SandboxAct>()
                .eq(SandboxAct::getCharacterId, character.getId())
                .ge(SandboxAct::getCreateTime, date.atStartOfDay())
                .lt(SandboxAct::getCreateTime, date.plusDays(1).atStartOfDay())
                .orderByAsc(SandboxAct::getCreateTime));
        if (acts.isEmpty()) {
            return;
        }
        String summary = null;
        boolean fromAi = true;
        try {
            summary = aiSummary(character, date, acts);
        } catch (Exception e) {
            log.warn("沙盒角色「{}」{} 的 AI 记忆总结失败，改用兜底拼接：{}",
                    character.getName(), date, e.getMessage());
        }
        if (summary == null || summary.trim().isEmpty()) {
            summary = fallbackSummary(acts);
            fromAi = false;
        }
        SandboxMemory exist = memoryMapper.selectOne(new LambdaQueryWrapper<SandboxMemory>()
                .eq(SandboxMemory::getCharacterId, character.getId())
                .eq(SandboxMemory::getMemoryDate, date)
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (exist == null) {
            SandboxMemory memory = new SandboxMemory();
            memory.setWorldId(character.getWorldId());
            memory.setCharacterId(character.getId());
            memory.setMemoryDate(date);
            memory.setSummary(truncate(summary, 2000));
            memory.setActCount(acts.size());
            memory.setFromAi(fromAi ? 1 : 0);
            memory.setCreateTime(now);
            memory.setUpdateTime(now);
            memoryMapper.insert(memory);
        } else {
            memoryMapper.update(null, new LambdaUpdateWrapper<SandboxMemory>()
                    .eq(SandboxMemory::getId, exist.getId())
                    .set(SandboxMemory::getSummary, truncate(summary, 2000))
                    .set(SandboxMemory::getActCount, acts.size())
                    .set(SandboxMemory::getFromAi, fromAi ? 1 : 0)
                    .set(SandboxMemory::getUpdateTime, now));
        }
        // 可选：总结完成后删除当天日志（默认关闭，前台时间线会看不到当天内容）
        if ("1".equals(configService.getConfigValue("sandbox_memory_delete_acts", "0"))) {
            actMapper.delete(new LambdaQueryWrapper<SandboxAct>()
                    .eq(SandboxAct::getCharacterId, character.getId())
                    .ge(SandboxAct::getCreateTime, date.atStartOfDay())
                    .lt(SandboxAct::getCreateTime, date.plusDays(1).atStartOfDay()));
        }
    }

    /** 让 AI 把一天的流水整理成一段第一人称的长期记忆 */
    private String aiSummary(SandboxCharacter character, LocalDate date, List<SandboxAct> acts) {
        StringBuilder sys = new StringBuilder();
        sys.append("你是一位擅长第一人称叙事的小说作者。请把这名角色一天的经历，写成一段**像回忆一样的连贯叙述**。\n")
                .append("写作要求：\n")
                .append("1. 用「我」的口吻，250~450 字，写成 2~4 个自然段；像在灯下回想今天，而不是汇报行程；\n")
                .append("2. 有起承转合与情绪起伏：今天从什么事开始、中途发生了什么转折、后来心情怎么变化、")
                .append("结束时带着怎样的状态入睡（或结束这一天）；\n")
                .append("3. 句子要有细节和画面感——光线的冷热、食物的味道、斗篷湿透的重量、失败的窘迫、")
                .append("遇到的人说话的语气；把这些细节串成故事，而不是一条条列举；\n")
                .append("4. **严禁流水账**：不要使用「今天：」「；」这种罗列格式，不要逐条复述每个动作，")
                .append("同一地点、同一件事的过程要合并提炼（例如把「买烤肉、吃烤肉、被香气吸引」写成一句话带情绪的场景）；\n")
                .append("5. 保留值得记住的东西：去过的地方（写到二级地点，如「自由城邦联盟 · 东侧集市」）、")
                .append("这一阵常待的小地方、遇到的人与相处感受、得到或失去的物品、金钱收支、身体与心情的变化、还没做完的事；\n")
                .append("6. 不要编造没有发生过的事，也不要写角色不可能知道的信息；\n")
                .append("7. 只输出这段回忆本身，不要标题、日期、分点符号、解释或 JSON。");
        StringBuilder user = new StringBuilder();
        user.append("角色：").append(character.getName());
        if (notBlank(character.getTitle())) {
            user.append("（").append(character.getTitle()).append("）");
        }
        user.append("\n日期：").append(date).append("\n【当天的行动】\n");
        for (SandboxAct act : acts) {
            user.append("- ").append(act.getCreateTime() == null ? "" : act.getCreateTime().format(TIME_FORMATTER))
                    .append(" 在").append(blankToDefault(placeText(act.getLocationName(), act.getSubLocation()), "某处")).append("：")
                    .append(blankToDefault(act.getActions(), blankToDefault(act.getSummary(), "")));
            if (notBlank(act.getInnerVoice())) {
                user.append("（心声：").append(act.getInnerVoice()).append("）");
            }
            if (act.getCoinChange() != null && act.getCoinChange() != 0) {
                user.append(" 金币").append(act.getCoinChange() > 0 ? "+" : "").append(act.getCoinChange());
            }
            if (notBlank(act.getItemChange())) {
                user.append(" 物品：").append(act.getItemChange());
            }
            if (notBlank(act.getFavorChange())) {
                user.append(" 好感：").append(act.getFavorChange());
            }
            user.append("\n");
        }
        // 记忆总结属于系统级调用：如果实际使用的是系统服务商（与角色绑定的不同），
        // 角色自己的模型名在系统服务商上通常不存在，这里改用配置的系统模型
        AiProvider provider = AuditContext.isSchedule()
                ? aiProviderService.resolveSystemProvider(character.getProviderId())
                : aiProviderService.resolveManualProvider(character.getProviderId());
        String model = character.getModel();
        if (provider != null && !provider.getId().equals(character.getProviderId())) {
            String systemModel = configService.getConfigValue("sandbox_system_model", "");
            if (notBlank(systemModel)) {
                model = systemModel.trim();
            }
        }
        String content = aiProviderService.chat(provider, model, sys.toString(), user.toString(), 0.6);
        return content == null ? null : content.trim();
    }

    /** AI 总结失败时的兜底：把当天每条行动的概括拼起来 */
    private String fallbackSummary(List<SandboxAct> acts) {
        List<String> parts = new ArrayList<>();
        for (SandboxAct act : acts) {
            String text = blankToDefault(act.getSummary(), blankToDefault(act.getActions(), ""));
            if (notBlank(text)) {
                parts.add(text.replace("\n", " "));
            }
        }
        if (parts.isEmpty()) {
            return "今天什么也没做，只是发了很久的呆，直到天色暗下来。";
        }
        // 兜底也要读得顺：不写成「今天：A；B；C」，而是连成几段自然的叙述
        StringBuilder sb = new StringBuilder();
        sb.append("（AI 总结暂不可用，以下是根据当天行动整理的记录）\n");
        int index = 0;
        for (String part : parts) {
            if (index == 0) {
                sb.append("这一天从").append(part).append("开始。");
            } else if (index % 4 == 0) {
                sb.append("\n后来，").append(part).append("。");
            } else {
                sb.append("接着").append(part).append("。");
            }
            index++;
        }
        return truncate(sb.toString(), 2000);
    }

    /** 最近的记忆（按日期倒序取最近 n 天） */
    private List<SandboxMemory> recentMemories(Long characterId, int days) {
        if (characterId == null || days <= 0) {
            return new ArrayList<>();
        }
        int limit = Math.max(1, days);
        return memoryMapper.selectList(new LambdaQueryWrapper<SandboxMemory>()
                .eq(SandboxMemory::getCharacterId, characterId)
                .ge(SandboxMemory::getMemoryDate, LocalDate.now().minusDays(limit - 1L))
                .orderByDesc(SandboxMemory::getMemoryDate)
                .last("limit " + limit));
    }

    /**
     * 可选的自查（审查）：把 AI 的输出再交给一次 AI 只做 JSON 校验与数值修正，不改写剧情。
     * 需要在后台把 sandbox_verify_enabled 设为 1 才生效（会翻倍消耗 token）。
     * 任何失败都会退回原始输出，不影响正常流程。
     *
     * 注意：自查模型的提示词必须列出**全部**字段，并强调「原样保留所有字段」。
     * 早期版本只列了 10 个字段，弱模型（如 gemini-flash 系列）会照着清单重建 JSON，
     * 把 sub_location、items_change、news_refs、next_after_minutes/next_after_reason 全删掉。
     * 除了提示词，这里还加了一层服务端兜底：只要自查结果丢了原 JSON 的字段，就直接丢弃它。
     */
    private String verifyOutput(SandboxCharacter character, String raw, List<SandboxLocation> locations,
                                List<SandboxCharacter> companions) {
        if (raw == null || raw.trim().isEmpty()) {
            return raw;
        }
        try {
            StringBuilder sys = new StringBuilder();
            sys.append("你是一个 JSON 校验器，只做校验与数值修正，不改写故事内容，也不新增剧情。\n")
                    .append("输入是某个角色扮演输出的 JSON。请**原样保留输入中的每一个字段**（键名一个都不能少），")
                    .append("只修正其中的数值和名字，然后输出完整的 JSON：\n")
                    .append("- 不允许删除、省略、改名字段，特别是 sub_location、items_change、news_refs、")
                    .append("next_after_minutes、next_after_reason 这些字段必须原样保留；\n")
                    .append("- 不允许新增输入里没有的字段；\n")
                    .append("- 不允许改写或缩写 actions、inner_voice、summary 的文字内容；\n")
                    .append("- 即使某个字段看起来多余，也要照抄，不要自作主张删掉。\n")
                    .append("校验规则：\n")
                    .append("1. location 必须是【可用地点】里的名字，x/y 为 0~100 的整数；\n")
                    .append("2. actions 至少保留 1 条，条目内容不要改动；\n")
                    .append("3. status 里体力、魔力、饥饿度必须是 0~100 的整数，心情等其它键保留原文；\n")
                    .append("4. coins_change 必须与 actions 描述的收支一致，没有花钱或赚钱就改成 0；\n")
                    .append("5. companions 只能填【可用角色名】里的名字；favor_changes 的键也只能是这些名字，")
                    .append("并且必须与 companions 和 actions 的描述相符，幅度限制在 -10~+10，")
                    .append("还要保证加上当前好感度后仍在 -100~100 之内；\n")
                    .append("6. items_change 的键是物品名、值是 ±9 以内的整数，没有变化就保留空对象；\n")
                    .append("7. next_after_minutes 必须是大于 0 的整数，next_after_reason 必须是 2~6 个字，")
                    .append("这两个字段绝对不能删除；\n")
                    .append("8. 不要编造任何角色名或地点名。\n")
                    .append("只输出这一段完整的 JSON，不要输出解释文字，也不要使用 Markdown 代码块标记。\n");

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
            if (content == null || content.trim().isEmpty()) {
                return raw;
            }
            // 服务端兜底：自查只该改数值/名字。一旦它把原 JSON 的字段删掉了，就整份丢弃，
            // 改用原始输出（不指望模型自觉，这一步才是真正的保险）
            return keepVerifiedIfComplete(raw, content);
        } catch (Exception e) {
            log.warn("沙盒输出自查失败，改用原始输出：{}", e.getMessage());
            return raw;
        }
    }

    /**
     * 比对自查前后的字段，决定是否采用自查结果。
     * 自查结果丢失了原 JSON 的任何字段时返回原始输出，否则返回自查结果。
     */
    private String keepVerifiedIfComplete(String raw, String verified) {
        JSONObject original = parseJson(raw);
        JSONObject checked = parseJson(verified);
        if (original == null || checked == null) {
            log.warn("沙盒输出自查结果无法解析，改用原始输出");
            return raw;
        }
        List<String> lost = new ArrayList<>();
        for (String key : original.keySet()) {
            if (!checked.containsKey(key)) {
                lost.add(key);
            }
        }
        if (!lost.isEmpty()) {
            log.warn("沙盒输出自查丢了字段（{}），改用原始输出", String.join("、", lost));
            return raw;
        }
        return verified;
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

    // ============================== 地点区域工具 ==============================

    /** 地点区域左上角 X */
    private int areaX(SandboxLocation location) {
        return location.getX() == null ? 50 : location.getX();
    }

    /** 地点区域左上角 Y */
    private int areaY(SandboxLocation location) {
        return location.getY() == null ? 50 : location.getY();
    }

    /** 地点区域宽度，0 表示单点 */
    private int areaWidth(SandboxLocation location) {
        return location.getWidth() == null ? 0 : location.getWidth();
    }

    /** 地点区域高度，0 表示单点 */
    private int areaHeight(SandboxLocation location) {
        return location.getHeight() == null ? 0 : location.getHeight();
    }

    /** 地点中心 X */
    private int centerX(SandboxLocation location) {
        return areaX(location) + Math.max(0, areaWidth(location) / 2);
    }

    /** 地点中心 Y */
    private int centerY(SandboxLocation location) {
        return areaY(location) + Math.max(0, areaHeight(location) / 2);
    }

    /** 坐标是否落在地点区域内（单点地点允许 2% 的容差） */
    private boolean inLocationArea(SandboxLocation location, int x, int y) {
        int width = areaWidth(location);
        int height = areaHeight(location);
        if (width <= 0 || height <= 0) {
            return Math.abs(areaX(location) - x) <= 2 && Math.abs(areaY(location) - y) <= 2;
        }
        return x >= areaX(location) && x <= areaX(location) + width
                && y >= areaY(location) && y <= areaY(location) + height;
    }

    /** 坐标落在哪个地点区域内，没有命中返回 null */
    private SandboxLocation locationAtPoint(List<SandboxLocation> locations, int x, int y) {
        for (SandboxLocation location : locations) {
            if (inLocationArea(location, x, y)) {
                return location;
            }
        }
        return null;
    }

    /** 把坐标夹到地点区域内（单点地点不处理） */
    private int[] clampToArea(SandboxLocation location, int x, int y) {
        int width = areaWidth(location);
        int height = areaHeight(location);
        if (width <= 0 || height <= 0) {
            return new int[]{x, y};
        }
        int left = areaX(location);
        int top = areaY(location);
        return new int[]{
                Math.max(left, Math.min(left + width, x)),
                Math.max(top, Math.min(top + height, y))
        };
    }

    /**
     * 计算下一次行动时间：优先采用 AI 自己给出的间隔（例如睡觉就是几小时），
     * 没有给或功能关闭时，回退到角色配置的随机区间；两种情况都会避开夜间静默。
     */
    private LocalDateTime resolveNextRunTime(SandboxCharacter character, LocalDateTime from, Integer aiMinutes) {
        boolean enabled = "1".equals(configService.getConfigValue("sandbox_ai_interval_enabled", "1"));
        if (enabled && aiMinutes != null && aiMinutes > 0) {
            int min = character.getAiIntervalMin() == null
                    ? intConfig("sandbox_ai_interval_min", 15) : character.getAiIntervalMin();
            int max = character.getAiIntervalMax() == null
                    ? intConfig("sandbox_ai_interval_max", 720) : character.getAiIntervalMax();
            if (min < 1) {
                min = 1;
            }
            if (max < min) {
                max = min;
            }
            int minutes = Math.max(min, Math.min(max, aiMinutes));
            return shiftOutOfNight(from.plusMinutes(minutes));
        }
        return nextRunTime(character, from);
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

    /** 地点显示：一级地点 · 二级地点；两级都没有时返回 null */
    private String placeText(String locationName, String subLocation) {
        if (!notBlank(locationName) && !notBlank(subLocation)) {
            return null;
        }
        String main = blankToDefault(locationName, "某处");
        return notBlank(subLocation) ? main + " · " + subLocation.trim() : main;
    }
}
