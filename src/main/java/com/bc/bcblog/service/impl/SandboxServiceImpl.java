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
import com.bc.bcblog.common.SandboxBusyException;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.SandboxGeo;
import com.bc.bcblog.common.SandboxBackoff;
import com.bc.bcblog.common.SandboxOutputRepair;
import com.bc.bcblog.common.SandboxShopCoin;
import com.bc.bcblog.common.SandboxSpendLimit;
import com.bc.bcblog.common.SandboxItemName;
import com.bc.bcblog.common.SandboxOutputIssues;
import com.bc.bcblog.common.SandboxReplyParser;
import com.bc.bcblog.dto.ChatMessage;
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
import com.bc.bcblog.mapper.SandboxShopItemMapper;
import com.bc.bcblog.mapper.SandboxShopOrderMapper;
import com.bc.bcblog.mapper.SandboxGiftMapper;
import com.bc.bcblog.entity.SandboxShopItem;
import com.bc.bcblog.entity.SandboxShopOrder;
import com.bc.bcblog.entity.SandboxGift;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.zip.ZipInputStream;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

    /** 上传目录（导出存档时要读取图片文件） */
    @Value("${bcblog.upload-dir:./uploads}")
    private String uploadDir;
    /** 存档里的时间格式（带秒，便于导入时精确还原） */
    private static final DateTimeFormatter SAVE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
    /**
     * 默认交通方式与速度（km/h）。格式「名称:速度」逗号分隔，后台可改。
     * 用途有两个：把速度表念给 AI（让它按距离选交通方式）、服务端按最快方式算赶路时间下限。
     */
    private static final String DEFAULT_TRAVEL_SPEEDS = "步行:4,骑乘:20,车船:12,飞行:60";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 需要统一成 0~100 数值的标准状态项 */
    private static final List<String> STANDARD_STATUS_KEYS = Arrays.asList("体力", "魔力", "饥饿度", "饱食度");
    /** 好感度上下限 */
    private static final int FAVOR_MIN = -100;
    private static final int FAVOR_MAX = 100;
    /** 单次行动好感度变化上限，避免 AI 一次给出夸张数值 */
    private static final int FAVOR_STEP_MAX = 20;
    /** 角色战斗力默认值与下限 */
    private static final int COMBAT_POWER_DEFAULT = 10;
    private static final int COMBAT_POWER_MIN = 1;
    /** 单次行动的战斗力变化上限，避免 AI 一次给太夸张的数值 */
    private static final int COMBAT_STEP_MAX = 5;
    /**
     * 一次行动「必须有」的字段：缺任何一个都会触发补全（有原文）或重跑（没原文）。
     * 省略后不影响正确性的字段不列在这里（companions 空数组、coins_change 缺省算 0、
     * sub_location 沿用上一次、x/y 沿用上一次位置），这样可以少花调用次数。
     */
    private static final String[] REQUIRED_FIELDS = {
            "next_after_minutes", "next_after_reason", "location", "actions", "inner_voice", "status", "summary"
    };

    private final SandboxWorldMapper worldMapper;
    private final SandboxShopItemMapper shopItemMapper;
    private final SandboxShopOrderMapper shopOrderMapper;
    private final SandboxGiftMapper giftMapper;
    private final com.bc.bcblog.mapper.AiProviderMapper aiProviderMapper;
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
    public SandboxWorld world(Long worldId) {
        LambdaQueryWrapper<SandboxWorld> wrapper = new LambdaQueryWrapper<>();
        if (worldId != null) {
            wrapper.eq(SandboxWorld::getId, worldId);
        }
        SandboxWorld w = worldMapper.selectOne(wrapper
                .orderByAsc(SandboxWorld::getId)
                .last("limit 1"));
        if (w == null) {
            // 还没有创建过世界时，返回一个空壳给后台表单使用
            w = new SandboxWorld();
            w.setName("");
            w.setEnabled(1);
            w.setPortalVisible(1);
        }
        return w;
    }

    @Override
    public List<SandboxWorld> worlds() {
        return worldMapper.selectList(new LambdaQueryWrapper<SandboxWorld>()
                .orderByAsc(SandboxWorld::getId));
    }

    @Override
    public List<SandboxWorld> visibleWorlds() {
        // 前台下拉：只要管理员设成「可见」就列出来，包括已经停止运行的（游客可以只看历史）
        return worldMapper.selectList(new LambdaQueryWrapper<SandboxWorld>()
                .eq(SandboxWorld::getPortalVisible, 1)
                .orderByAsc(SandboxWorld::getId));
    }

    @Override
    public void saveWorld(SandboxWorld world) {
        if (world.getName() == null) {
            world.setName("");
        }
        if (world.getEnabled() == null) {
            world.setEnabled(1);
        }
        if (world.getPortalVisible() == null) {
            world.setPortalVisible(1);
        }
        if (world.getId() == null) {
            worldMapper.insert(world);
        } else {
            worldMapper.updateById(world);
        }
    }

    @Override
    public void setWorldEnabled(Long worldId, Integer enabled) {
        requireWorld(worldId);
        worldMapper.update(null, new LambdaUpdateWrapper<SandboxWorld>()
                .eq(SandboxWorld::getId, worldId)
                .set(SandboxWorld::getEnabled, enabled != null && enabled == 1 ? 1 : 0));
    }

    @Override
    public void setWorldVisible(Long worldId, Integer visible) {
        requireWorld(worldId);
        worldMapper.update(null, new LambdaUpdateWrapper<SandboxWorld>()
                .eq(SandboxWorld::getId, worldId)
                .set(SandboxWorld::getPortalVisible, visible != null && visible == 1 ? 1 : 0));
    }

    /**
     * 删除世界：把它名下的角色、地点、行动、记忆、背包、好感度、纪闻、旅人低语、金币流水一并删掉。
     * 这些数据都带 world_id（低语与金币流水是本次迁移补上的），所以按世界删除是干净的。
     */
    @Override
    public void deleteWorld(Long worldId) {
        requireWorld(worldId);
        for (SandboxCharacter character : characters(worldId)) {
            actMapper.delete(new LambdaQueryWrapper<SandboxAct>().eq(SandboxAct::getCharacterId, character.getId()));
            memoryMapper.delete(new LambdaQueryWrapper<SandboxMemory>().eq(SandboxMemory::getCharacterId, character.getId()));
            itemMapper.delete(new LambdaQueryWrapper<SandboxItem>().eq(SandboxItem::getCharacterId, character.getId()));
            relationMapper.delete(new LambdaQueryWrapper<SandboxRelation>()
                    .eq(SandboxRelation::getCharacterId, character.getId())
                    .or().eq(SandboxRelation::getTargetId, character.getId()));
            interactionMapper.delete(new LambdaQueryWrapper<SandboxInteraction>()
                    .eq(SandboxInteraction::getCharacterId, character.getId()));
            coinLogMapper.delete(new LambdaQueryWrapper<SandboxCoinLog>()
                    .eq(SandboxCoinLog::getCharacterId, character.getId()));
        }
        // 兜底：按 world_id 再清一遍（防止有孤儿数据）
        interactionMapper.delete(new LambdaQueryWrapper<SandboxInteraction>().eq(SandboxInteraction::getWorldId, worldId));
        coinLogMapper.delete(new LambdaQueryWrapper<SandboxCoinLog>().eq(SandboxCoinLog::getWorldId, worldId));
        actMapper.delete(new LambdaQueryWrapper<SandboxAct>().eq(SandboxAct::getWorldId, worldId));
        memoryMapper.delete(new LambdaQueryWrapper<SandboxMemory>().eq(SandboxMemory::getWorldId, worldId));
        itemMapper.delete(new LambdaQueryWrapper<SandboxItem>().eq(SandboxItem::getWorldId, worldId));
        relationMapper.delete(new LambdaQueryWrapper<SandboxRelation>().eq(SandboxRelation::getWorldId, worldId));
        newsMapper.delete(new LambdaQueryWrapper<SandboxNews>().eq(SandboxNews::getWorldId, worldId));
        characterMapper.delete(new LambdaQueryWrapper<SandboxCharacter>().eq(SandboxCharacter::getWorldId, worldId));
        locationMapper.delete(new LambdaQueryWrapper<SandboxLocation>().eq(SandboxLocation::getWorldId, worldId));
        worldMapper.deleteById(worldId);
    }

    /** 世界必须存在，避免对不存在的世界做操作 */
    private void requireWorld(Long worldId) {
        if (worldId == null || worldMapper.selectById(worldId) == null) {
            throw new BusinessException("世界不存在或已被删除");
        }
    }

    /** 这个世界是否在运行（enabled = 1）；停跑的世界不参与定时行动与定时记忆总结 */
    private boolean isWorldRunning(Long worldId) {
        if (worldId == null) {
            return true;
        }
        SandboxWorld world = worldMapper.selectById(worldId);
        return world != null && world.getEnabled() != null && world.getEnabled() == 1;
    }

    @Override
    public List<SandboxLocation> locations(Long worldId) {
        return locationMapper.selectList(new LambdaQueryWrapper<SandboxLocation>()
                .eq(worldId != null, SandboxLocation::getWorldId, worldId)
                .orderByAsc(SandboxLocation::getSortOrder)
                .orderByAsc(SandboxLocation::getId));
    }

    @Override
    public SandboxLocation saveLocation(SandboxLocation location) {
        if (location.getName() == null || location.getName().trim().isEmpty()) {
            throw new BusinessException("地点名称不能为空");
        }
        location.setName(location.getName().trim());
        // 多边形区域（后台套索 / 魔法棒描边）：规范化 + 自交校验，并用外接矩形同步 x/y/width/height，
        // 这样提示词、后台列表等沿用矩形字段的地方依然能拿到可用信息
        boolean clearPolygon = location.getPolygon() != null && location.getPolygon().trim().isEmpty();
        if (location.getPolygon() != null && !clearPolygon) {
            List<double[]> raw = SandboxGeo.parse(location.getPolygon());
            List<double[]> polygon = SandboxGeo.normalize(raw);
            if (polygon.size() < 3) {
                throw new BusinessException("多边形区域至少需要 3 个有效顶点（当前去掉重复与共线后不足 3 个）");
            }
            if (SandboxGeo.selfIntersects(polygon)) {
                throw new BusinessException("区域边界不能自交（不能画成 8 字形），请重新描边");
            }
            location.setPolygon(SandboxGeo.toJson(polygon));
            double[] box = SandboxGeo.bbox(polygon);
            int left = (int) Math.round(box[0]);
            int top = (int) Math.round(box[1]);
            location.setX(clamp(left));
            location.setY(clamp(top));
            location.setWidth(Math.max(0, Math.min(100 - location.getX(), (int) Math.round(box[2]) - location.getX())));
            location.setHeight(Math.max(0, Math.min(100 - location.getY(), (int) Math.round(box[3]) - location.getY())));
        } else if (clearPolygon) {
            location.setPolygon(null);
        }
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
                location.setWorldId(worldId(location.getWorldId()));
            }
        }
        // 区域不能超出地图边界
        if (location.getX() != null && location.getWidth() != null && location.getWidth() > 0) {
            location.setX(Math.min(location.getX(), 100 - location.getWidth()));
        }
        if (location.getY() != null && location.getHeight() != null && location.getHeight() > 0) {
            location.setY(Math.min(location.getY(), 100 - location.getHeight()));
        }
        // 区域之间不允许交叉重叠（允许完全包含，用于「国家里放城市」这种嵌套）
        validateOverlap(location);
        if (location.getId() == null) {
            locationMapper.insert(location);
        } else {
            locationMapper.updateById(location);
            // updateById 会忽略 null，所以要单独把「清空多边形」写进去
            if (clearPolygon) {
                locationMapper.update(null, new LambdaUpdateWrapper<SandboxLocation>()
                        .eq(SandboxLocation::getId, location.getId())
                        .set(SandboxLocation::getPolygon, null));
            }
        }
        return location;
    }

    /**
     * 校验区域不与其它地点交叉重叠。
     *
     * 规则（与后台实时校验、前台渲染保持一致）：
     *   1. 相离：放行；
     *   2. 交叉重叠：重叠面积小于地图面积 1% 视为手绘压边误差，放行；否则拦下并提示与谁重叠了多少；
     *   3. 完全包含：放行（允许「圣云教国」里放「圣光塔」这种嵌套，命中时取面积小的那个）。
     */
    private void validateOverlap(SandboxLocation target) {
        List<double[]> polygon = polygonOf(target);
        if (polygon == null) {
            // 单点地点：只要不落在别的区域里即可
            if (target.getX() == null || target.getY() == null) {
                return;
            }
            for (SandboxLocation other : locations(target.getWorldId())) {
                if (isSelf(target, other)) {
                    continue;
                }
                List<double[]> otherPolygon = polygonOf(other);
                if (otherPolygon != null && SandboxGeo.contains(otherPolygon, target.getX(), target.getY())) {
                    throw new BusinessException("这个位置已经在「" + other.getName() + "」的范围里了，请换个位置");
                }
            }
            return;
        }
        for (SandboxLocation other : locations(target.getWorldId())) {
            if (isSelf(target, other)) {
                continue;
            }
            List<double[]> otherPolygon = polygonOf(other);
            if (otherPolygon == null) {
                if (other.getX() != null && other.getY() != null
                        && SandboxGeo.contains(polygon, other.getX(), other.getY())) {
                    throw new BusinessException("地点「" + other.getName() + "」就在你画的区域里，请先调整它");
                }
                continue;
            }
            if (SandboxGeo.relation(polygon, otherPolygon) != SandboxGeo.REL_CROSS) {
                continue;
            }
            if (SandboxGeo.overlapAllowed(polygon, otherPolygon)) {
                continue;
            }
            throw new BusinessException("与「" + other.getName() + "」重叠了它面积的 "
                    + Math.round(SandboxGeo.overlapRatio(polygon, otherPolygon)) + "%，区域之间不能交叉重叠"
                    + "（可以贴着画，或改成包含关系，例如国家里放城市）");
        }
    }

    /** 判断两个地点对象是不是同一条记录 */
    private boolean isSelf(SandboxLocation a, SandboxLocation b) {
        return a.getId() != null && a.getId().equals(b.getId());
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
        vo.setVerifyMode(verifyMode());
        vo.setDraftMode(configService.getConfigValue("sandbox_draft_mode", "on"));
        vo.setThinkStage(configService.getConfigValue("sandbox_think_stage", "on"));
        vo.setStyleExtra(configService.getConfigValue("sandbox_style_extra", ""));
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
        vo.setSystemProviderId(configService.getConfigValue("sandbox_system_provider_id", ""));
        vo.setFailBackoffBaseMinutes(configService.getConfigValue("sandbox_fail_backoff_base_minutes", "15"));
        vo.setFailBackoffMaxMinutes(configService.getConfigValue("sandbox_fail_backoff_max_minutes", "120"));
        vo.setWhisperEnabled(configService.getConfigValue("sandbox_whisper_enabled", "1"));
        // 旅人集市
        vo.setShopTitle(configService.getConfigValue("sandbox_shop_title", "旅人集市"));
        vo.setShopEnabled(configService.getConfigValue("sandbox_shop_enabled", "1"));
        vo.setShopAutoEnabled(configService.getConfigValue("sandbox_shop_auto_enabled", "1"));
        vo.setShopIntervalHours(configService.getConfigValue("sandbox_shop_interval_hours", "24"));
        vo.setShopAutoTime(configService.getConfigValue("sandbox_shop_auto_time", "08:00"));
        vo.setShopPerGenerate(configService.getConfigValue("sandbox_shop_per_generate", "3"));
        vo.setShopProviderId(configService.getConfigValue("sandbox_shop_provider_id", ""));
        vo.setShopModel(configService.getConfigValue("sandbox_shop_model", ""));
        vo.setShopPromptExtra(configService.getConfigValue("sandbox_shop_prompt_extra", ""));
        vo.setShopLimitPerCharacter(configService.getConfigValue("sandbox_shop_limit_per_character", "1"));
        vo.setShopBuyPerDay(configService.getConfigValue("sandbox_shop_buy_per_day", "2"));
        vo.setCoinRate(configService.getConfigValue("sandbox_coin_rate", "1"));
        vo.setKmMapWidth(configService.getConfigValue("sandbox_km_map_width", "200"));
        vo.setTravelSpeeds(configService.getConfigValue("sandbox_travel_speeds", DEFAULT_TRAVEL_SPEEDS));
        vo.setSocialMaxKm(configService.getConfigValue("sandbox_social_max_km", "30"));
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
        writeSetting("sandbox_draft_mode", vo.getDraftMode());
        writeSetting("sandbox_think_stage", vo.getThinkStage());
        writeSetting("sandbox_style_extra", vo.getStyleExtra());
        // 新的三档自查模式；同时把旧开关写成 1/0，保证还有别的读取者时行为一致
        writeSetting("sandbox_verify_mode", vo.getVerifyMode());
        if (notBlank(vo.getVerifyMode())) {
            writeSetting("sandbox_verify_enabled", "off".equalsIgnoreCase(vo.getVerifyMode().trim()) ? "0" : "1");
        }
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
        writeSetting("sandbox_system_provider_id", vo.getSystemProviderId());
        writeSetting("sandbox_fail_backoff_base_minutes", vo.getFailBackoffBaseMinutes());
        writeSetting("sandbox_fail_backoff_max_minutes", vo.getFailBackoffMaxMinutes());
        writeSetting("sandbox_whisper_enabled", vo.getWhisperEnabled());
        writeSetting("sandbox_shop_title", vo.getShopTitle());
        writeSetting("sandbox_shop_enabled", vo.getShopEnabled());
        writeSetting("sandbox_shop_auto_enabled", vo.getShopAutoEnabled());
        writeSetting("sandbox_shop_interval_hours", vo.getShopIntervalHours());
        writeSetting("sandbox_shop_auto_time", vo.getShopAutoTime());
        writeSetting("sandbox_shop_per_generate", vo.getShopPerGenerate());
        writeSetting("sandbox_shop_provider_id", vo.getShopProviderId());
        writeSetting("sandbox_shop_model", vo.getShopModel());
        writeSetting("sandbox_shop_prompt_extra", vo.getShopPromptExtra());
        writeSetting("sandbox_shop_limit_per_character", vo.getShopLimitPerCharacter());
        writeSetting("sandbox_shop_buy_per_day", vo.getShopBuyPerDay());
        writeSetting("sandbox_coin_rate", vo.getCoinRate());
        writeSetting("sandbox_km_map_width", vo.getKmMapWidth());
        writeSetting("sandbox_travel_speeds", vo.getTravelSpeeds());
        writeSetting("sandbox_social_max_km", vo.getSocialMaxKm());
    }

    /**
     * 集市管理页保存设置：只写集市/经济相关的键。
     * 页面上虽然拿到了整份设置，但普通管理员不该顺手改到世界运行参数（AI 开关、间隔、记忆、系统模型…），
     * 所以这里做一道作用域收口，配合后端只允许超管写 /sandbox/settings。
     */
    @Override
    public void saveShopSettings(SandboxSettingVO vo) {
        writeSetting("sandbox_shop_title", vo.getShopTitle());
        writeSetting("sandbox_shop_enabled", vo.getShopEnabled());
        writeSetting("sandbox_shop_auto_enabled", vo.getShopAutoEnabled());
        writeSetting("sandbox_shop_interval_hours", vo.getShopIntervalHours());
        writeSetting("sandbox_shop_auto_time", vo.getShopAutoTime());
        writeSetting("sandbox_shop_per_generate", vo.getShopPerGenerate());
        writeSetting("sandbox_shop_provider_id", vo.getShopProviderId());
        writeSetting("sandbox_shop_model", vo.getShopModel());
        writeSetting("sandbox_shop_prompt_extra", vo.getShopPromptExtra());
        writeSetting("sandbox_shop_limit_per_character", vo.getShopLimitPerCharacter());
        writeSetting("sandbox_shop_buy_per_day", vo.getShopBuyPerDay());
        writeSetting("sandbox_coin_rate", vo.getCoinRate());
    }

    /** 行动日志页保存「旅人纪闻设置」：只写纪闻相关的键 */
    @Override
    public void saveNewsSettings(SandboxSettingVO vo) {
        writeSetting("sandbox_news_title", vo.getNewsTitle());
        writeSetting("sandbox_news_enabled", vo.getNewsEnabled());
        writeSetting("sandbox_news_per_generate", vo.getNewsPerGenerate());
        writeSetting("sandbox_news_provider_id", vo.getNewsProviderId());
        writeSetting("sandbox_news_model", vo.getNewsModel());
        writeSetting("sandbox_news_prompt_extra", vo.getNewsPromptExtra());
        writeSetting("sandbox_news_auto_enabled", vo.getNewsAutoEnabled());
        writeSetting("sandbox_news_auto_time", vo.getNewsAutoTime());
    }

    private void writeSetting(String key, String value) {
        if (value == null) {
            return;
        }
        configService.setConfigValue(key, value.trim());
    }

    // ============================== 角色 ==============================

    @Override
    public List<SandboxCharacter> characters(Long worldId) {
        return characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>()
                .eq(worldId != null, SandboxCharacter::getWorldId, worldId)
                .orderByAsc(SandboxCharacter::getId));
    }

    @Override
    public SandboxCharacterDraftVO generateCharacter(SandboxCharacterGenerateDTO dto, Long worldId) {
        if (dto == null || dto.getRequirement() == null || dto.getRequirement().trim().isEmpty()) {
            throw new BusinessException("请先输入你的角色需求");
        }
        SandboxWorld world = world(worldId);
        List<SandboxLocation> locations = locations(world.getId());
        List<SandboxCharacter> exists = characters(world.getId());

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

        // 对实力 / 财富的态度：生成时由 AI 填写，管理员也可以在弹窗里改
        vo.setPowerView(truncate(trimToEmpty(obj.getStr("power_view")), 60));
        vo.setWealthView(truncate(trimToEmpty(obj.getStr("wealth_view")), 60));

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
                .append("\"power_view\":\"对自身实力的看法（4~12 字，例如 不甘平庸，想变强 / 够用就行）\",")
                .append("\"wealth_view\":\"对金钱财富的看法（4~12 字，例如 穷怕了，拼命攒钱 / 钱是身外之物）\",")
                .append("\"items\":[{\"name\":\"干粮\",\"quantity\":2,\"rarity\":1,\"description\":\"用油纸包着的干粮\"}]}\n")
                .append("要求：\n")
                .append("1. 角色名不要与【已有角色】重复，人设也不要去撞已有角色的定位与身份；\n")
                .append("2. 必须符合【世界观】的风格；location 只能从【地图地点】里挑一个；\n")
                .append("3. status 里体力、魔力、饥饿度是 0~100 的整数，心情用简短词语；\n")
                .append("4. coins 是初始金币，0~30 之间的整数；\n")
                .append("5. power_view 与 wealth_view 是这名角色对「实力」和「金钱」的态度，必须与身份经历自洽，")
                .append("并且**每个角色都要有明显差异**：有人想变强、有人只想安稳过日子；有人视钱如命、有人视钱财如粪土。")
                .append("这两条会写进行动提示词，直接影响角色平时是去修炼/接委托，还是摸鱼、散财；要能一眼看出性格；\n")
                .append("6. items 是背包里的初始物品，2~4 件，都是符合身份的日常小物件；")
                .append("rarity 用 1~5（1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说），不要给神器；\n")
                .append("7. 不要输出立绘、绘图关键词、英文名或任何与 JSON 无关的内容。");
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
            if (character.getCombatPower() == null) {
                character.setCombatPower(COMBAT_POWER_DEFAULT);
            }
            if (character.getWorldId() == null) {
                character.setWorldId(worldId(character.getWorldId()));
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
            // 新建角色如果自带初始金币，补一条流水：这样"流水合计 = 余额"始终成立，对账修复才有意义
            int initCoins = character.getCoins() == null ? 0 : character.getCoins();
            if (initCoins > 0) {
                addCoinLog(character.getId(), null, null, "init", initCoins, 0, initCoins, "初始金币");
            }
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
        return acts(characterId, locationName, null, page, size);
    }

    @Override
    public PageResult<SandboxAct> acts(Long characterId, String locationName, Long worldId, long page, long size) {
        LambdaQueryWrapper<SandboxAct> wrapper = new LambdaQueryWrapper<SandboxAct>()
                .eq(characterId != null, SandboxAct::getCharacterId, characterId)
                // 多世界：没指定角色时按世界过滤，避免行动时间线串世界
                .eq(worldId != null, SandboxAct::getWorldId, worldId)
                .eq(locationName != null && !locationName.trim().isEmpty(),
                        SandboxAct::getLocationName, locationName == null ? null : locationName.trim())
                .orderByDesc(SandboxAct::getCreateTime)
                .orderByDesc(SandboxAct::getId);
        IPage<SandboxAct> result = actMapper.selectPage(new Page<>(page, size), wrapper);
        fillMoveKm(result.getRecords());
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    /**
     * 给这一页行动补上「相比上一条移动了多少公里」。
     *
     * 为什么放服务端算：分页之后，某条行动的"上一条"不一定在同一页里
     * （按单个角色看时，每页第一条的上一条就在上一页），前端拿不到就画不出「移动 N km」。
     * 做法：每个角色在本页最早的那条，回查一条更早的行动当衔接点，然后在本页内两两计算。
     */
    private void fillMoveKm(List<SandboxAct> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        // 本页里每个角色最早的一条（列表是时间倒序，顺序遍历后写进去的就是最早的）
        Map<Long, SandboxAct> oldestInPage = new HashMap<>();
        for (SandboxAct act : records) {
            if (act.getCharacterId() != null) {
                oldestInPage.put(act.getCharacterId(), act);
            }
        }
        // 每个角色的"本页之前的那一条"，作为最旧一条的比较对象
        Map<Long, SandboxAct> neighbor = new HashMap<>();
        for (Map.Entry<Long, SandboxAct> entry : oldestInPage.entrySet()) {
            SandboxAct older = actMapper.selectOne(new LambdaQueryWrapper<SandboxAct>()
                    .eq(SandboxAct::getCharacterId, entry.getKey())
                    .lt(SandboxAct::getId, entry.getValue().getId())
                    .orderByDesc(SandboxAct::getId)
                    .last("limit 1"));
            if (older != null) {
                neighbor.put(entry.getKey(), older);
            }
        }
        // 从最旧往最新走：每一步的"上一条"就是刚刚处理过的那条
        for (int i = records.size() - 1; i >= 0; i--) {
            SandboxAct act = records.get(i);
            Long characterId = act.getCharacterId();
            if (characterId == null) {
                continue;
            }
            act.setMoveKm(moveKmBetween(act, neighbor.get(characterId)));
            neighbor.put(characterId, act);
        }
    }

    /** 两次行动之间换了地点时的实际距离（km）：没换地点、或不到 1 km 时返回 null（前台不显示标签） */
    private Double moveKmBetween(SandboxAct act, SandboxAct previous) {
        if (previous == null || !notBlank(act.getLocationName())
                || act.getLocationName().equals(previous.getLocationName())) {
            return null;
        }
        double km = kmBetween(act.getX() == null ? 50 : act.getX(), act.getY() == null ? 50 : act.getY(),
                previous.getX() == null ? 50 : previous.getX(), previous.getY() == null ? 50 : previous.getY());
        return km < 1 ? null : km;
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
     * 抢「这个角色正在执行」的锁。
     *
     * 为什么要锁：一次行动要等 AI 几十秒，期间如果同一个角色又跑了一次（手动点两次、手动碰上定时任务、
     * 回应回合撞上定时任务），两次都基于各自的旧快照算金币，后写的会把先写的覆盖掉——
     * 之前「晴少了 10 金币、羽少了 15、灵少了 18」就是这个原因；同时也白烧 token。
     *
     * 实现：running_at 置为当前时间；超过 sandbox_run_lock_minutes 分钟的老锁视为失效（进程崩过），允许抢占。
     */
    private boolean acquireRunLock(Long characterId) {
        int lockMinutes = Math.max(1, intConfig("sandbox_run_lock_minutes", 5));
        LocalDateTime expireAt = LocalDateTime.now().minusMinutes(lockMinutes);
        int updated = characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, characterId)
                .and(w -> w.isNull(SandboxCharacter::getRunningAt)
                        .or().lt(SandboxCharacter::getRunningAt, expireAt))
                .set(SandboxCharacter::getRunningAt, LocalDateTime.now()));
        return updated > 0;
    }

    /**
     * 释放执行锁（无论成功失败都必须调用）。
     *
     * 这里用 setSql("running_at = NULL") 而不是 .set(...) ：
     * 清空字段只走 SQL 最直接，也避免"传 null 被当成不更新"这类坑；
     * 一旦释放失败必须打 error 日志（锁没清掉会让角色一直显示"正在执行中"）。
     */
    private void releaseRunLock(Long characterId) {
        try {
            characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                    .eq(SandboxCharacter::getId, characterId)
                    .setSql("running_at = NULL"));
        } catch (Throwable t) {
            log.error("释放沙盒执行锁失败（角色 #{}），该角色会一直显示「正在执行中」直到锁超时：{}",
                    characterId, t.getMessage(), t);
        }
    }

    /** 管理员手动解除执行锁：锁超时是 5 分钟，这个是给卡住的角色一个立刻可用的出口 */
    @Override
    public void unlockCharacter(Long characterId) {
        releaseRunLock(characterId);
        log.info("管理员手动解除了沙盒角色 #{} 的执行锁", characterId);
    }

    /**
     * 记录一次行动失败：写 last_error、连续失败次数，并把下次行动时间往后推（失败退避）。
     * AI 调用失败与"AI 成功但落库失败"都走这里，保证任何失败在后台都能看到原因。
     */
    private void recordRunFailure(Long characterId, String msg) {
        SandboxCharacter current = characterMapper.selectById(characterId);
        int failCount = (current == null || current.getFailCount() == null ? 0 : current.getFailCount()) + 1;
        int backoffMinutes = SandboxBackoff.minutes(failCount,
                intConfig("sandbox_fail_backoff_base_minutes", SandboxBackoff.DEFAULT_BASE_MINUTES),
                intConfig("sandbox_fail_backoff_max_minutes", SandboxBackoff.DEFAULT_MAX_MINUTES));
        LocalDateTime retryAt = LocalDateTime.now().plusMinutes(backoffMinutes);
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, characterId)
                .set(SandboxCharacter::getLastError,
                        truncate(SandboxBackoff.describeError(failCount, backoffMinutes, msg), 480))
                .set(SandboxCharacter::getFailCount, failCount)
                .set(SandboxCharacter::getNextRunTime, retryAt));
        log.warn("沙盒角色 #{} 行动失败（连续第 {} 次），已退避 {} 分钟，下次尝试 {}：{}",
                characterId, failCount, backoffMinutes, retryAt, msg);
    }

    /**
     * 执行一次行动并处理回应链（调度用：单个角色失败只记日志，不影响其他角色）。
     */
    private SandboxAct runWithChain(Long characterId, boolean manual, boolean reaction, SandboxAct trigger,
                                    int depth, Set<Long> planned, AtomicInteger reactionCount) {
        SandboxAct act;
        try {
            act = runOnce(characterId, manual, reaction, trigger);
        } catch (SandboxBusyException e) {
            // 角色正在执行中：跳过就好，既不算失败也不触发失败退避
            log.info("沙盒角色 #{} 正在执行中，本次跳过", characterId);
            return null;
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
        // 并发保护：同一个角色同一时刻只允许一个行动在跑（见 acquireRunLock 的注释）
        if (!acquireRunLock(characterId)) {
            throw new SandboxBusyException("该角色正在执行中，请稍候再试");
        }
        try {
            return runOnceLocked(characterId, manual, reaction, trigger);
        } catch (BusinessException e) {
            // AI 调用失败等已经在里面记过原因与退避了，直接往外抛
            throw e;
        } catch (Throwable t) {
            // 兜底：AI 已经返回、但后续计算/落库抛错。以前这种失败既不记原因也不退避，
            // 管理员只会看到「没生成记录」，排查无从下手（本次「创建角色后第一次行动没记录」就是这一类）。
            String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            log.error("沙盒角色 #{} 行动落库失败", characterId, t);
            recordRunFailure(characterId, "生成行动记录失败：" + msg);
            throw new BusinessException("生成行动记录失败：" + msg);
        } finally {
            releaseRunLock(characterId);
        }
    }

    /** 真正执行一次行动（调用前必须已经持有执行锁） */
    private SandboxAct runOnceLocked(Long characterId, boolean manual, boolean reaction, SandboxAct trigger) {
        SandboxCharacter character = characterMapper.selectById(characterId);
        if (character == null) {
            throw new BusinessException("角色不存在");
        }
        SandboxWorld world = world(character.getWorldId());
        List<SandboxLocation> locations = locations(character.getWorldId());
        List<SandboxAct> recent = recentActs(characterId, PROMPT_ACT_LIMIT);
        List<SandboxInteraction> whispers = recentWhispers(characterId, character.getLastRunTime());
        // 同世界的其它角色与它们最近的动静，让角色有机会相遇、互动
        List<SandboxCharacter> companions = otherCharacters(characterId);
        List<SandboxAct> companionActs = neighborActs(companions);
        // 长期记忆（每日总结）与背包，都会写进提示词
        List<SandboxMemory> memories = recentMemories(characterId, intConfig("sandbox_memory_prompt_days", 5));
        List<SandboxItem> backpack = items(characterId);
        // 今天的旅人纪闻：行动时会参考，但不强制参与
        List<SandboxNews> news = todayNews(character.getWorldId());

        String systemPrompt = buildSystemPrompt(character, world, locations, companions);
        String userPrompt = buildUserPrompt(character, recent, whispers, companions, companionActs, reaction,
                trigger == null ? null : characterNameOf(companions, trigger.getCharacterId()), trigger,
                memories, backpack, news);
        // 提示词预算守护：超过上限时砍掉"氛围类"内容重来一次，避免数据增长后提示词无限膨胀
        int promptBudget = intConfig("sandbox_prompt_char_limit", 9000);
        if (systemPrompt.length() + userPrompt.length() > promptBudget) {
            List<SandboxAct> shortRecent = recent.size() > 6 ? new ArrayList<>(recent.subList(0, 6)) : recent;
            String compact = buildUserPrompt(character, shortRecent, whispers, companions, new ArrayList<>(),
                    reaction, trigger == null ? null : characterNameOf(companions, trigger.getCharacterId()), trigger,
                    memories, backpack, new ArrayList<>());
            if (compact.length() < userPrompt.length()) {
                log.info("沙盒提示词超出预算（{}+{} > {} 字符），已精简：去掉其他居民动静与今日要闻、最近行动取 6 条",
                        systemPrompt.length(), userPrompt.length(), promptBudget);
                userPrompt = compact;
            }
        }

        String raw;
        try {
            double temperature = character.getTemperature() == null ? 0.9 : character.getTemperature().doubleValue();
            // 手动执行用发起人自己的服务商，定时执行用系统服务商
            AiProvider provider = manual
                    ? aiProviderService.resolveManualProvider(character.getProviderId())
                    : sandboxSystemProvider(character);
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
            // 失败时只记录原因，不生成记录，避免接口异常时时间线被刷屏；
            // 同时做失败退避：把 next_run_time 往后推，别让角色一直「逾期」被每 5 分钟重试一次
            String msg = e.getMessage() == null ? "AI 调用失败" : e.getMessage();
            recordRunFailure(characterId, msg);
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
        // 当前目标：AI 没给就沿用上一次的，避免目标凭空丢失
        String goal = character.getGoal();
        int coinChange = 0;
        /** 花费被上限截断时的说明，会附在金币流水后面 */
        String spendNote = "";
        // 战斗力：默认 10，只有 AI 明确给出变化时才动，单次幅度限制在 ±COMBAT_STEP_MAX
        int combatPower = character.getCombatPower() == null ? COMBAT_POWER_DEFAULT : character.getCombatPower();
        int combatChange = 0;
        Integer aiNextMinutes = null;
        String aiNextReason = null;

        JSONObject obj = parseJson(raw);
        // 兜底校验：解析结果必须有 actions（哪怕空数组）才算"按格式返回"。
        // 解析器曾经误取到内层小对象（例如 {"delta":-1}），那种对象能解析、但字段全空，
        // 就会写成一条"空白行动"；现在这类情况统一按"没按格式返回"处理，交给补救流程。
        if (obj == null || obj.get("actions") == null) {
            // AI 没有按格式返回：原文保存下来，方便管理员在后台看到并调整提示词
            act.setFromAi(0);
            // 注意：沙盒角色的 AI 回复不做敏感词过滤，保持原文（过滤会误伤正常词汇）。
            // 但解析失败时前台会看到这段内容，所以要用 displayText 去掉 <draft>/<review> 这些标记
            act.setActions(truncate(trimToEmpty(SandboxReplyParser.displayText(raw)), 1000));
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
            // 互动只认"真的在附近"的角色：以前只按名字匹配，AI 能让相隔几百公里的人隔空聊天、还会加好感度
            List<SandboxCharacter> nearby = nearbyCompanions(character, companions, x, y, locationName);
            // 这一步和哪些角色互动了（只保留世界里真实存在的角色名）
            act.setCompanions(matchCompanions(obj.getJSONArray("companions"), nearby));
            // 好感度变化：AI 返回 { "角色名": 3 }，服务端累加到对应关系上
            act.setFavorChange(applyFavorChanges(character, obj.get("favor_changes"), nearby));
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

            // 当前目标：AI 每步维护；没给或给空就沿用上一次
            String aiGoal = truncate(trimToEmpty(obj.getStr("goal")), 100);
            if (!aiGoal.isEmpty()) {
                goal = aiGoal;
            }

            // 金币变化：赚取为正、消耗为负，余额不允许变成负数
            coinChange = Convert.toInt(obj.get("coins_change"), 0);
            // 原子增减：这一步先把自己赚/花的钱落到库里，后面扣集市购买时才看得到真实余额。
            // 这样即使同时有别的写入（用户贡献金币、别的行动），也不会互相覆盖。
            if (coinChange > 0) {
                coins = addCoins(characterId, coinChange);
            } else if (coinChange < 0) {
                // 花费上限：AI 常常随口写个大额支出（例如第一次行动就 -15，而 actions 里只买了浆果和火把）。
                // 按「余额分档 + 后台配置」取上限，超出就截断，并在流水里注明原因，方便事后核对。
                int spendLimit = SandboxSpendLimit.maxSpend(coins, intConfig("sandbox_max_spend_per_act", 10));
                if (-coinChange > spendLimit) {
                    log.info("沙盒角色 #{} 本次花费 {} 金币超过上限 {}，已按上限截断",
                            characterId, -coinChange, spendLimit);
                    spendNote = "（原花费 " + (-coinChange) + " 金币，已按单次上限 " + spendLimit + " 截断）";
                    coinChange = -spendLimit;
                }
                Integer balance = trySpendCoins(characterId, -coinChange);
                if (balance == null) {
                    // 余额不够（AI 给的花费超过身上金币，或被并发花掉了）：这一步就当没花钱
                    coins = currentCoins(characterId);
                    coinChange = 0;
                } else {
                    coins = balance;
                }
            } else {
                coins = currentCoins(characterId);
            }
            // 金币流水就写在这里（而不是等行动记录落库之后）：
            // 集市购买会在后面扣款，如果这条流水写到那时候，它的"当时余额"就会用最终余额，看起来像没扣钱
            if (coinChange != 0) {
                addCoinLog(characterId, null, null, coinChange > 0 ? "earn" : "spend", coinChange, 0, coins,
                        truncate(blankToDefault(act.getSummary(), coinChange > 0 ? "日常赚取" : "日常花销") + spendNote, 190));
            }

            // 战斗力变化：与金币一样是可选项，没给就按 0 处理
            combatChange = Math.max(-COMBAT_STEP_MAX, Math.min(COMBAT_STEP_MAX,
                    Convert.toInt(obj.get("combat_change"), 0)));
            combatPower = Math.max(COMBAT_POWER_MIN, combatPower + combatChange);

            // 旅人集市自购：AI 返回 shop_buy 时，用角色自己的金币结算（扣库存、进背包、写流水）
            // 注意顺序：先把这一步赚到的钱算进来，再扣购买花费，余额不足就买不成
            int[] coinsHolder = new int[]{coins};
            Set<String> boughtNames = new HashSet<>();
            String shopChange = applyShopPurchases(character, obj.get("shop_buy"), coinsHolder, boughtNames);
            coins = coinsHolder[0];
            // 物品变化：AI 返回 { "物品名": 1 }，正为获得、负为消耗
            // 刚在集市买到的物品要排除掉，避免 AI 同时写进 items_change 造成重复计数
            String itemChange = applyItemChanges(character, obj.get("items_change"), boughtNames);
            if (shopChange != null) {
                itemChange = itemChange == null ? shopChange : truncate(itemChange + "、" + shopChange, 290);
            }
            act.setItemChange(itemChange);
        }

        if (act.getLocationName() == null) {
            act.setLocationName(locationName);
        }
        if (act.getX() == null) {
            act.setX(x);
            act.setY(y);
        }
        act.setCoinChange(coinChange);
        act.setCombatChange(combatChange);

        // 赶路时间兜底：防止 AI 让角色"一步跨过 80 km 却只花 30 分钟"
        aiNextMinutes = applyTravelFloor(character, x, y, locationName, aiNextMinutes);
        LocalDateTime next = resolveNextRunTime(character, now, aiNextMinutes);
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, characterId)
                .set(SandboxCharacter::getX, x)
                .set(SandboxCharacter::getY, y)
                .set(SandboxCharacter::getLocationName, locationName)
                .set(SandboxCharacter::getSubLocation, subLocation)
                .set(SandboxCharacter::getGoal, goal)
                .set(SandboxCharacter::getStatusJson, statusJson)
                // 金币不在这里整值写回：前面已经用原子增减落到库里了（见 addCoins / trySpendCoins）
                .set(SandboxCharacter::getCombatPower, combatPower)
                .set(SandboxCharacter::getLastRunTime, now)
                .set(SandboxCharacter::getNextRunTime, next)
                .set(SandboxCharacter::getNextReason, aiNextReason == null || aiNextReason.isEmpty() ? null : aiNextReason)
                .set(SandboxCharacter::getLastError, null)
                // 成功一次就把连续失败次数清零，退避随之回到起步值
                .set(SandboxCharacter::getFailCount, 0));

        actMapper.insert(act);
        // 金币流水已在上面（金币落库时）写过：那时余额才是这一步真实的余额
        return act;
    }

    /**
     * 调用 AI 生成行动，并要求带上「下次行动间隔 + 原因」。
     * 如果 AI 掉格式（缺字段 / 不是合法 JSON），会自动补救（最多 3 次）；仍未给出时返回
     * 「字段最全」的那一次结果，由调用方按默认随机间隔兜底，并补一个默认原因。
     *
     * 补救策略分两种，核心思路是「有原文就只补字段，没原文才重跑」：
     *   1. 主调用有内容但缺字段 / 不是合法 JSON → **补全调用**：把它自己的输出回传，
     *      只让它补缺失字段，再由 SandboxOutputRepair 做字段级合并（剧情一个字不改）。
     *      输入只有原文 + 很短的一段上下文，token 大约只有重跑的 1/5 ~ 1/10。
     *   2. 主调用返回空、或短到没有有效内容 → 没东西可补，退回**重跑**：
     *      完整提示词 + 「上一次缺了哪个字段」的强调（系统提示词追加说明，
     *      用户提示词开头与结尾各放一次，模型对首尾最敏感）。
     *
     * 执行顺序：主调用 →（可选）输出自查 → 字段完整性检查。自查排在检查之前，
     * 这样即使自查把字段改没了，也会被下面的检查发现并触发补救，而不是直接落库。
     */
    private String callAiWithInterval(SandboxCharacter character, AiProvider provider,
                                      String systemPrompt, String userPrompt, double temperature,
                                      List<SandboxLocation> locations, List<SandboxCharacter> companions) {
        String verifyMode = verifyMode();
        // 记下主调用的审计标签，自查/补全时临时换掉，保证日志能区分不同类型的调用
        String mainAction = AuditContext.action();
        boolean mainScheduled = AuditContext.isSchedule();
        // 补全调用用的最小上下文（当前时间、可用地点、其它角色），循环外算一次就够
        String repairContext = repairContext(character, locations, companions);

        String current = null;
        // 兜底：记录「字段最全」的那一次输出，最后全都缺字段时用它（比直接用最后一次更合理）
        String best = null;
        int bestMissing = Integer.MAX_VALUE;
        // 上一次输出缺失的字段说明，第一次调用时为空
        List<String> missing = Collections.emptyList();
        for (int attempt = 1; attempt <= 3; attempt++) {
            String produced;
            if (attempt == 1) {
                // 第一次：完整提示词正常生成（开启三段式时走"预填充 + 草稿/自审/终稿"）
                produced = chatWithOptionalDraft(provider, character.getModel(), systemPrompt, userPrompt, temperature);
            } else if (SandboxOutputRepair.hasUsableContent(current)) {
                // 掉格式但手上有原文 → 只补缺的字段（便宜、保原文）
                produced = repairOutput(character, provider, current, repairContext, missing, temperature,
                        mainAction, mainScheduled);
                produced = mergeRepair(current, produced);
            } else {
                // 主调用什么都没给（空响应 / 太短），没有原文可补，只能带着提示重跑
                produced = chatWithOptionalDraft(provider, character.getModel(),
                        systemPrompt + buildRetrySystemSuffix(missing), buildRetryUserPrompt(userPrompt, missing), temperature);
            }
            String candidate = produced;
            // 输出自查有三种模式（后台「世界与地图」可切换）：
            //   off        关闭
            //   suspicious 仅可疑时查（默认）：命中规则才多花一次调用
            //   always     每次都查
            List<String> verifyIssues = Collections.emptyList();
            boolean needVerify = "always".equals(verifyMode);
            if ("suspicious".equals(verifyMode)) {
                verifyIssues = SandboxOutputIssues.find(parseJson(candidate), locationNames(locations),
                        characterNames(companions),
                        SandboxSpendLimit.maxSpend(character.getCoins() == null ? 0 : character.getCoins(),
                                intConfig("sandbox_max_spend_per_act", 10)));
                needVerify = !verifyIssues.isEmpty();
                if (needVerify) {
                    log.info("沙盒角色「{}」输出可疑（{}），触发一次自查", character.getName(),
                            String.join("；", verifyIssues));
                }
            }
            if (needVerify) {
                // 自查单独打审计标签，方便和主调用区分
                applyAudit(mainAction, mainScheduled, "沙盒·输出自查");
                try {
                    // 只把最终的 JSON 交给自查：三段式回复里还夹着草稿与自审，整段发过去会干扰判断
                    String finalJson = SandboxReplyParser.extractFinalJson(candidate);
                    candidate = verifyOutput(character, finalJson == null ? candidate : finalJson,
                            locations, companions, verifyIssues);
                } finally {
                    // 还原主调用的审计标签，避免后续调用被记成自查
                    applyAudit(mainAction, mainScheduled, mainAction);
                }
            }
            missing = findMissingFields(parseJson(candidate));
            if (missing.isEmpty()) {
                return candidate;
            }
            if (missing.size() < bestMissing) {
                best = candidate;
                bestMissing = missing.size();
            }
            // 下一次补救以这一次的结果为基准，保留已经补上的字段
            current = candidate;
            if (attempt < 3) {
                log.warn("沙盒角色「{}」第 {} 次输出缺少字段（{}），准备{}", character.getName(), attempt,
                        String.join("、", missing),
                        SandboxOutputRepair.hasUsableContent(current) ? "补全" : "重跑");
            } else {
                log.warn("沙盒角色「{}」补救 {} 次后仍缺少字段（{}），改用默认间隔兜底", character.getName(), attempt,
                        String.join("、", missing));
            }
        }
        return best == null ? current : best;
    }

    /**
     * 角色行动的主调用：开启三段式时用「预填充 + 草稿/自审/终稿」。
     *
     * 预填充的意义：把 assistant 消息预置成 `&lt;draft&gt;` 的开头，模型只能"接着写"，
     * 于是几乎不可能跑成散文、也不会忘记先写草稿；返回内容不带预填充部分，所以要 manually 拼回去。
     *
     * 注意：三段式的输出不是纯 JSON，所以这里**不能**要求 response_format=json_object
     * （否则上游会强制纯 JSON，草稿段直接被判违规）。
     */
    private String chatWithOptionalDraft(AiProvider provider, String model, String systemPrompt,
                                        String userPrompt, Double temperature) {
        if (!draftModeOn()) {
            return aiProviderService.chat(provider, model, systemPrompt, userPrompt, temperature);
        }
        // 预填充必须和提示词里的"第一段"一致：开了思考阶段就先写 <think>，否则先写 <draft>。
        // （以前写死 <draft>，模型被逼着先写草稿，<think> 只能排到后面，段落顺序就乱了）
        String prefill = thinkStageOn() ? "<think>\n" : "<draft>\n";
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(userPrompt));
        messages.add(ChatMessage.assistant(prefill));
        String content = aiProviderService.chatMessages(provider, model, messages, temperature, false);
        return prefill + (content == null ? "" : content);
    }

    /** 三段式（草稿→自审→终稿）是否开启；默认开启 */
    private boolean draftModeOn() {
        return !"off".equalsIgnoreCase(configService.getConfigValue("sandbox_draft_mode", "on").trim());
    }

    /** 按「手动 / 定时」还原审计标签 */
    private void applyAudit(String mainAction, boolean scheduled, String action) {
        if (scheduled) {
            AuditContext.schedule(action);
        } else {
            AuditContext.manual(action);
        }
    }

    /**
     * 补全调用：把 AI 自己上一次的输出回传，只让它补上缺失/非法的字段。
     * 相比重跑：剧情不动、token 只要 1/5 ~ 1/10、任务更简单也更不容易再失败。
     * 返回的是模型的原始回复，调用方必须再走 mergeRepair 做字段级合并。
     */
    private String repairOutput(SandboxCharacter character, AiProvider provider, String current,
                                String context, List<String> missing, double temperature,
                                String mainAction, boolean scheduled) {
        StringBuilder sys = new StringBuilder();
        sys.append("你是一个 JSON 校正器。下面会给你一段角色扮演的输出，它可能缺少字段、或者不是合法 JSON。\n")
                .append("你的任务只有一件：把它整理成符合约定的完整 JSON，**补上缺失或非法的字段**。\n")
                .append("硬性要求：\n")
                .append("1. 已经存在的字段必须原样保留，一个字都不要改写（actions、inner_voice、summary、location、x、y 等）；\n")
                .append("2. 只补缺失或非法的字段，不要新增约定之外的字段；\n")
                .append("3. 如果原文根本不是 JSON，就按它描述的意思整理成 JSON，不要丢掉它写过的行动；\n")
                .append("4. 只输出这一个 JSON 对象，不要解释文字、不要 Markdown 代码块。\n")
                .append("JSON 结构：\n")
                .append("{\"next_after_minutes\":45,\"next_after_reason\":\"稍作停留\",\"location\":\"地点名\",")
                .append("\"sub_location\":\"二级地点（自己创作）\",\"x\":35,\"y\":62,\"actions\":[\"具体动作\"],")
                .append("\"inner_voice\":\"心里话\",\"status\":{\"体力\":80,\"魔力\":45,\"饥饿度\":30,\"心情\":\"平静\"},")
                .append("\"coins_change\":0,\"combat_change\":0,\"companions\":[],\"favor_changes\":{},")
                .append("\"items_change\":{\"物品名\":{\"delta\":1,\"description\":\"6~20 字说明它是什么、有什么用\"}},\"news_refs\":[],")
                .append("\"summary\":\"30 字以内概括这一步\"}\n")
                .append("字段要求：next_after_minutes 是大于 0 的整数分钟；next_after_reason 是 2~6 个字；")
                .append("status 必须有体力、魔力、饥饿度（0~100 整数）与心情；x / y 是 0~100 的地图百分比。\n")
                .append("combat_change 是这一步战斗力的变化（整数，日常填 0，只有学会新魔法、得到强力装备、受伤这类才给 ±1~±3），")
                .append("给非 0 时原因必须写在 actions 里；原文已有的 combat_change 要原样保留。\n")
                .append("items_change 里每一项是 {\"delta\": 数量变化, \"description\": \"物品描述\"}：")
                .append("新获得的物品（delta 为正）**必须**补上 6~20 字的 description；只是消耗已有物品时 description 可省略。\n")
                .append("间隔要与 actions 描述相符：睡觉/过夜 360~600（午睡 30~90）、长途赶路 120~240、")
                .append("专注做事 60~180、吃饭逛街 15~45、短暂交谈 10~30、警戒守夜 30~90。\n");
        if (!missing.isEmpty()) {
            sys.append("本次特别需要补齐：").append(String.join("；", missing)).append("\n");
        }
        StringBuilder user = new StringBuilder();
        user.append(context).append("\n【需要整理的输出】\n").append(current);
        // 补全调用单独打审计标签，方便在后台看清一次行动到底花了几次调用
        applyAudit(mainAction, scheduled, "沙盒·补全输出");
        try {
            return aiProviderService.chat(provider, character.getModel(), sys.toString(), user.toString(), 0.2d);
        } finally {
            applyAudit(mainAction, scheduled, mainAction);
        }
    }

    /**
     * 补全调用用的最小上下文：当前时间、角色当前位置、可用地点名、其它角色名。
     * 刻意不带记忆和最近行动 —— 补全只需要判断「间隔是否合理」「地点名能不能用」。
     */
    private String repairContext(SandboxCharacter character, List<SandboxLocation> locations,
                                 List<SandboxCharacter> companions) {
        StringBuilder sb = new StringBuilder();
        sb.append("【当前情况】\n")
                .append("现在时间：").append(timeText(LocalDateTime.now())).append("\n")
                .append("角色：").append(character.getName());
        if (notBlank(character.getTitle())) {
            sb.append("（").append(character.getTitle()).append("）");
        }
        sb.append("\n当前位置：")
                .append(blankToDefault(placeText(character.getLocationName(), character.getSubLocation()), "尚未确定"))
                .append("\n身上金币：").append(character.getCoins() == null ? 0 : character.getCoins()).append(" 枚\n");
        if (!locations.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (SandboxLocation location : locations) {
                names.add(location.getName());
            }
            sb.append("可用地点：").append(String.join("、", names)).append("\n");
        }
        if (!companions.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (SandboxCharacter other : companions) {
                names.add(other.getName());
            }
            sb.append("世界里的其他角色：").append(String.join("、", names)).append("\n");
        }
        return sb.toString();
    }

    /**
     * 补全结果的字段级合并：以原文为基准，只采纳「缺失或非法」的字段。
     * 即使模型借着补全把整段重写了一遍，落库的剧情仍然是第一次生成的那份。
     */
    private String mergeRepair(String current, String repaired) {
        JSONObject base = parseJson(current);
        JSONObject fix = parseJson(repaired);
        if (fix == null) {
            // 补全也失败：有原文就用原文
            return base != null ? current : repaired;
        }
        if (base == null) {
            // 原文不是 JSON（散文 / 被截断），没有可保留的结构，只能整份采用补全结果
            return repaired;
        }
        return SandboxOutputRepair.merge(base, fix).toString();
    }

    /**
     * 检查 AI 输出里「必须存在」的字段是否齐全。
     * 返回缺失字段的中文说明列表；返回空列表表示字段齐全（可以正常入库）。
     *
     * 省略后不影响正确性的字段不在这里（例如 companions 空数组、coins_change 缺省算 0、
     * sub_location 缺省沿用上一次、x/y 缺省沿用上一次位置），它们不会触发补全，省调用次数。
     */
    private List<String> findMissingFields(JSONObject obj) {
        List<String> missing = new ArrayList<>();
        if (obj == null) {
            // 连 JSON 都没解析出来，属于更严重的情况，单独提示
            missing.add("整段输出根本不是合法的 JSON 对象（被多余文字包裹、缺少花括号或使用了代码块标记）");
            return missing;
        }
        for (String key : REQUIRED_FIELDS) {
            // 判定规则与补全合并共用一套，避免两边口径不一致
            if (SandboxOutputRepair.needRepair(key, obj.get(key))) {
                missing.add(fieldHint(key));
            }
        }
        // 新获得的物品必须带描述，否则背包里会是一堆光有名字的东西
        missing.addAll(findMissingItemDesc(obj.get("items_change")));
        return missing;
    }

    /**
     * 检查 items_change 里「新获得的物品」有没有 description。
     * 只消耗已有物品（delta 为负）时不需要描述，所以不会触发补全。
     */
    private List<String> findMissingItemDesc(Object itemsChange) {
        List<String> missing = new ArrayList<>();
        if (!(itemsChange instanceof JSONObject)) {
            return missing;
        }
        JSONObject obj = (JSONObject) itemsChange;
        for (String name : obj.keySet()) {
            Object value = obj.get(name);
            if (SandboxOutputRepair.itemDelta(value) <= 0) {
                continue;
            }
            if (notBlank(SandboxOutputRepair.itemDescription(value))) {
                continue;
            }
            missing.add("items_change 里新获得的「" + name
                    + "」缺少 description（6~20 字说明它是什么、有什么用、看起来什么样）");
        }
        return missing;
    }

    /** 缺失字段的中文说明（补全提示词、重跑提示词与日志都用它） */
    private String fieldHint(String key) {
        switch (key) {
            case "next_after_minutes":
                return "next_after_minutes（下一次行动间隔的分钟数，必须是大于 0 的整数）";
            case "next_after_reason":
                return "next_after_reason（这段时间在做什么的简短说明，2~6 个字）";
            case "location":
                return "location（这一步所处的地点名称）";
            case "actions":
                return "actions（1~3 条具体、有画面感的动作）";
            case "inner_voice":
                return "inner_voice（角色此刻的心里话）";
            case "status":
                return "status（体力、魔力、饥饿度 0~100 的整数，以及心情）";
            case "summary":
                return "summary（30 字以内概括这一步）";
            default:
                return key;
        }
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
        // 多个世界各自成批：只在「运行中」的世界里挑角色，互动与回应链也限定在同一个世界内
        for (SandboxWorld world : worlds()) {
            if (world.getEnabled() == null || world.getEnabled() != 1) {
                continue;
            }
            runScheduledForWorld(world.getId());
        }
    }

    /** 跑某一个世界里到期的角色（同一轮合并执行，方便互相遇见） */
    private void runScheduledForWorld(Long worldId) {
        List<SandboxCharacter> characters = characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getWorldId, worldId)
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
        return runAll(null);
    }

    @Override
    public SandboxRunAllVO runAll(Long worldId) {
        SandboxRunAllVO vo = new SandboxRunAllVO();
        List<SandboxCharacter> characters = characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>()
                // 多世界：只跑当前这个世界里启用的角色
                .eq(SandboxCharacter::getWorldId, worldId(worldId))
                .eq(SandboxCharacter::getEnabled, 1)
                .orderByAsc(SandboxCharacter::getId));
        vo.setTotal(characters.size());
        for (SandboxCharacter character : characters) {
            try {
                // 全员一起行动，本身就已经互相可见，不再额外触发回应回合
                SandboxAct act = runOnce(character.getId(), true, false, null);
                vo.setSuccess(vo.getSuccess() + 1);
                vo.getItems().add(character.getName() + "：" + blankToDefault(act.getSummary(), "已行动"));
            } catch (SandboxBusyException e) {
                // 这个角色正在执行（手动点了、或上一轮还没跑完）：不算失败，只是跳过
                vo.getItems().add(character.getName() + "：正在执行中，本轮已跳过");
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
        // 旅人低语关闭时，前台拿不到任何低语（后台仍可查看，走的是另一个接口）
        if (!"1".equals(configService.getConfigValue("sandbox_whisper_enabled", "1"))) {
            return PageResult.of(0L, new ArrayList<>());
        }
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
        if (!"1".equals(configService.getConfigValue("sandbox_whisper_enabled", "1"))) {
            throw new BusinessException("旅人低语功能已关闭");
        }
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
        // 记录所属世界：多世界下便于按世界清理
        interaction.setWorldId(character.getWorldId());
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

        // 原子加钱：不能用"读出来加完再整值写回"，否则会覆盖掉同时进行的角色行动赚到的金币
        int coins = addCoins(characterId, gained);
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
    // ============================== 金币（原子增减 + 对账修复） ==============================

    /** 当前金币余额（直接读库，避免用快照） */
    private int currentCoins(Long characterId) {
        SandboxCharacter fresh = characterMapper.selectById(characterId);
        return fresh == null || fresh.getCoins() == null ? 0 : fresh.getCoins();
    }

    /**
     * 原子增加金币（正数）并返回新的余额。
     * 用 SQL 里的 coins = coins + delta，而不是"读出来加完再整值写回"——
     * 否则一次几十秒的 AI 行动期间，用户贡献金币、集市购买、另一个行动的改动都会被覆盖。
     */
    private int addCoins(Long characterId, int delta) {
        if (delta > 0) {
            characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                    .eq(SandboxCharacter::getId, characterId)
                    .setSql("coins = coins + " + delta));
        }
        return currentCoins(characterId);
    }

    /**
     * 尝试原子扣款：余额够才扣，返回扣完的余额；余额不足返回 null（调用方当作"买不起"处理）。
     * 条件更新保证不会扣成负数，也不会和其它并发写互相覆盖。
     */
    private Integer trySpendCoins(Long characterId, int amount) {
        if (amount <= 0) {
            return currentCoins(characterId);
        }
        int updated = characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, characterId)
                .ge(SandboxCharacter::getCoins, amount)
                .setSql("coins = coins - " + amount));
        if (updated <= 0) {
            return null;
        }
        return currentCoins(characterId);
    }

    /**
     * 金币对账修复：以金币流水为准重算每个角色的余额，并把每条流水的"当时余额"按顺序重算。
     *
     * 前提：所有金币变动都会写流水（角色行动、集市自购、旅人贡献、管理员调整、初始金币），
     * 所以 SUM(coins) 就是真实余额。没有流水的角色不参与修复（可能是历史数据或刚建的角色）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> repairCoins(Long worldId) {
        Long wid = worldId(worldId);
        List<Map<String, Object>> details = new ArrayList<>();
        int fixed = 0;
        for (SandboxCharacter character : characters(wid)) {
            List<SandboxCoinLog> logs = coinLogMapper.selectList(new LambdaQueryWrapper<SandboxCoinLog>()
                    .eq(SandboxCoinLog::getCharacterId, character.getId())
                    .orderByAsc(SandboxCoinLog::getId));
            if (logs.isEmpty()) {
                continue;
            }
            int before = character.getCoins() == null ? 0 : character.getCoins();
            int running = 0;
            int logsFixed = 0;
            for (SandboxCoinLog log : logs) {
                int delta = log.getCoins() == null ? 0 : log.getCoins();
                running += delta;
                if (log.getBalance() == null || log.getBalance() != running) {
                    coinLogMapper.update(null, new LambdaUpdateWrapper<SandboxCoinLog>()
                            .eq(SandboxCoinLog::getId, log.getId())
                            .set(SandboxCoinLog::getBalance, running));
                    logsFixed++;
                }
            }
            if (before != running) {
                characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                        .eq(SandboxCharacter::getId, character.getId())
                        .set(SandboxCharacter::getCoins, running));
                fixed++;
            }
            if (before != running || logsFixed > 0) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("characterId", character.getId());
                row.put("characterName", character.getName());
                row.put("before", before);
                row.put("after", running);
                row.put("logsFixed", logsFixed);
                details.add(row);
            }
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("checked", characters(wid).size());
        report.put("fixed", fixed);
        report.put("details", details);
        log.info("沙盒金币对账完成（世界 {}）：修正 {} 个角色", wid, fixed);
        return report;
    }

    /**
     * 输出自查模式：off（关闭）/ suspicious（仅可疑时查，默认）/ always（每次都查）。
     *
     * 新配置 sandbox_verify_mode 优先；没配时兼容旧开关 sandbox_verify_enabled
     * （1 = 每次都查，0 = 关闭）；两个都没有就用默认的「仅可疑时查」。
     */
    private String verifyMode() {
        String mode = configService.getConfigValue("sandbox_verify_mode", "").trim().toLowerCase();
        if ("off".equals(mode) || "always".equals(mode) || "suspicious".equals(mode)) {
            return mode;
        }
        String legacy = configService.getConfigValue("sandbox_verify_enabled", "").trim();
        if ("1".equals(legacy)) {
            return "always";
        }
        if ("0".equals(legacy)) {
            return "off";
        }
        return "suspicious";
    }

    /** 可用地点名列表（输出自查 / 可疑判定用） */
    private List<String> locationNames(List<SandboxLocation> locations) {
        List<String> names = new ArrayList<>();
        for (SandboxLocation location : locations) {
            if (notBlank(location.getName())) {
                names.add(location.getName());
            }
        }
        return names;
    }

    /** 可用角色名列表（输出自查 / 可疑判定用） */
    private List<String> characterNames(List<SandboxCharacter> companions) {
        List<String> names = new ArrayList<>();
        for (SandboxCharacter other : companions) {
            if (notBlank(other.getName())) {
                names.add(other.getName());
            }
        }
        return names;
    }

    private void addCoinLog(Long characterId, Long userId, String userName, String type,
                            int coins, int pointsCost, int balance, String remark) {
        SandboxCoinLog log = new SandboxCoinLog();
        log.setCharacterId(characterId);
        // 记录所属世界：多世界下便于按世界清理
        SandboxCharacter owner = characterMapper.selectById(characterId);
        log.setWorldId(owner == null ? worldId(null) : owner.getWorldId());
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
    public SandboxPortalVO portal(Long worldId) {
        SandboxWorld world = world(worldId);
        SandboxPortalVO vo = new SandboxPortalVO();
        // 世界「是否运行」是独立开关：全局沙盒开关关掉、或这个世界停跑，前台都只展示历史
        vo.setEnabled("1".equals(configService.getConfigValue("sandbox_enabled", "0"))
                && world.getEnabled() != null && world.getEnabled() == 1);
        // 旅人低语总开关
        vo.setWhisperEnabled("1".equals(configService.getConfigValue("sandbox_whisper_enabled", "1")));
        vo.setWhisperPoints(intConfig("sandbox_whisper_points", 1));
        vo.setCoinRate(Math.max(1, intConfig("sandbox_coin_rate", 10)));
        vo.setNewsTitle(configService.getConfigValue("sandbox_news_title", "旅人纪闻"));
        vo.setNews(todayNews(world.getId()));
        // 旅人集市
        boolean shopEnabled = "1".equals(configService.getConfigValue("sandbox_shop_enabled", "1"));
        vo.setShopTitle(configService.getConfigValue("sandbox_shop_title", "旅人集市"));
        vo.setShopEnabled(shopEnabled);
        vo.setShopBuyPerDay(intConfig("sandbox_shop_buy_per_day", 2));
        vo.setKmMapWidth((int) mapWidthKm());
        vo.setShopItems(shopEnabled ? shopItems(world.getId()) : new ArrayList<>());
        vo.setWorld(world);
        vo.setLocations(locations(world.getId()));
        List<SandboxCharacterVO> list = new ArrayList<>();
        for (SandboxCharacter character : characters(world.getId())) {
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
        vo.setCombatPower(character.getCombatPower() == null ? COMBAT_POWER_DEFAULT : character.getCombatPower());
        vo.setGoal(character.getGoal());
        vo.setPowerView(character.getPowerView());
        vo.setWealthView(character.getWealthView());
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
        // 前台按时间倒序展示：最新的一次行动排在最上面
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
    /**
     * 事件与角色的距离描述：先算实际公里数，再翻译成自然语言（并带上 km，避免 AI 又想自己换算）。
     */
    private String distanceText(int x1, int y1, int x2, int y2) {
        double km = kmBetween(x1, y1, x2, y2);
        if (km <= 3) {
            return "就在附近（约 " + SandboxGeo.kmText(km) + "）";
        }
        if (km <= 15) {
            return "半天内能到（约 " + SandboxGeo.kmText(km) + "）";
        }
        if (km <= 60) {
            return "大约一天路程（约 " + SandboxGeo.kmText(km) + "）";
        }
        if (km <= 200) {
            return "要走两三天（约 " + SandboxGeo.kmText(km) + "）";
        }
        return "非常遥远（约 " + SandboxGeo.kmText(km) + "）";
    }

    /** 一天的时段名称（清晨/上午/中午/下午/傍晚/晚上/深夜） */
    private String periodOfDay(LocalTime time) {
        return periodOfDayBody(time);
    }

    /**
     * 附带在「现在时间」后面的作息提示。
     * 白天到傍晚明确提示"还不到睡觉时间"，深夜则提示"该休息了"，让作息更像真人。
     */
    private String sleepHint(LocalDateTime now) {
        int hour = now.getHour();
        if (hour >= 5 && hour < 9) {
            return "（清晨，适合起床、准备或早市）";
        }
        if (hour >= 9 && hour < 17) {
            return "（白天，还远不到睡觉的时间）";
        }
        if (hour >= 17 && hour < 21) {
            return "（傍晚，通常还在活动：吃饭、收尾、赶路或待在酒馆；除非很累或受伤，不要这会儿就睡下）";
        }
        if (hour >= 21 && hour < 23) {
            return "（晚上，准备休息也算合理，但也可以再活动一会儿）";
        }
        return "（深夜，正常情况下应该在休息或守夜）";
    }

    private String periodOfDayBody(LocalTime time) {
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
                int lw = location.getWidth() == null ? 0 : location.getWidth();
                int lh = location.getHeight() == null ? 0 : location.getHeight();
                sb.append("- ").append(location.getName());
                List<double[]> polygon = polygonOf(location);
                if (polygon != null && notBlank(location.getPolygon())) {
                    // 套索画的多边形：只给「中心 + 外接范围」，不把顶点列表塞进提示词（否则每条几十个坐标会白烧 token）。
                    // 归属判定由服务端完成，AI 只要给一个大致坐标就够了。
                    double[] box = SandboxGeo.bbox(polygon);
                    double[] center = SandboxGeo.labelPoint(polygon);
                    sb.append("（区域中心 x=").append(Math.round(center[0])).append(", y=").append(Math.round(center[1]))
                            .append("；大致范围 x ").append(Math.round(box[0])).append("~").append(Math.round(box[2]))
                            .append("、y ").append(Math.round(box[1])).append("~").append(Math.round(box[3]))
                            .append("，边界形状不规则）");
                } else if (lw > 0 && lh > 0) {
                    int lx = areaX(location);
                    int ly = areaY(location);
                    sb.append("（区域 x ").append(lx).append("~").append(lx + lw)
                            .append("、y ").append(ly).append("~").append(ly + lh)
                            .append("；中心 x=").append(centerX(location)).append(", y=").append(centerY(location)).append("）");
                } else {
                    sb.append("（坐标 x=").append(areaX(location)).append(", y=").append(areaY(location)).append("）");
                }
                if (notBlank(location.getDescription())) {
                    sb.append("：").append(location.getDescription());
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
                .append("\"goal\":\"当前要去做的事（10 字以内，例如 去森林采药）\",")
                .append("\"coins_change\":0,\"combat_change\":0,\"companions\":[],\"favor_changes\":{\"角色名\":3},")
                .append("\"items_change\":{\"物品名\":{\"delta\":1,\"description\":\"6~20 字说明它是什么、有什么用\"}},")
                .append("\"shop_buy\":[{\"name\":\"旅人集市里的商品名\",\"quantity\":1,\"reason\":\"为什么买\"}],")
                .append("\"news_refs\":[],\"summary\":\"30 字以内概括这一步\"}\n")
                .append("要求：\n")
                .append("1. actions 写 1~3 条具体、有画面感的动作。\n")
                .append("2. status 必须包含体力、魔力、饥饿度（0~100 的整数）与心情（简短词语），可以再补充其它状态项；")
                .append("体力与魔力会随活动增减，饥饿度随时间上升、吃东西后下降。\n")
                .append("3. 两类数值变化项，没有变化都填 0：\n")
                .append("   · coins_change 金币（整数）：赚钱填正数、花钱填负数，不能超过身上的余额；")
                .append("但「打工、接委托、摆摊卖采集物」这类收入必须写在这里、并在 actions 里说明做了什么；\n")
                .append("   · **花钱必须与 actions 相称，金额要说得通**：一顿饭 1~3 金币、普通住宿 2~5、")
                .append("短途车马 2~6、长途车马 8~20、情报或打点 1~5、普通日用品 1~5；")
                .append("别为了「买了几颗浆果和一支火把」就写 -15，那种东西两三金币就够了；")
                .append("单次行动的非集市花费不允许超过系统上限（超了会被自动截断），身上钱少时更要省着花。\n")
                .append("   · **同一件东西不要重复付钱**：在旅人集市买的东西只写进 shop_buy（服务端按标价扣款），")
                .append("**绝对不要再把它算进 coins_change**；集市里没有、但你确实买了的小东西才写进 items_change，")
                .append("并按上面的参考价在 coins_change 里扣钱；\n")
                .append("   · combat_change 战斗力（整数，综合实力）：只有学会新魔法、得到强力装备（+1~+3）、")
                .append("受伤或力量受损（-1~-3）这类才给非 0，日常行动一律 0，且原因必须写进 actions。\n")
                .append("4. 行动必须符合当前时间与时段，作息要像真人：清晨 5~8 点起床准备，上午到下午适合赶路、做工或交易，")
                .append("傍晚 18~21 点适合吃饭、收尾、闲逛、赶路或去酒馆坐坐，23 点以后多是休息或守夜。")
                .append("**晚上 21 点之前一般不要进入整夜睡眠**——除非有明确理由：体力低于 30、受伤或生病、")
                .append("前一天熬夜没睡好、外面风雨太大无处可去；否则会显得作息不真实。")
                .append("如果确实要睡，就按「睡到第二天早上 6~8 点」来设置 next_after_minutes，不要出现「傍晚六点睡下、凌晨两点醒来」这种时间。\n")
                .append("5. companions 是角色名数组：如果这一步你与【世界里的其他居民】在同一地点相遇、交谈、")
                .append("同行或互相影响，就把他们的名字填进去（名字必须与上面列出的完全一致），没有就填 []。\n")
                .append("6. favor_changes 表示这一步你对某个角色的好感度变化，格式是 {\"角色名\": 变化量}：")
                .append("只有当这一步真的和对方发生了互动（并且已把对方写进 companions）时才填，否则填 {}；")
                .append("名字必须与【世界里的其他居民】里列出的完全一致，不要用称号、不要编造；")
                .append("变化幅度只能是 -10~+10 的整数，日常小事 ±1~3，重要事件才用 ±5~10；")
                .append("还要参考【你与其他角色的关系】里的当前好感度：接近 100 时不要再给正数，接近 -100 时不要再给负数；")
                .append("并且这一步做了什么必须写在 actions 里，不允许出现「好感变了但行动里看不出来」的情况。\n")
                .append("7. items_change 表示背包物品的变化，格式是 {\"物品名\": {\"delta\": 数量变化, \"description\": \"物品描述\"}}：")
                .append("delta 获得东西填正数（例如采到草药 2、买到干粮 1），用掉或丢失填负数（例如吃掉干粮 -1）；")
                .append("**只要是新获得的物品（delta 为正），就必须同时给出 description**：")
                .append("用 6~20 个字说明它是什么、有什么用、看起来什么样（例如「能入药的淡紫色小草」「半块硬得能砸核桃的黑面包」）；")
                .append("只是消耗或丢弃已有物品时，description 可以省略。")
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
                .append("并且间隔要和行动相符。参考：睡觉/过夜 360~600（午睡 30~90）、长途赶路 120~240、")
                .append("专注做事 60~180、吃饭逛街 15~45、短暂交谈 10~30、警戒守夜 30~90；")
                .append("不要给与行动无关的短间隔（例如「睡觉」却只隔 15 分钟）；")
                .append("**睡觉要把间隔算到第二天早上 6~8 点**（例如 22:30 睡下就是 450~570 分钟），")
                .append("不允许「睡 8 小时却在凌晨 2 点醒来」这种与时段的矛盾。");
        sb.append("\n13. news_refs 是数组：如果你这一步听说了、议论了或关注了【今日要闻】里的某条事件，")
                .append("就把那条事件的原句填进去（必须与上面列出的标题完全一致），没有就填 []；")
                .append("听说并不代表一定要参与。");
        sb.append("\n14. 背包管理：每次行动都顺便看一眼背包——能用掉的就用掉（吃掉干粮、喝掉药水等，")
                .append("让饥饿度或体力、魔力得到恢复），用不上的可以丢掉或送人（在 items_change 里写负数，")
                .append("数量减到 0 会自动从背包移除）；不要长期囤积用不上的东西，也不要一次丢光所有物资。");
        sb.append("\n15. 保持活跃、别一直待着：除非有明确理由（睡觉或休息、受伤养伤、专心研究或看守某个东西、")
                .append("被人缠住脱不开身），不要连续多次停在同一个二级地点。")
                .append("每一步尽量让位置发生变化——可以换一个新的二级地点、在地区内走动，")
                .append("也可以动身去别的地区（路远就分几步赶路，不要一步跳过去）；")
                .append("同一地区最多连着停留 2~3 次，就该考虑换个地方了。");
        sb.append("\n16. 同行是暂时的，要有自己的事做：goal 是你当前要去做的事（10 字以内），")
                .append("每一步都要维护它——做完了就换一个符合人设与当前处境的新目标。")
                .append("和别的角色一起行动最多持续 2~3 次：除非你们正在做**同一件事**（一起赶路去同一个地方、")
                .append("组队做同一件委托、并肩守夜），否则就该各自去办自己的事。")
                .append("分开要自然：可以说一句「我先去办点事」、约定回头见，或者因为目标不同而各走各的；")
                .append("不要为了黏在一起而给两个人硬编同一个目标。");
        sb.append("\n17. 要像过日子一样有收入：每天安排 1~2 次能挣到钱的活动（接委托、打零工、")
                .append("摆摊卖掉采来的东西、帮人跑腿、表演等），**单次收入 5~25 金币、整天合计 5~40 金币**，")
                .append("金额写进 coins_change 并在 actions 里说清是靠什么赚的；")
                .append("不要凭空变出钱财，也不要一天赚几百金币。")
                .append("夜里睡觉、休息、受伤时不要赚钱，也不要干活。");
        sb.append("\n18. 旅人集市（商队每天摆摊）：如果【旅人集市】里列出了商品，你可以按需购买——")
                .append("确实需要、且身上金币足够时，把要买的东西写进 shop_buy（name 必须与商品名完全一致），")
                .append("并把这笔花费在 actions 里说清楚（例如「在商队摊子上买下一包干粮」）；")
                .append("没钱、不需要、正在睡觉赶路时就不要买，shop_buy 填 []。")
                .append("买到的物品服务端会自动放进背包，所以**不要再把这些物品写进 items_change**，")
                .append("也不要再写金币变化——重复写会导致算重。");
        sb.append("\n19. 赶路要挑交通方式，别一律走路：【从这里出发的距离】里已经给出每个地方离你多少公里、")
                .append("步行要多久。距离超过几公里时，请结合世界观选一种合理的方式前往——")
                .append("参考速度 ").append(travelSpeedsText()).append("，")
                .append("可以是花钱搭商队的货车、租一匹马、坐船、雇车、用飞行坐骑或魔法捷径等等，")
                .append("具体叫什么由你按世界观命名，并在 actions 里写清是怎么去的（例如「搭上南下的盐商货车」）；")
                .append("**next_after_minutes 不能小于路上时间**，否则等于瞬移，系统会按最快方式把间隔抬到合理值。")
                .append("反过来，就在同一片区域、几十米到一两公里内走动时，步行最自然，不要动不动就雇车。");
        sb.append("\n20. 按自己的态度过日子：提示词里给了你对「实力」与「财富」的态度，请让它体现在行动里——")
                .append("想变强的人会找机会修炼、拜师、挑战强敌、攒钱买装备；")
                .append("看重钱的人会主动接委托、摆摊、做买卖；")
                .append("不在意实力或财富的人就别为了它们违背人设（可以安稳过日子、散财、拒绝危险委托）。")
                .append("战斗力提升通常需要付出代价（时间、金钱、受伤风险），不要每天都涨。");
        appendStyleExtra(sb);
        appendDraftFlow(sb);
        return sb.toString();
    }

    /** 管理员自定义文风补充（后台可编辑，可以粘贴酒馆预设里的写作基准段落） */
    private void appendStyleExtra(StringBuilder sb) {
        String extra = configService.getConfigValue("sandbox_style_extra", "");
        if (!notBlank(extra)) {
            return;
        }
        sb.append("\n【文风补充（管理员设置，优先遵守）】\n")
                .append(truncate(extra.trim(), 4000)).append("\n");
    }

    /**
     * 输出流程（三段式 / 四段式）：思考（可选）→ 草稿 → 自审 → 终稿 JSON。
     *
     * 为什么这样设计：用的多是免费接口（输出 token 不心疼），与其在容易掉字段之后补救
     * （补全/重跑要再花一次调用），不如在同一次输出里让它先想清楚、再自检一遍。
     * 服务端只认 &lt;final&gt; 里的 JSON，前面几段不会进任何业务字段、也不会出现在前台。
     */
    private void appendDraftFlow(StringBuilder sb) {
        if (!draftModeOn()) {
            return;
        }
        sb.append("\n【本次输出流程·必须严格遵守】\n");
        sb.append("请按顺序输出").append(thinkStageOn() ? "四段" : "三段").append("，一个都不能少，段外不要写任何解释。");
        sb.append("这几段只是给你自己想清楚用的，篇幅不限，但**最后一段必须是完整的 JSON**。\n");
        if (thinkStageOn()) {
            sb.append("第一段 <think>：先想清楚再动笔。写下你现在的处境（时间/位置/体力魔力/金币/背包）、");
            sb.append("有哪几种可选做法（列出 2~3 个）、你为什么选这一个、这么做的代价与风险是什么");
            sb.append("（会不会累垮、钱够不够、赶不赶得上、是否偏离目标或与记忆矛盾）。");
            sb.append("这一段允许写长，鼓励真正权衡；但不要在这里排演具体动作台词。\n");
            sb.append("写法上用角色名或第三人称来写取舍，**不要出现「我打算让她…」「我决定让他…」这类作者口吻**。\n");
        }
        sb.append("【草稿】<draft>：用 2~5 句白话写这一步具体做什么——动作顺序、");
        sb.append("会涉及哪些字段、金币与物品的收支是怎么来的。这一段不要写 JSON。\n");
        sb.append("【自审】<review>：对照下面的清单自检草稿，并写出你打算怎么改：\n");
        sb.append("· 地点与坐标是否落在【地图地点】的范围内；\n");
        sb.append("· 下一步间隔是否和做的事相符、是否符合当前时间与作息；\n");
        sb.append("· 物品是否有合理来源、名称是否为纯中文（不得出现 of / the / and 这类英文）；\n");
        sb.append("· 金币收支是否与 actions 里真正买的东西相称（参考价），有没有把集市买的东西重复算进 coins_change；\n");
        sb.append("· 是否符合人设、态度、记忆与当前目标，有没有和上一轮矛盾。\n");
        sb.append("自审的最后一行必须给出**最终决定**（后面 JSON 要照着它写）：地点与二级地点、");
        sb.append("下一步间隔多少分钟、物品变化、金币变化各是什么。**如果中途改了主意，就在这里写清新结论**，");
        sb.append("不要让 final 与这里的结论不一致。\n");
        sb.append("【终稿】<final>：给出修正后的最终 JSON，必须完整、合法、字段齐全（结构见【输出要求】），");
        sb.append("而且只有这一段是 JSON。**<final> 必须是整段回复的最后内容，后面不允许再有任何文字**。\n");
        sb.append("注意：前面几段都是给你自己用的思考过程，绝不能把里面的内容写进 final 的 actions、");
        sb.append("inner_voice 或 summary；也不要在那几段里输出 JSON 或代码块标记。\n");
    }

    /** 思考阶段（四段式的最前面一段）是否开启；默认开启，仅在三段式开启时生效 */
    private boolean thinkStageOn() {
        return !"off".equalsIgnoreCase(configService.getConfigValue("sandbox_think_stage", "on").trim());
    }

    /**
     * 往用户提示词里追加【旅人集市】。
     *
     * 省 token 的三条原则：
     *   1. 集市关闭、身上没钱、今天买够了、买得起的东西为空 → 整段不注入；
     *   2. 只列「金币价 ≤ 身上金币」的商品，最多 8 件，贵的看了也买不起；
     *   3. 描述截断到 18 字，够模型判断是什么东西就行。
     */
    private void appendShopPrompt(StringBuilder sb, SandboxCharacter c) {
        if (!"1".equals(configService.getConfigValue("sandbox_shop_enabled", "1"))) {
            return;
        }
        int coins = c.getCoins() == null ? 0 : c.getCoins();
        if (coins <= 0) {
            return;
        }
        List<SandboxShopItem> affordable = new ArrayList<>();
        for (SandboxShopItem item : latestShopBatch(c.getWorldId(), true)) {
            int price = item.getPrice() == null ? 0 : item.getPrice();
            int stock = item.getStock() == null ? 0 : item.getStock();
            if (price > 0 && price <= coins && stock > 0) {
                affordable.add(item);
            }
        }
        if (affordable.isEmpty()) {
            return;
        }
        int perDay = intConfig("sandbox_shop_buy_per_day", 2);
        if (perDay > 0) {
            Long bought = shopOrderMapper.selectCount(new LambdaQueryWrapper<SandboxShopOrder>()
                    .eq(SandboxShopOrder::getCharacterId, c.getId())
                    .eq(SandboxShopOrder::getBuyerType, "character")
                    .ge(SandboxShopOrder::getCreateTime, LocalDate.now().atStartOfDay()));
            int left = perDay - (bought == null ? 0 : bought.intValue());
            if (left <= 0) {
                // 今天已经买够了，不再注入，免得 AI 白写 shop_buy
                return;
            }
        }
        affordable.sort(Comparator.comparingInt(item -> item.getPrice() == null ? 0 : item.getPrice()));
        sb.append("\n【").append(configService.getConfigValue("sandbox_shop_title", "旅人集市"))
                .append("】商队今天摆出来的东西（你身上有 ").append(coins).append(" 金币）：\n");
        int shown = 0;
        for (SandboxShopItem item : affordable) {
            if (shown >= 8) {
                break;
            }
            sb.append("- ").append(item.getName()).append("：").append(item.getPrice()).append(" 金币");
            if (notBlank(item.getDescription())) {
                sb.append("（").append(truncate(item.getDescription(), 18)).append("）");
            }
            sb.append("，剩 ").append(item.getStock()).append(" 件\n");
            shown++;
        }
        sb.append("（想买就把商品名写进 shop_buy，钱会自动从你的 ").append(coins).append(" 金币里扣；不需要就别买）\n");
    }

    /**
     * 往用户提示词里追加【从这里出发的距离】。
     *
     * 以前只给坐标数字，AI 根本算不出"多远"：实测出现过 39 个坐标单位（约 78 km）
     * 只花 180 分钟、8 个单位也花 120 分钟这种自相矛盾。这里直接把
     * "距离多少 km + 步行多久 + 最快方式多久"算好给它，AI 只负责按结果选目的地和交通方式。
     *
     * 放在用户提示词而不是系统提示词：它随角色位置变化，放前面会破坏系统提示词的稳定前缀（上下文缓存）。
     */
    private void appendDistancePrompt(StringBuilder sb, SandboxCharacter c) {
        List<SandboxLocation> locations = locations(c.getWorldId());
        if (locations.isEmpty()) {
            return;
        }
        int x = c.getX() == null ? 50 : c.getX();
        int y = c.getY() == null ? 50 : c.getY();
        List<String[]> modes = travelSpeeds();
        String fastestName = "步行";
        double fastestKmh = 0;
        for (String[] speed : modes) {
            double modeSpeed = Convert.toDouble(speed[1], 0d);
            if (modeSpeed > fastestKmh) {
                fastestKmh = modeSpeed;
                fastestName = speed[0];
            }
        }
        if (fastestKmh <= 0) {
            fastestKmh = 4d;
        }
        double walkKmh = walkSpeedKmh();
        sb.append("\n【从这里出发的距离】（地图宽 ").append((int) mapWidthKm())
                .append(" km；交通方式参考：").append(travelSpeedsText()).append("）\n");
        // 按距离从近到远排，AI 通常更愿意去近的地方
        List<Object[]> rows = new ArrayList<>();
        for (SandboxLocation location : locations) {
            double km = kmToLocation(x, y, location);
            if (km > 900) {
                continue;
            }
            rows.add(new Object[]{location, km});
        }
        rows.sort(Comparator.comparingDouble(row -> (double) row[1]));
        for (Object[] row : rows) {
            SandboxLocation location = (SandboxLocation) row[0];
            double km = (double) row[1];
            sb.append("- ").append(location.getName()).append("：");
            if (km <= 0.05) {
                sb.append("就在这里（同一片区域内）");
            } else {
                sb.append("约 ").append(SandboxGeo.kmText(km))
                        .append("（").append(travelTimeText(km, "步行", walkKmh));
                if (fastestKmh > walkKmh && !fastestName.contains("步行")) {
                    sb.append("；").append(travelTimeText(km, fastestName, fastestKmh));
                }
                sb.append("）");
            }
            sb.append("\n");
        }
        sb.append("（距离超过几公里就别硬走：结合世界观挑个交通方式，例如花钱搭商队的货车、租马、坐船，")
                .append("把路上时间如实写进 next_after_minutes；短距离或就在附近时步行更自然）\n");
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
                .append("现在时间：").append(timeText(now)).append(sleepHint(now)).append("\n")
                .append("当前位置：").append(blankToDefault(placeText(c.getLocationName(), c.getSubLocation()), "尚未确定"))
                .append("（x=").append(c.getX() == null ? 50 : c.getX())
                .append(", y=").append(c.getY() == null ? 50 : c.getY()).append("）\n")
                .append("身上金币：").append(c.getCoins() == null ? 0 : c.getCoins())
                .append(" 枚（本次非集市花费请不要超过 ")
                .append(SandboxSpendLimit.maxSpend(c.getCoins() == null ? 0 : c.getCoins(),
                        intConfig("sandbox_max_spend_per_act", 10)))
                .append(" 金币）\n")
                .append("战斗力：").append(c.getCombatPower() == null ? COMBAT_POWER_DEFAULT : c.getCombatPower())
                .append("（只有真正影响实力的事情才需要改：学会新魔法、得到强力装备、受伤等；日常行动填 0）\n")
                .append("当前目标：").append(blankToDefault(c.getGoal(), "（还没有明确目标，可以自己定一个要去做的事）")).append("\n");
        // 对实力 / 财富的态度：让"战斗力"与"金币"的变化带着性格走，而不是谁都拼命变强、拼命攒钱
        if (notBlank(c.getPowerView()) || notBlank(c.getWealthView())) {
            sb.append("你的态度：");
            if (notBlank(c.getPowerView())) {
                sb.append("对实力——").append(truncate(c.getPowerView(), 60));
            }
            if (notBlank(c.getWealthView())) {
                if (notBlank(c.getPowerView())) {
                    sb.append("；");
                }
                sb.append("对财富——").append(truncate(c.getWealthView(), 60));
            }
            sb.append("（按这个态度决定要不要变强、要不要为钱奔波，也会影响你的心情）\n");
        }
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
        // 旅人集市：只列「今天买得起」的商品，控制 token（名称 + 金币价 + 库存 + 极短描述）
        appendShopPrompt(sb, c);
        // 各地点的实际距离（km）：坐标对 AI 没有意义，给它"多远、要多久"才用得上
        appendDistancePrompt(sb, c);
        // 其他居民（含此刻位置）：这部分每次都可能变，放在用户提示词里，让系统提示词保持稳定前缀
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
                // 实际距离：AI 判断"要不要去找他 / 算不算相遇"全靠这个，光给坐标它算不明白
                double km = kmBetween(c.getX() == null ? 50 : c.getX(), c.getY() == null ? 50 : c.getY(),
                        other.getX() == null ? 50 : other.getX(), other.getY() == null ? 50 : other.getY());
                boolean sameSpot = sameSpot(c.getLocationName(), c.getSubLocation(),
                        other.getLocationName(), other.getSubLocation());
                // 不管是不是同一个区域，都给实际距离：区域大的时候，同区域也可能相距几十公里
                sb.append("，距你约 ").append(SandboxGeo.kmText(km))
                        .append("（").append(travelTimeText(km, "步行", walkSpeedKmh())).append("）");
                if (sameSpot) {
                    sb.append("，就在同一个地方");
                }
                // 明确告诉 AI 这一步能不能相遇：否则它会写出"碰了面"，而同行/好感度会被服务端丢掉
                double socialMaxKm = intConfig("sandbox_social_max_km", 30);
                if (sameSpot || socialMaxKm <= 0 || km <= socialMaxKm) {
                    sb.append("，这一步可以相遇、一起行动");
                } else if (km <= socialMaxKm * 2) {
                    sb.append("，稍远：这一步赶过去只能赶路，很难同时做别的事");
                } else {
                    sb.append("，太远了，这一步不可能相遇");
                }
                sb.append("\n");
            }
        }

        if (!memories.isEmpty()) {
            List<SandboxMemory> ordered = new ArrayList<>(memories);
            ordered.sort((a, b) -> {
                LocalDate da = a.getMemoryDate() == null ? LocalDate.MIN : a.getMemoryDate();
                LocalDate db = b.getMemoryDate() == null ? LocalDate.MIN : b.getMemoryDate();
                return da.compareTo(db);
            });
            // 记忆两级压缩：最近 2 天保留完整故事，更早的只留一句模糊印象
            // （既省 token，也符合"久远的记忆会褪色"的设定）
            int keepRecent = 2;
            int olderStart = Math.max(0, ordered.size() - keepRecent);
            sb.append("\n【最近的记忆】按时间从早到晚，这是你对过去几天的印象：\n");
            if (olderStart > 0) {
                SandboxMemory lastOlder = ordered.get(olderStart - 1);
                sb.append("- 更早的几天（印象已经模糊）：")
                        .append(truncate(blankToDefault(lastOlder.getSummary(), "记不清了"), 40))
                        .append("……\n");
            }
            for (int i = olderStart; i < ordered.size(); i++) {
                SandboxMemory memory = ordered.get(i);
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
        // 活跃度引导：连续多次停在同一个地区时，明确提醒该动一动了（比笼统要求"多走动"有效）
        int stayStreak = stayStreak(recent);
        if (stayStreak >= 3) {
            sb.append("\n【别一直待着】你已经连续 ").append(stayStreak).append(" 次行动都在「")
                    .append(blankToDefault(recent.isEmpty() ? null : recent.get(0).getLocationName(), "这一带"))
                    .append("」了。\n");
            sb.append("除非有明确理由（正在睡觉或养伤、专心研究或看守某个东西、被人缠住脱不开身），")
                    .append("这一步请换个地方：可以换一个二级地点，也可以动身去别的地区（路远就分几步赶路）。\n");
        }
        // 同行统计：和同一个人连续一起行动太久时点名提醒（和「别一直待着」同一套思路）
        appendTogethernessHint(sb, c, recent, companions);
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
        // 异世界的礼物：只说「收到来自异世界的礼物」，不带赠送者名字，避免角色记忆错乱
        List<SandboxGift> gifts = recentGifts(c.getId(), c.getLastRunTime(), 5);
        if (!gifts.isEmpty()) {
            sb.append("\n【旅人的馈赠】你收到了来自异世界的礼物：\n");
            List<SandboxGift> orderedGifts = new ArrayList<>(gifts);
            Collections.reverse(orderedGifts);
            for (SandboxGift gift : orderedGifts) {
                sb.append("- 收到来自异世界的礼物「").append(gift.getItemName()).append("」");
                if (notBlank(gift.getItemDescription())) {
                    sb.append("：").append(truncate(gift.getItemDescription(), 60));
                }
                sb.append("（已经放进你的背包）\n");
            }
            sb.append("（可以自然地收下、提起或使用它，但不要追问送礼的人是谁）\n");
        }
        // 最近行动放到最末尾：离生成点越近，模型越会参考刚刚发生的事
        sb.append("\n【最近行动】按时间从新到旧排列，**第一条就是刚刚发生的**，越往下越早：\n");
        if (recent.isEmpty()) {
            sb.append("（这是角色在这个世界的第一步，可以自由展开）\n");
        } else {
            for (SandboxAct act : recent) {
                sb.append("- ").append(act.getCreateTime() == null ? "" : act.getCreateTime().format(DATE_TIME_FORMATTER))
                        .append(" 在").append(blankToDefault(placeText(act.getLocationName(), act.getSubLocation()), "某处")).append("：")
                        .append(blankToDefault(act.getSummary(), blankToDefault(act.getActions(), "")))
                        .append("\n");
            }
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

    /**
     * 从最近一次行动往前数，连续在同一个地区待了多少次。
     * 用来在提示词里提醒 AI「你该动一动了」——比笼统地要求"多走动"有效得多。
     */
    private int stayStreak(List<SandboxAct> recent) {
        return stayStreakReal(recent);
    }

    /**
     * 同行统计与冷却提示：
     *   1. 和同一个角色连续一起行动 ≥3 次 → 点名提醒"该各自去办自己的事了"；
     *   2. 上一轮刚分开、这一轮又碰面 → 提示"你们刚分开，各自在忙自己的事"，避免分开又立刻重逢。
     * 数据直接来自行动记录的 companions 字段，不需要新表。
     */
    private void appendTogethernessHint(StringBuilder sb, SandboxCharacter c, List<SandboxAct> recent,
                                        List<SandboxCharacter> companions) {
        if (companions.isEmpty() || recent.isEmpty()) {
            return;
        }
        // 连续同行次数：从最近一条往前数，看某个名字是否一直出现
        Map<String, Integer> streaks = new LinkedHashMap<>();
        for (SandboxAct act : recent) {
            String joined = act.getCompanions();
            for (SandboxCharacter other : companions) {
                boolean with = joined != null && joined.contains(other.getName());
                if (!with) {
                    // 这条没在一起：streak 到此结束（没记录过就是 0）
                    streaks.putIfAbsent(other.getName(), 0);
                    continue;
                }
                int current = streaks.getOrDefault(other.getName(), -1);
                if (current < 0) {
                    streaks.put(other.getName(), 1);
                }
            }
        }
        // 上面只统计了"最近一段"，这里重新用更直观的方式算：从最近一条开始连续包含
        List<String> longTogether = new ArrayList<>();
        List<String> justParted = new ArrayList<>();
        for (SandboxCharacter other : companions) {
            int streak = 0;
            boolean broken = false;
            for (SandboxAct act : recent) {
                String joined = act.getCompanions();
                boolean with = joined != null && joined.contains(other.getName());
                if (with && !broken) {
                    streak++;
                } else {
                    broken = true;
                }
            }
            if (streak >= 3) {
                longTogether.add(other.getName() + "（" + streak + " 次）");
            } else if (streak == 0 && recentlyTogether(recent, other.getName())) {
                // 最近这条没在一起，但更早的两条里有 → 属于"刚分开"
                justParted.add(other.getName());
            }
        }
        if (!longTogether.isEmpty()) {
            sb.append("\n【别总黏在一起】你和 ").append(String.join("、", longTogether))
                    .append(" 已经连续一起行动很久了。\n");
            sb.append("除非你们正在做同一件事（一起赶路去同一个地方、组队做同一件委托、并肩守夜），")
                    .append("这一步请分开各自去办自己的事——可以约定回头见，也可以因为目标不同而各走各的；")
                    .append("不要为了待在一起给两个人硬编同一个目标。\n");
        }
        if (!justParted.isEmpty()) {
            sb.append("\n【刚分开不久】你和 ").append(String.join("、", justParted))
                    .append(" 上一轮刚分开，各自正在忙自己的事。\n");
            sb.append("除非有正当理由（正好顺路、临时求助、约好了见面），否则不要立刻又凑到一起。\n");
        }
    }

    /** 更早的两条行动里是否有过同行（用于判断"刚分开"） */
    private boolean recentlyTogether(List<SandboxAct> recent, String name) {
        int checked = 0;
        for (SandboxAct act : recent) {
            checked++;
            if (checked > 3) {
                break;
            }
            String joined = act.getCompanions();
            if (joined != null && joined.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private int stayStreakReal(List<SandboxAct> recent) {
        int streak = 0;
        String place = null;
        for (SandboxAct act : recent) {
            String current = act.getLocationName();
            if (!notBlank(current)) {
                break;
            }
            if (place == null) {
                place = current;
                streak = 1;
                continue;
            }
            if (!place.equals(current)) {
                break;
            }
            streak++;
        }
        return streak;
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
                relation.setWorldId(resolveWorldId(relation.getWorldId(), relation.getCharacterId()));
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
        // 物品名规范化（中英混排统一成中文），避免同一种东西因为名字不同各占一格
        item.setName(SandboxItemName.normalize(item.getName()));
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
                item.setWorldId(resolveWorldId(item.getWorldId(), item.getCharacterId()));
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
    public List<SandboxNews> todayNews(Long worldId) {
        return newsMapper.selectList(new LambdaQueryWrapper<SandboxNews>()
                .eq(worldId != null, SandboxNews::getWorldId, worldId)
                .eq(SandboxNews::getNewsDate, LocalDate.now())
                .eq(SandboxNews::getEnabled, 1)
                .orderByDesc(SandboxNews::getPinned)
                .orderByDesc(SandboxNews::getLevel)
                .orderByDesc(SandboxNews::getId)
                .last("limit 10"));
    }

    @Override
    public PageResult<SandboxNews> newsPage(String date, long page, long size) {
        return newsPage(date, page, size, null);
    }

    @Override
    public PageResult<SandboxNews> newsPage(String date, long page, long size, Long worldId) {
        LambdaQueryWrapper<SandboxNews> wrapper = new LambdaQueryWrapper<SandboxNews>()
                // 多世界：纪闻列表也要按世界过滤
                .eq(worldId != null, SandboxNews::getWorldId, worldId)
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
            news.setWorldId(worldId(news.getWorldId()));
        }
        if (news.getSource() == null || news.getSource().trim().isEmpty()) {
            news.setSource("admin");
        }
        // 补坐标：优先匹配地图地点的区域中心
        if (news.getX() == null || news.getY() == null) {
            SandboxLocation matched = matchLocation(locations(resolveWorldId(news.getWorldId(), null)), news.getLocationName());
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
    public int generateNews(Integer count, Long providerId, String model, Long worldId) {
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

        SandboxWorld world = world(worldId);
        List<SandboxLocation> locations = locations(world.getId());
        // 最近的世界动向：只取少量角色行动概括，作为氛围参考
        List<String> recentMoves = new ArrayList<>();
        for (SandboxCharacter character : characters(world.getId())) {
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
        for (SandboxNews news : todayNews(world.getId())) {
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
    public void autoGenerateNews(Long worldId) {
        if (!"1".equals(configService.getConfigValue("sandbox_news_enabled", "1"))) {
            return;
        }
        boolean preset = AuditContext.isSet();
        if (!preset) {
            AuditContext.schedule("沙盒·自动生成旅人纪闻");
        }
        try {
            int created = generateNews(null, null, null, worldId);
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
        return applyItemChanges(character, itemObj, Collections.emptySet());
    }

    /**
     * 应用 AI 给的物品变化。
     *
     * @param ignoreNames 本次已经在集市买到的物品名：这些上面已经入过包，这里跳过，
     *                    避免 AI 把「买到的物品」同时写进 items_change 造成数量翻倍
     */
    private String applyItemChanges(SandboxCharacter character, Object itemObj, Set<String> ignoreNames) {
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
            // 物品名规范化：模型偶尔中英混排（「晨雾森林 of 野浆果」），统一成中文后才能和已有物品合并
            String name = SandboxItemName.normalize(key);
            if (name.isEmpty() || name.length() > 60) {
                continue;
            }
            if (ignoreNames.contains(name)) {
                continue;
            }
            // 兼容新旧两种写法：{"物品名": 2} 与 {"物品名": {"delta": 2, "description": "..."}}
            int delta = SandboxOutputRepair.itemDelta(obj.get(key));
            String aiDescription = SandboxOutputRepair.itemDescription(obj.get(key));
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
                // 物品描述来自 AI（提示词里要求新获得的物品必须给描述）
                created.setDescription(truncate(aiDescription, 300));
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
                LambdaUpdateWrapper<SandboxItem> update = new LambdaUpdateWrapper<SandboxItem>()
                        .eq(SandboxItem::getId, item.getId())
                        .set(SandboxItem::getQuantity, quantity)
                        .set(SandboxItem::getUpdateTime, now);
                // 老物品之前没有描述时，用 AI 这次的描述补上（不覆盖管理员已经写好的说明）
                if (notBlank(aiDescription) && !notBlank(item.getDescription())) {
                    update.set(SandboxItem::getDescription, truncate(aiDescription, 300));
                    item.setDescription(truncate(aiDescription, 300));
                }
                itemMapper.update(null, update);
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
                memory.setWorldId(resolveWorldId(memory.getWorldId(), memory.getCharacterId()));
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
                // 定时总结只处理「运行中」的世界，避免已停止的世界白烧 AI 额度；
                // 管理员手动补生成时不做这个限制（历史世界也能补）
                if (schedule && !isWorldRunning(character.getWorldId())) {
                    continue;
                }
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
                ? sandboxSystemProvider(character)
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
                                List<SandboxCharacter> companions, List<String> issues) {
        if (raw == null || raw.trim().isEmpty()) {
            return raw;
        }
        try {
            StringBuilder sys = new StringBuilder();
            // 提示词要点：以前这里写得太"照抄"，模型连「晨雾森林 of 野浆果」这种错名、以及不合理的
            // 花费都会原样复制；现在改成"除下列必须纠正的点外，其余一个字都不许改"。
            sys.append("你是一个 JSON 校验器：只做校验与修正，不改写故事内容，也不新增剧情。\n")
                    .append("输入是某个角色扮演输出的 JSON。请**原样保留输入中的每一个字段**（键名一个都不能少），")
                    .append("然后输出完整的 JSON：\n")
                    .append("- 不允许删除、省略、改名字段，特别是 sub_location、items_change、news_refs、")
                    .append("shop_buy、next_after_minutes、next_after_reason 这些字段必须原样保留；\n")
                    .append("- 不允许新增输入里没有的字段；\n")
                    .append("- 不允许改写或缩写 actions、inner_voice、summary 的文字内容（错别字也保持原样）；\n")
                    .append("- 即使某个字段看起来多余，也要照抄，不要自作主张删掉。\n")
                    .append("**必须纠正的地方（只有这些可以改，别的地方一个字都不许动）**：\n")
                    .append("A. 物品名必须是纯中文：出现 of / and / the 这类英文连接词，要改成中文")
                    .append("（of→的、and→和、the 直接去掉，例如「晨雾森林 of 野浆果」改成「晨雾森林的野浆果」）；\n")
                    .append("B. coins_change 必须与 actions 里真正买的东西/服务相称，参考价：一顿饭 1~3、")
                    .append("普通住宿 2~5、短途车马 2~6、长途车马 8~20、情报或打点 1~5、日用品 1~5；")
                    .append("如果 actions 里买的东西明显不值这个价（例如两颗浆果和一支火把却写了 -15），")
                    .append("就改成与参考价相符的金额；没写清用途的大额支出直接调小或改成 0；\n")
                    .append("C. 在旅人集市买的东西只能出现在 shop_buy 里，不能同时算进 coins_change（否则等于重复付款）；\n")
                    .append("校验规则：\n")
                    .append("1. location 必须是【可用地点】里的名字，x/y 为 0~100 的整数；\n")
                    .append("2. actions 至少保留 1 条，条目内容不要改动；\n")
                    .append("3. status 里体力、魔力、饥饿度必须是 0~100 的整数，心情等其它键保留原文；\n")
                    .append("4. coins_change 必须与 actions 描述的收支一致，没有花钱或赚钱就改成 0；\n")
                    .append("5. companions 只能填【可用角色名】里的名字；favor_changes 的键也只能是这些名字，")
                    .append("并且必须与 companions 和 actions 的描述相符，幅度限制在 -10~+10，")
                    .append("还要保证加上当前好感度后仍在 -100~100 之内；\n")
                    .append("6. items_change 的键是物品名、值是 {\"delta\": ±9 以内的整数, \"description\": \"物品描述\"}；")
                    .append("新获得的物品（delta 为正）必须保留原有的 description，缺失时补一句 6~20 字的说明；")
                    .append("没有变化就保留空对象；\n")
                    .append("7. next_after_minutes 必须是大于 0 的整数，next_after_reason 必须是 2~6 个字，")
                    .append("这两个字段绝对不能删除；\n")
                    .append("8. 不要编造任何角色名或地点名。\n")
                    .append("9. shop_buy 是集市购物清单，商品名与数量一律照抄，不要新增、不要改写、不要清空；\n")
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
            // 把系统检测到的问题点名列出：比让模型自己找有效得多
            if (issues != null && !issues.isEmpty()) {
                user.append("\n【系统检测到的问题（必须逐条修正）】\n");
                for (String issue : issues) {
                    user.append("- ").append(issue).append("\n");
                }
            }
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
    /** 取角色上次行动之后收到的礼物（只用于提示词，最多 limit 条） */
    private List<SandboxGift> recentGifts(Long characterId, LocalDateTime after, int limit) {
        return giftMapper.selectList(new LambdaQueryWrapper<SandboxGift>()
                .eq(SandboxGift::getCharacterId, characterId)
                .gt(after != null, SandboxGift::getCreateTime, after)
                .orderByDesc(SandboxGift::getCreateTime)
                .orderByDesc(SandboxGift::getId)
                .last("limit " + limit));
    }

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
        // 解析交给 SandboxReplyParser：它会优先取 <final> 里的 JSON，
        // 也兼容"模型把 JSON 包在代码块里 / 前后夹了草稿自审 / 直接就是纯 JSON"这些情况
        return SandboxReplyParser.parse(raw);
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
            // 按「点到区域的距离」找最近：以前比的是左上角坐标，多边形化以后会明显跑偏
            double distance = distanceToLocation(location, x, y);
            if (distance < best) {
                best = distance;
                nearest = location;
            }
        }
        return nearest;
    }

    // ============================== 地点区域工具 ==============================

    /**
     * 取地点的判定多边形：
     *   1. 有 polygon 字段（后台套索画的）→ 用多边形；
     *   2. 没有 polygon 但有宽高 → 用矩形的 4 个角（老数据等价迁移，行为与以前完全一致）；
     *   3. 宽高都是 0（单点地点）→ 返回 null，由调用方按「点 + 容差」处理。
     */
    private List<double[]> polygonOf(SandboxLocation location) {
        if (location == null) {
            return null;
        }
        if (notBlank(location.getPolygon())) {
            List<double[]> polygon = SandboxGeo.parse(location.getPolygon());
            if (polygon.size() >= 3) {
                return polygon;
            }
        }
        if (areaWidth(location) > 0 && areaHeight(location) > 0) {
            return SandboxGeo.rectPolygon(areaX(location), areaY(location), areaWidth(location), areaHeight(location));
        }
        return null;
    }

    /** 地点区域面积（用于「多条命中取更具体的那条」） */
    private double areaOf(SandboxLocation location) {
        List<double[]> polygon = polygonOf(location);
        return polygon == null ? 0d : SandboxGeo.area(polygon);
    }

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

    /**
     * 地点中心：多边形用面积加权形心；形心落在区域外（C 形、回旋镖形）时退回内部点。
     * 这个点会被用在提示词、纪闻坐标、前台标签位置上，必须保证在区域内。
     */
    private double[] centerOf(SandboxLocation location) {
        List<double[]> polygon = polygonOf(location);
        if (polygon != null) {
            return SandboxGeo.labelPoint(polygon);
        }
        return new double[]{
                areaX(location) + Math.max(0, areaWidth(location) / 2d),
                areaY(location) + Math.max(0, areaHeight(location) / 2d)
        };
    }

    /** 地点中心 X */
    private int centerX(SandboxLocation location) {
        return clamp((int) Math.round(centerOf(location)[0]));
    }

    /** 地点中心 Y */
    private int centerY(SandboxLocation location) {
        return clamp((int) Math.round(centerOf(location)[1]));
    }

    /** 坐标是否落在地点区域内（单点地点允许 2% 的容差） */
    private boolean inLocationArea(SandboxLocation location, int x, int y) {
        List<double[]> polygon = polygonOf(location);
        if (polygon != null) {
            return SandboxGeo.contains(polygon, x, y);
        }
        return Math.abs(areaX(location) - x) <= 2 && Math.abs(areaY(location) - y) <= 2;
    }

    /**
     * 坐标落在哪个地点区域内，没有命中返回 null。
     * 多条命中时取面积最小的那条：区域允许嵌套（国家里放城市），内层更具体。
     */
    private SandboxLocation locationAtPoint(List<SandboxLocation> locations, int x, int y) {
        SandboxLocation best = null;
        double bestArea = Double.MAX_VALUE;
        for (SandboxLocation location : locations) {
            if (!inLocationArea(location, x, y)) {
                continue;
            }
            double area = areaOf(location);
            if (area < bestArea) {
                bestArea = area;
                best = location;
            }
        }
        return best;
    }

    /**
     * 把坐标修正到地点区域内：
     *   - 已经在区域内：原样返回；
     *   - 在区域外（AI 报的地点名和坐标对不上）：投影到最近的边界上，
     *     保持「角色一定在自己所属的区域内」这个语义；
     *   - 单点地点：不处理。
     */
    private int[] clampToArea(SandboxLocation location, int x, int y) {
        List<double[]> polygon = polygonOf(location);
        if (polygon != null) {
            if (SandboxGeo.contains(polygon, x, y)) {
                return new int[]{x, y};
            }
            double[] projected = SandboxGeo.projectToPolygon(polygon, x, y);
            return new int[]{
                    clamp((int) Math.round(projected[0])),
                    clamp((int) Math.round(projected[1]))
            };
        }
        return new int[]{x, y};
    }

    /** 点到地点区域的距离：在区域内为 0，用于「没命中任何区域时找最近的地点」 */
    private double distanceToLocation(SandboxLocation location, int x, int y) {
        List<double[]> polygon = polygonOf(location);
        if (polygon != null) {
            return SandboxGeo.distanceToPolygon(polygon, x, y);
        }
        return Math.hypot(areaX(location) - x, areaY(location) - y);
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

    // ============================== 距离与交通方式（km） ==============================

    /** 地图宽度（km）：横向 100 个坐标单位对应多少公里，默认 200 km */
    private double mapWidthKm() {
        int width = intConfig("sandbox_km_map_width", 200);
        return width <= 0 ? 200d : width;
    }

    /** 交通方式与速度（km/h），形如「步行:4,骑乘:20」；配置被改坏时兜底步行 */
    private List<String[]> travelSpeeds() {
        String text = configService.getConfigValue("sandbox_travel_speeds", DEFAULT_TRAVEL_SPEEDS);
        List<String[]> list = new ArrayList<>();
        for (String part : text.split("[,，]")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            String[] pair = item.split("[:：]");
            String name = pair[0].trim();
            double speed = pair.length > 1 ? Convert.toDouble(pair[1].trim(), 0d) : 0d;
            if (!name.isEmpty() && speed > 0) {
                list.add(new String[]{name, String.valueOf(speed)});
            }
        }
        if (list.isEmpty()) {
            list.add(new String[]{"步行", "4"});
        }
        return list;
    }

    /** 最快交通方式的速度（km/h）：赶路时间下限按它算，别把有马有船的世界当成只能走路 */
    private double fastestSpeedKmh() {
        double fastest = 0;
        for (String[] speed : travelSpeeds()) {
            fastest = Math.max(fastest, Convert.toDouble(speed[1], 0d));
        }
        return fastest <= 0 ? 4d : fastest;
    }

    /** 「步行 4 km/h、骑乘 20 km/h」这样一段文字，直接写进提示词给 AI 参考 */
    private String travelSpeedsText() {
        List<String> parts = new ArrayList<>();
        for (String[] speed : travelSpeeds()) {
            parts.add(speed[0] + " " + speed[1] + " km/h");
        }
        return String.join("、", parts);
    }

    /** 步行速度（km/h）：配置里的「步行」，没配就按 4 km/h */
    private double walkSpeedKmh() {
        for (String[] speed : travelSpeeds()) {
            if ("步行".equals(speed[0])) {
                return Convert.toDouble(speed[1], 4d);
            }
        }
        return 4d;
    }

    /** 两点之间的实际距离（km），内部已按地图 16:9 折算纵轴 */
    private double kmBetween(int x1, int y1, int x2, int y2) {
        return SandboxGeo.kmPointToPoint(x1, y1, x2, y2, mapWidthKm());
    }

    /** 某个坐标到地点区域的距离（km）：已经在这个地点范围内时为 0 */
    private double kmToLocation(int x, int y, SandboxLocation location) {
        List<double[]> polygon = polygonOf(location);
        if (polygon == null) {
            return kmBetween(x, y, centerX(location), centerY(location));
        }
        return SandboxGeo.kmToPolygon(polygon, x, y, mapWidthKm());
    }

    /** 走完这段距离按最快交通方式至少要多少分钟（服务端兜底：不允许 390 km 只走 3 小时） */
    private int minTravelMinutes(double km) {
        return SandboxGeo.travelMinutes(km, fastestSpeedKmh());
    }

    /**
     * 系统级调用（定时行动、记忆总结）用哪个服务商。
     *
     * 优先用后台「世界与地图 → 系统服务商」里配置的那个；没配（或配的服务商被删了）就沿用原来的规则：
     * 角色绑定的服务商如果是系统服务商就用它，否则回落到默认服务商。
     */
    private AiProvider sandboxSystemProvider(SandboxCharacter character) {
        Long configured = Convert.toLong(configService.getConfigValue("sandbox_system_provider_id", ""), null);
        if (configured != null) {
            try {
                return aiProviderService.resolveSystemProvider(configured);
            } catch (Exception e) {
                log.warn("系统服务商 id={} 不可用，回落到自动规则：{}", configured, e.getMessage());
            }
        }
        return aiProviderService.resolveSystemProvider(character.getProviderId());
    }

    /**
     * 这一步"能不能和别人互动"的角色清单。
     *
     * 规则（一级区域名相同**不再**算数：区域最大能有 60 多公里，两端的人其实离得很远）：
     *   1. 同一个"具体地点"（一级地点 + 二级地点都相同）→ 直接算相遇；
     *   2. 其余按实际距离判断，默认阈值 30 km（后台可改成 0 = 不限制）。
     * 远在天边的人仍会出现在提示词里（AI 可以"听说"），但不允许写进 companions / favor_changes，
     * 否则会出现"隔着一个王国互相请喝酒还涨好感度"。
     */
    private List<SandboxCharacter> nearbyCompanions(SandboxCharacter self, List<SandboxCharacter> companions,
                                                    int x, int y, String locationName) {
        double maxKm = intConfig("sandbox_social_max_km", 30);
        List<SandboxCharacter> list = new ArrayList<>();
        for (SandboxCharacter other : companions) {
            if (other.getId() != null && other.getId().equals(self.getId())) {
                continue;
            }
            // 同一个二级地点（AI 自创的小地点）才算"就在一起"，比一级区域精确得多
            if (sameSpot(locationName, self.getSubLocation(), other.getLocationName(), other.getSubLocation())) {
                list.add(other);
                continue;
            }
            double km = kmBetween(x, y, other.getX() == null ? 50 : other.getX(),
                    other.getY() == null ? 50 : other.getY());
            if (maxKm <= 0 || km <= maxKm) {
                list.add(other);
            }
        }
        return list;
    }

    /**
     * 是否在同一个"具体地点"：一级地点与二级地点都相同。
     * 只用一级区域判断是不准的——像「霜白王国」横跨 50 km，两端的人根本见不到面；
     * 而二级地点是 AI 当场自创的小地方（「城郊的碎石分岔口」），相同就意味着真的在同一处。
     */
    private boolean sameSpot(String locationA, String subA, String locationB, String subB) {
        if (!notBlank(locationA) || !notBlank(subA)) {
            return false;
        }
        return locationA.equals(locationB) && subA.equals(subB);
    }

    /**
     * 赶路时间下限：AI 说从 A 到 B 只花 30 分钟，但两地相距 80 km 时，
     * 把间隔抬到「距离 ÷ 最快交通方式」所需的时间（正常情况提示词已经让它自己算好了，这里只是兜底）。
     */
    private Integer applyTravelFloor(SandboxCharacter character, int x, int y, String locationName, Integer aiMinutes) {
        if (aiMinutes == null || aiMinutes <= 0) {
            return aiMinutes;
        }
        boolean moved = locationName != null && !locationName.equals(character.getLocationName());
        double km = kmBetween(character.getX() == null ? 50 : character.getX(),
                character.getY() == null ? 50 : character.getY(), x, y);
        // 没换地区、又只是就近走动，就不用管
        if (!moved && km < 5) {
            return aiMinutes;
        }
        int floor = minTravelMinutes(km);
        if (floor > aiMinutes) {
            log.info("沙盒角色「{}」这一步移动约 {}，AI 只给了 {} 分钟，按最快交通方式抬到 {} 分钟",
                    character.getName(), SandboxGeo.kmText(km), aiMinutes, floor);
            return floor;
        }
        return aiMinutes;
    }

    /** 「步行约 5 小时」这种时间描述，避免提示词里出现 320 分钟这类不好读的数字 */
    private String travelTimeText(double km, String mode, double speedKmh) {
        if (km <= 0 || speedKmh <= 0) {
            return "";
        }
        return mode + "约 " + SandboxGeo.minutesText(SandboxGeo.travelMinutes(km, speedKmh));
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

    private Long worldId(Long preferred) {
        if (preferred != null) {
            return preferred;
        }
        SandboxWorld w = world(null);
        return w.getId() == null ? 1L : w.getId();
    }

    // ============================== 旅人集市 ==============================

    /** 各品质对应的价格区间（金币），防止 AI 给出「传说品质卖 1 金币」这种离谱定价。
     *  汇率默认 1 积分 = 1 金币，所以这份区间同时也就是前台用户要付的积分量级。 */
    private static final int[][] SHOP_PRICE_RANGE = {{1, 5}, {3, 10}, {6, 18}, {12, 30}, {25, 50}};
    /** 单件商品的库存上限 */
    private static final int SHOP_STOCK_MAX = 5;

    @Override
    public List<SandboxShopItem> shopItems(Long worldId) {
        Long wid = worldId(worldId);
        List<SandboxShopItem> list = latestShopBatch(wid, true);
        // 每件商品带上赠送记录：前台点开商品时展示「谁送给了谁」
        for (SandboxShopItem item : list) {
            item.setOrders(shopOrderMapper.selectList(new LambdaQueryWrapper<SandboxShopOrder>()
                    .eq(SandboxShopOrder::getItemId, item.getId())
                    .orderByDesc(SandboxShopOrder::getId)
                    .last("limit 12")));
        }
        return list;
    }

    @Override
    public List<SandboxShopItem> shopItemsForAdmin(Long worldId) {
        return latestShopBatch(worldId(worldId), false);
    }

    /** 取最新一批商品；onlyEnabled=true 时过滤掉管理员下架的 */
    private List<SandboxShopItem> latestShopBatch(Long worldId, boolean onlyEnabled) {
        SandboxShopItem latest = shopItemMapper.selectOne(new LambdaQueryWrapper<SandboxShopItem>()
                .eq(SandboxShopItem::getWorldId, worldId)
                .orderByDesc(SandboxShopItem::getBatchTime)
                .orderByDesc(SandboxShopItem::getId)
                .last("limit 1"));
        if (latest == null) {
            return new ArrayList<>();
        }
        return shopItemMapper.selectList(new LambdaQueryWrapper<SandboxShopItem>()
                .eq(SandboxShopItem::getWorldId, worldId)
                .eq(SandboxShopItem::getBatchTime, latest.getBatchTime())
                .eq(onlyEnabled, SandboxShopItem::getEnabled, 1)
                .orderByDesc(SandboxShopItem::getPinned)
                .orderByAsc(SandboxShopItem::getId));
    }

    @Override
    public SandboxShopItem saveShopItem(SandboxShopItem item) {
        if (item.getName() == null || item.getName().trim().isEmpty()) {
            throw new BusinessException("商品名称不能为空");
        }
        // 集市商品名也做同样的规范化，保证"背包名 = 商品名 = 购买记录名"
        item.setName(SandboxItemName.normalize(item.getName()));
        if (item.getRarity() == null || item.getRarity() < 1 || item.getRarity() > 5) {
            item.setRarity(1);
        }
        if (item.getPrice() == null || item.getPrice() < 0) {
            item.setPrice(1);
        }
        int stock = item.getStock() == null ? 1 : Math.max(0, item.getStock());
        item.setStock(stock);
        if (item.getTotalStock() == null || item.getTotalStock() < stock) {
            item.setTotalStock(stock);
        }
        if (item.getEnabled() == null) {
            item.setEnabled(1);
        }
        if (item.getPinned() == null) {
            item.setPinned(0);
        }
        if (item.getSource() == null) {
            item.setSource("admin");
        }
        if (item.getId() == null) {
            if (item.getWorldId() == null) {
                item.setWorldId(worldId(null));
            }
            // 管理员手动上架的商品挂到当前最新批次；还没有批次就用现在的时间开一批
            if (item.getBatchTime() == null) {
                SandboxShopItem latest = shopItemMapper.selectOne(new LambdaQueryWrapper<SandboxShopItem>()
                        .eq(SandboxShopItem::getWorldId, item.getWorldId())
                        .orderByDesc(SandboxShopItem::getBatchTime)
                        .last("limit 1"));
                item.setBatchTime(latest == null || latest.getBatchTime() == null
                        ? LocalDateTime.now() : latest.getBatchTime());
            }
            item.setCreateTime(LocalDateTime.now());
            shopItemMapper.insert(item);
        } else {
            // 编辑时不改批次时间
            shopItemMapper.updateById(item);
        }
        return item;
    }

    @Override
    public void deleteShopItem(Long id) {
        shopItemMapper.deleteById(id);
    }

    /**
     * 生成一批新商品（AI）。
     * 世界观是必需输入；另外带上地图地点、当天纪闻与角色最近的行动做氛围参考，
     * 这样能出现「下雪了卖暖手炉」这类应景商品。商场内容不会反向写进角色提示词。
     */
    @Override
    public int generateShopItems(Integer count, Long providerId, String model, Long worldId) {
        int size = Math.max(1, Math.min(10, count == null ? intConfig("sandbox_shop_per_generate", 3) : count));
        Long wid = worldId(worldId);
        SandboxWorld world = world(wid);
        if (world.getId() == null) {
            throw new BusinessException("请先创建沙盒世界");
        }
        Long provider = providerId != null ? providerId
                : Convert.toLong(configService.getConfigValue("sandbox_shop_provider_id", ""), null);
        String useModel = model;
        if (useModel == null || useModel.trim().isEmpty()) {
            useModel = configService.getConfigValue("sandbox_shop_model", "");
        }
        if (useModel == null || useModel.trim().isEmpty()) {
            // 没配置时提示去后台设置（系统服务商的模型名不一定是通用的，硬套容易报错）
            throw new BusinessException("请先在后台「集市管理」里指定生成商品使用的模型（可留空用系统服务商，但要先填模型名）");
        }
        List<SandboxLocation> locations = locations(wid);
        List<SandboxNews> news = todayNews(wid);
        List<String> moves = new ArrayList<>();
        for (SandboxCharacter character : characters(wid)) {
            for (SandboxAct act : recentActs(character.getId(), 1)) {
                moves.add(character.getName() + "：" + truncate(blankToDefault(act.getSummary(), act.getActions()), 50));
            }
        }
        String extra = configService.getConfigValue("sandbox_shop_prompt_extra", "");

        StringBuilder sys = new StringBuilder();
        sys.append("你是一个幻想世界的商队头目，负责每天在世界各地摆摊。\n")
                .append("请根据下面给出的世界观与最近的见闻，设计 ").append(size).append(" 件当天摆出来的商品。\n")
                .append("要求：\n")
                .append("1. 商品必须符合这个世界观（技术水平、魔法程度、风俗），不要出现现代物品；\n")
                .append("2. 每件商品给一句 15~40 字的描述，最好带一点来源或用途的故事感（例如「上个旅人当掉的旧罗盘」）；\n")
                .append("3. rarity 是品质：1 普通 / 2 精良 / 3 稀有 / 4 史诗 / 5 传说，大多数应该是 1~2，偶尔才有 3~4；\n")
                .append("4. price 是售价（金币，世界里的通用货币），要和品质相称：")
                .append("普通 1~5、精良 3~10、稀有 6~18、史诗 12~30、传说 25~50；\n")
                .append("5. stock 是今天的库存：1~5 件，越是好东西越少；\n")
                .append("6. 只输出一个 JSON 数组，不要解释、不要 Markdown 代码块。\n")
                .append("输出格式：[{\"name\":\"商品名\",\"description\":\"描述\",\"rarity\":1,\"price\":3,\"stock\":2}]\n");
        if (notBlank(extra)) {
            sys.append("附加要求：").append(extra).append("\n");
        }

        StringBuilder user = new StringBuilder();
        user.append("【世界观】\n").append(blankToDefault(world.getWorldPrompt(),
                blankToDefault(world.getDescription(), DEFAULT_WORLD_PROMPT))).append("\n");
        if (notBlank(world.getDescription())) {
            user.append("【世界简介】\n").append(world.getDescription()).append("\n");
        }
        user.append("【现在时间】").append(timeText(LocalDateTime.now())).append("\n");
        if (!locations.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (SandboxLocation location : locations) {
                names.add(location.getName());
            }
            user.append("【地图上的地区】").append(String.join("、", names)).append("\n");
        }
        if (!news.isEmpty()) {
            user.append("【今日要闻】\n");
            for (SandboxNews item : news) {
                user.append("- ").append(item.getTitle()).append("\n");
            }
        }
        if (!moves.isEmpty()) {
            user.append("【居民最近的动静】\n");
            for (String move : moves) {
                user.append("- ").append(move).append("\n");
            }
        }
        user.append("\n请输出今天要摆出来的 ").append(size).append(" 件商品（JSON 数组）。");

        AiProvider target = aiProviderService.resolveManualProvider(provider);
        boolean preset = AuditContext.isSet();
        if (!preset) {
            AuditContext.manual("沙盒·生成集市商品");
        }
        String raw;
        try {
            raw = aiProviderService.chat(target, useModel, sys.toString(), user.toString(), 0.9);
        } finally {
            if (!preset) {
                AuditContext.clear();
            }
        }
        JSONArray array = parseJsonArray(raw);
        if (array == null || array.isEmpty()) {
            throw new BusinessException("AI 返回内容无法解析成 JSON 数组，请重试或换一个模型");
        }
        LocalDateTime batch = LocalDateTime.now();
        int created = 0;
        for (Object element : array) {
            if (created >= size) {
                break;
            }
            JSONObject item = element instanceof JSONObject ? (JSONObject) element : null;
            if (item == null) {
                continue;
            }
            // AI 生成的商品名同样规范化（防止出现「晨雾森林 of 野浆果」这种混排名字）
            String name = truncate(SandboxItemName.normalize(item.getStr("name")), 60);
            if (name.isEmpty()) {
                continue;
            }
            int rarity = Math.max(1, Math.min(5, Convert.toInt(item.get("rarity"), 1)));
            int[] range = SHOP_PRICE_RANGE[rarity - 1];
            int price = Math.max(range[0], Math.min(range[1], Convert.toInt(item.get("price"), range[0])));
            int stock = Math.max(1, Math.min(SHOP_STOCK_MAX, Convert.toInt(item.get("stock"), 1)));
            SandboxShopItem createdItem = new SandboxShopItem();
            createdItem.setWorldId(wid);
            createdItem.setBatchTime(batch);
            createdItem.setName(name);
            createdItem.setDescription(truncate(trimToEmpty(item.getStr("description")), 300));
            createdItem.setRarity(rarity);
            createdItem.setPrice(price);
            createdItem.setStock(stock);
            createdItem.setTotalStock(stock);
            createdItem.setSource("ai");
            createdItem.setPinned(0);
            createdItem.setEnabled(1);
            createdItem.setCreateTime(batch);
            shopItemMapper.insert(createdItem);
            created++;
        }
        if (created == 0) {
            throw new BusinessException("AI 没有给出可用的商品，请重试");
        }
        log.info("旅人集市刷新完成（世界 {}）：新增 {} 件商品", wid, created);
        return created;
    }

    @Override
    public int autoRefreshShop() {
        if (!"1".equals(configService.getConfigValue("sandbox_shop_enabled", "1"))
                || !"1".equals(configService.getConfigValue("sandbox_shop_auto_enabled", "1"))) {
            return 0;
        }
        int refreshed = 0;
        for (SandboxWorld world : worlds()) {
            if (world.getEnabled() == null || world.getEnabled() != 1 || !shopRefreshDue(world.getId())) {
                continue;
            }
            try {
                if (generateShopItems(null, null, null, world.getId()) > 0) {
                    refreshed++;
                }
            } catch (Exception e) {
                log.warn("旅人集市自动刷新失败（世界 {}）：{}", world.getId(), e.getMessage());
            }
        }
        return refreshed;
    }

    /**
     * 是否到了刷新时间：
     * 间隔 ≥ 24 小时 → 按每天的起始时间对齐（每天固定在某个时间刷一次）；
     * 间隔 &lt; 24 小时 → 距上一批满 N 小时就刷（一天可以刷多次）。
     */
    private boolean shopRefreshDue(Long worldId) {
        int hours = Math.max(1, intConfig("sandbox_shop_interval_hours", 24));
        LocalTime start = timeConfig("sandbox_shop_auto_time", LocalTime.of(8, 0));
        LocalDateTime now = LocalDateTime.now();
        SandboxShopItem latest = shopItemMapper.selectOne(new LambdaQueryWrapper<SandboxShopItem>()
                .eq(SandboxShopItem::getWorldId, worldId)
                .orderByDesc(SandboxShopItem::getBatchTime)
                .last("limit 1"));
        LocalDateTime due;
        if (latest == null || latest.getBatchTime() == null) {
            due = LocalDate.now().atTime(start);
        } else if (hours >= 24) {
            due = latest.getBatchTime().toLocalDate().plusDays(1).atTime(start);
        } else {
            due = latest.getBatchTime().plusHours(hours);
        }
        return !now.isBefore(due);
    }

    /**
     * 前台用户购买并赠送给角色：商品按金币标价，这里按汇率折算成积分扣款（管理员免费）→ 扣库存（条件更新，防超卖）→
     * 礼物进角色背包 → 写礼物记录（角色提示词里只说「来自异世界的礼物」）→ 写购买记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SandboxShopOrder buyShopItem(Long itemId, Long characterId) {
        SandboxShopItem item = shopItemMapper.selectById(itemId);
        if (item == null || item.getEnabled() == null || item.getEnabled() != 1) {
            throw new BusinessException("这件商品已经不在集市里了");
        }
        if (item.getStock() == null || item.getStock() <= 0) {
            throw new BusinessException("这件商品已经售罄了");
        }
        SandboxCharacter character = characterMapper.selectById(characterId);
        if (character == null) {
            throw new BusinessException("角色不存在");
        }
        if (!item.getWorldId().equals(character.getWorldId())) {
            throw new BusinessException("这件商品不属于该角色所在的世界");
        }
        Long userId = currentUserId();
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        boolean admin = isAdmin(user);
        if (!admin && userId != null) {
            int limit = Math.max(1, intConfig("sandbox_shop_limit_per_character", 1));
            Long bought = shopOrderMapper.selectCount(new LambdaQueryWrapper<SandboxShopOrder>()
                    .eq(SandboxShopOrder::getItemId, itemId)
                    .eq(SandboxShopOrder::getUserId, userId)
                    .eq(SandboxShopOrder::getCharacterId, characterId));
            if (bought != null && bought >= limit) {
                throw new BusinessException("你今天已经把这件商品送给「" + character.getName() + "」了");
            }
        }
        // 先确认背包收得下（新物品且背包已满时直接拒绝，避免扣了库存又塞不进去）
        checkBackpackCanAccept(character, item.getName());
        int coinPrice = Math.max(0, item.getPrice() == null ? 0 : item.getPrice());
        // 商品用金币标价，用户掏的是积分：按汇率折算并向上取整（例如 3 金币按 1:10 折算 = 1 积分）
        int cost = admin ? 0 : shopPointsCost(coinPrice);
        if (cost > 0) {
            pointService.deductPoints(userId, cost, "sandbox_shop",
                    "旅人集市：把「" + item.getName() + "」送给「" + character.getName() + "」");
        }
        int updated = shopItemMapper.update(null, new LambdaUpdateWrapper<SandboxShopItem>()
                .eq(SandboxShopItem::getId, itemId)
                .gt(SandboxShopItem::getStock, 0)
                .setSql("stock = stock - 1"));
        if (updated <= 0) {
            throw new BusinessException("手慢了，这件商品刚刚被抢光");
        }
        addGiftToBackpack(character, item);
        LocalDateTime now = LocalDateTime.now();
        SandboxGift gift = new SandboxGift();
        gift.setWorldId(item.getWorldId());
        gift.setCharacterId(character.getId());
        gift.setItemName(item.getName());
        gift.setItemDescription(truncate(item.getDescription(), 300));
        gift.setQuantity(1);
        gift.setPointsCost(cost);
        gift.setCoinPrice(coinPrice);
        gift.setCreateTime(now);
        giftMapper.insert(gift);

        SandboxShopOrder order = new SandboxShopOrder();
        order.setWorldId(item.getWorldId());
        order.setItemId(item.getId());
        order.setItemName(item.getName());
        order.setUserId(userId);
        order.setUserName(user == null ? null : blankToDefault(user.getNickname(), user.getUsername()));
        order.setCharacterId(character.getId());
        order.setCharacterName(character.getName());
        order.setQuantity(1);
        order.setPointsCost(cost);
        order.setCoinPrice(coinPrice);
        order.setBuyerType("user");
        order.setCreateTime(now);
        shopOrderMapper.insert(order);
        return order;
    }

    /** 金币价折算成积分：按 sandbox_coin_rate 换算并向上取整（1 金币不能算成 0 积分） */
    private int shopPointsCost(int coinPrice) {
        return SandboxShopCoin.pointsOf(coinPrice, intConfig("sandbox_coin_rate", 1));
    }

    /** 背包能不能收下这件礼物：已有同名物品都能收（只加数量），新物品要看种类上限 */
    private void checkBackpackCanAccept(SandboxCharacter character, String itemName) {
        List<SandboxItem> backpack = items(character.getId());
        for (SandboxItem owned : backpack) {
            if (owned.getName() != null && owned.getName().equals(itemName)) {
                return;
            }
        }
        if (backpack.size() >= ITEM_KIND_MAX) {
            throw new BusinessException("「" + character.getName() + "」的背包已经满了，暂时收不下新礼物");
        }
    }

    /** 礼物进角色背包（数量固定 1 件） */
    private void addGiftToBackpack(SandboxCharacter character, SandboxShopItem item) {
        addToBackpack(character, item.getName(), 1, item.getRarity(), item.getIcon(), item.getDescription());
    }

    /** 物品进角色背包：已有就加数量，没有就新建一件 */
    private void addToBackpack(SandboxCharacter character, String name, int quantity,
                              Integer rarity, String icon, String description) {
        LocalDateTime now = LocalDateTime.now();
        for (SandboxItem owned : items(character.getId())) {
            if (owned.getName() != null && owned.getName().equals(name)) {
                int total = (owned.getQuantity() == null ? 1 : owned.getQuantity()) + quantity;
                itemMapper.update(null, new LambdaUpdateWrapper<SandboxItem>()
                        .eq(SandboxItem::getId, owned.getId())
                        .set(SandboxItem::getQuantity, total)
                        .set(SandboxItem::getUpdateTime, now));
                return;
            }
        }
        SandboxItem created = new SandboxItem();
        created.setWorldId(character.getWorldId());
        created.setCharacterId(character.getId());
        created.setName(name);
        created.setQuantity(quantity);
        created.setRarity(rarity);
        created.setIcon(icon);
        created.setDescription(truncate(description, 300));
        created.setCreateTime(now);
        created.setUpdateTime(now);
        itemMapper.insert(created);
    }

    /**
     * 角色自己在旅人集市买东西（AI 在行动里返回 shop_buy 时调用）。
     *
     * 规则：
     *   1. 商品必须来自当前这一批（前台只展示最新一批），且没有售罄、没有下架；
     *   2. 用**角色自己的金币**结算，余额不足就买不成（不会扣成负数）；
     *   3. 每天最多买 sandbox_shop_buy_per_day 件（默认 2），防止 AI 每步都去集市；
     *   4. 买到的东西进背包；库存用条件更新扣减，和前台用户抢购天然互斥。
     *
     * @param coinsRef 长度 1 的数组，回传扣款后的金币余额（金币是本方法算出、随后统一落库的）
     * @return 买到的东西的文字说明（追加进行动的物品变化里，前台时间线能看到），没买到返回 null
     */
    private String applyShopPurchases(SandboxCharacter character, Object shopBuy, int[] coinsRef, Set<String> boughtNames) {
        if (!(shopBuy instanceof JSONArray) || ((JSONArray) shopBuy).isEmpty()) {
            return null;
        }
        if (!"1".equals(configService.getConfigValue("sandbox_shop_enabled", "1"))) {
            return null;
        }
        int perDay = intConfig("sandbox_shop_buy_per_day", 2);
        int boughtCount = 0;
        if (perDay > 0) {
            Long boughtToday = shopOrderMapper.selectCount(new LambdaQueryWrapper<SandboxShopOrder>()
                    .eq(SandboxShopOrder::getCharacterId, character.getId())
                    .eq(SandboxShopOrder::getBuyerType, "character")
                    .ge(SandboxShopOrder::getCreateTime, LocalDate.now().atStartOfDay()));
            boughtCount = boughtToday == null ? 0 : boughtToday.intValue();
            if (boughtCount >= perDay) {
                return null;
            }
        }
        List<SandboxShopItem> batch = latestShopBatch(character.getWorldId(), true);
        if (batch.isEmpty()) {
            return null;
        }
        List<String> changes = new ArrayList<>();
        for (Object raw : (JSONArray) shopBuy) {
            String name;
            int quantity = 1;
            if (raw instanceof JSONObject) {
                name = SandboxItemName.normalize(((JSONObject) raw).getStr("name"));
                quantity = Math.max(1, Math.min(3, Convert.toInt(((JSONObject) raw).get("quantity"), 1)));
            } else {
                name = SandboxItemName.normalize(Convert.toStr(raw));
            }
            if (name.isEmpty()) {
                continue;
            }
            SandboxShopItem item = null;
            for (SandboxShopItem candidate : batch) {
                if (candidate.getName() != null && candidate.getName().equals(name)) {
                    item = candidate;
                    break;
                }
            }
            // 没在当天这批商品里（AI 编的名字）直接忽略，不做任何扣款
            if (item == null) {
                continue;
            }
            int price = Math.max(0, item.getPrice() == null ? 0 : item.getPrice());
            int stock = item.getStock() == null ? 0 : item.getStock();
            quantity = Math.min(quantity, stock);
            if (quantity <= 0) {
                continue;
            }
            if (perDay > 0 && boughtCount >= perDay) {
                break;
            }
            if (perDay > 0) {
                quantity = Math.min(quantity, perDay - boughtCount);
            }
            int total = price * quantity;
            // 背包收不下就不买（避免扣了钱塞不进背包）
            List<SandboxItem> backpack = items(character.getId());
            boolean owned = false;
            for (SandboxItem ownedItem : backpack) {
                if (ownedItem.getName() != null && ownedItem.getName().equals(item.getName())) {
                    owned = true;
                    break;
                }
            }
            if (!owned && backpack.size() >= ITEM_KIND_MAX) {
                continue;
            }
            // 条件更新扣库存：和前台用户抢购同一个商品时谁先到谁买到
            int updated = shopItemMapper.update(null, new LambdaUpdateWrapper<SandboxShopItem>()
                    .eq(SandboxShopItem::getId, item.getId())
                    .ge(SandboxShopItem::getStock, quantity)
                    .setSql("stock = stock - " + quantity));
            if (updated <= 0) {
                continue;
            }
            // 原子扣款：余额够才买（读快照会因为并发而不准）；扣不动就把刚扣的库存还回去
            Integer balance = trySpendCoins(character.getId(), total);
            if (balance == null) {
                shopItemMapper.update(null, new LambdaUpdateWrapper<SandboxShopItem>()
                        .eq(SandboxShopItem::getId, item.getId())
                        .setSql("stock = stock + " + quantity));
                continue;
            }
            coinsRef[0] = balance;
            boughtCount += quantity;
            boughtNames.add(item.getName());
            addToBackpack(character, item.getName(), quantity, item.getRarity(), item.getIcon(), item.getDescription());
            LocalDateTime now = LocalDateTime.now();
            SandboxShopOrder order = new SandboxShopOrder();
            order.setWorldId(item.getWorldId());
            order.setItemId(item.getId());
            order.setItemName(item.getName());
            order.setCharacterId(character.getId());
            order.setCharacterName(character.getName());
            order.setQuantity(quantity);
            order.setPointsCost(0);
            order.setCoinPrice(price);
            order.setBuyerType("character");
            order.setCreateTime(now);
            shopOrderMapper.insert(order);
            addCoinLog(character.getId(), null, null, "shop_buy", -total, 0, balance,
                    "在旅人集市买下「" + item.getName() + "」");
            changes.add("在旅人集市买下 " + item.getName() + " -" + total + " 金币");
        }
        return changes.isEmpty() ? null : truncate(String.join("、", changes), 200);
    }

    @Override
    public PageResult<SandboxShopOrder> shopOrders(Long worldId, long page, long size) {
        LambdaQueryWrapper<SandboxShopOrder> wrapper = new LambdaQueryWrapper<SandboxShopOrder>()
                .eq(worldId != null, SandboxShopOrder::getWorldId, worldId)
                .orderByDesc(SandboxShopOrder::getId);
        IPage<SandboxShopOrder> result = shopOrderMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    /** 集市今日统计：卖出多少件、回收多少积分与金币、角色自购几件（后台观察经济用） */
    @Override
    public Map<String, Object> shopStats(Long worldId) {
        List<SandboxShopOrder> today = shopOrderMapper.selectList(new LambdaQueryWrapper<SandboxShopOrder>()
                .eq(worldId != null, SandboxShopOrder::getWorldId, worldId)
                .ge(SandboxShopOrder::getCreateTime, LocalDate.now().atStartOfDay()));
        int sold = 0;
        int points = 0;
        int coins = 0;
        int characterBuys = 0;
        for (SandboxShopOrder order : today) {
            int quantity = order.getQuantity() == null ? 1 : order.getQuantity();
            sold += quantity;
            points += order.getPointsCost() == null ? 0 : order.getPointsCost();
            coins += (order.getCoinPrice() == null ? 0 : order.getCoinPrice()) * quantity;
            if ("character".equals(order.getBuyerType())) {
                characterBuys += quantity;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("sold", sold);
        stats.put("points", points);
        stats.put("coins", coins);
        stats.put("characterBuys", characterBuys);
        return stats;
    }

    /** 解析 AI 返回的 JSON 数组（允许被 Markdown 代码块包裹） */
    // ============================== 存档：导出 / 清空 ==============================

    /** 角色清空后的默认状态 */
    private static final String RESET_STATUS_JSON = "{\"体力\":100,\"魔力\":100,\"饥饿度\":0,\"心情\":\"平静\"}";

    @Override
    public byte[] exportWorld(Long worldId, boolean includeRawResponse) {
        Long wid = worldId(worldId);
        SandboxWorld world = world(wid);
        if (world.getId() == null) {
            throw new BusinessException("世界不存在");
        }
        List<SandboxLocation> locations = locations(wid);
        List<SandboxCharacter> characters = characters(wid);
        JSONObject root = new JSONObject();
        root.set("format", "bcblog-sandbox-save");
        root.set("version", 1);
        root.set("exportedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));
        root.set("includeRawResponse", includeRawResponse);

        JSONObject worldJson = new JSONObject();
        worldJson.set("name", world.getName());
        worldJson.set("description", world.getDescription());
        worldJson.set("mapImage", world.getMapImage());
        worldJson.set("worldPrompt", world.getWorldPrompt());
        worldJson.set("enabled", world.getEnabled());
        worldJson.set("portalVisible", world.getPortalVisible());
        root.set("world", worldJson);

        JSONArray locationArray = new JSONArray();
        for (SandboxLocation item : locations) {
            JSONObject o = new JSONObject();
            o.set("name", item.getName());
            o.set("icon", item.getIcon());
            o.set("x", item.getX());
            o.set("y", item.getY());
            o.set("width", item.getWidth());
            o.set("height", item.getHeight());
            o.set("polygon", item.getPolygon());
            o.set("description", item.getDescription());
            o.set("sortOrder", item.getSortOrder());
            locationArray.add(o);
        }
        root.set("locations", locationArray);

        JSONArray characterArray = new JSONArray();
        for (SandboxCharacter c : characters) {
            JSONObject o = new JSONObject();
            o.set("name", c.getName());
            o.set("title", c.getTitle());
            o.set("avatar", c.getAvatar());
            o.set("appearance", c.getAppearance());
            o.set("persona", c.getPersona());
            o.set("providerName", providerNameOf(c.getProviderId()));
            o.set("model", c.getModel());
            o.set("temperature", c.getTemperature());
            o.set("intervalMin", c.getIntervalMin());
            o.set("intervalMax", c.getIntervalMax());
            o.set("aiIntervalMin", c.getAiIntervalMin());
            o.set("aiIntervalMax", c.getAiIntervalMax());
            o.set("enabled", c.getEnabled());
            o.set("x", c.getX());
            o.set("y", c.getY());
            o.set("locationName", c.getLocationName());
            o.set("subLocation", c.getSubLocation());
            o.set("statusJson", c.getStatusJson());
            o.set("coins", c.getCoins());
            o.set("combatPower", c.getCombatPower());
            characterArray.add(o);
        }
        root.set("characters", characterArray);

        JSONArray actArray = new JSONArray();
        for (SandboxAct act : actMapper.selectList(new LambdaQueryWrapper<SandboxAct>()
                .eq(SandboxAct::getWorldId, wid).orderByAsc(SandboxAct::getId))) {
            JSONObject o = new JSONObject();
            o.set("characterName", characterNameOf(characters, act.getCharacterId()));
            o.set("createTime", act.getCreateTime() == null ? null : act.getCreateTime().format(SAVE_TIME_FORMATTER));
            o.set("locationName", act.getLocationName());
            o.set("subLocation", act.getSubLocation());
            o.set("x", act.getX());
            o.set("y", act.getY());
            o.set("actions", act.getActions());
            o.set("innerVoice", act.getInnerVoice());
            o.set("summary", act.getSummary());
            o.set("statusJson", act.getStatusJson());
            o.set("coinChange", act.getCoinChange());
            o.set("combatChange", act.getCombatChange());
            o.set("favorChange", act.getFavorChange());
            o.set("itemChange", act.getItemChange());
            o.set("companions", act.getCompanions());
            o.set("newsRef", act.getNewsRef());
            o.set("manual", act.getManual());
            o.set("reaction", act.getReaction());
            o.set("fromAi", act.getFromAi());
            if (includeRawResponse) {
                o.set("rawResponse", act.getRawResponse());
            }
            actArray.add(o);
        }
        root.set("acts", actArray);

        JSONArray memoryArray = new JSONArray();
        for (SandboxMemory m : memoryMapper.selectList(new LambdaQueryWrapper<SandboxMemory>()
                .eq(SandboxMemory::getWorldId, wid).orderByAsc(SandboxMemory::getId))) {
            JSONObject o = new JSONObject();
            o.set("characterName", characterNameOf(characters, m.getCharacterId()));
            o.set("memoryDate", m.getMemoryDate() == null ? null : m.getMemoryDate().toString());
            o.set("summary", m.getSummary());
            o.set("fromAi", m.getFromAi());
            memoryArray.add(o);
        }
        root.set("memories", memoryArray);

        JSONArray itemArray = new JSONArray();
        for (SandboxItem it : itemMapper.selectList(new LambdaQueryWrapper<SandboxItem>()
                .eq(SandboxItem::getWorldId, wid).orderByAsc(SandboxItem::getId))) {
            JSONObject o = new JSONObject();
            o.set("characterName", characterNameOf(characters, it.getCharacterId()));
            o.set("name", it.getName());
            o.set("quantity", it.getQuantity());
            o.set("rarity", it.getRarity());
            o.set("icon", it.getIcon());
            o.set("description", it.getDescription());
            itemArray.add(o);
        }
        root.set("items", itemArray);

        JSONArray relationArray = new JSONArray();
        for (SandboxRelation r : relationMapper.selectList(new LambdaQueryWrapper<SandboxRelation>()
                .eq(SandboxRelation::getWorldId, wid).orderByAsc(SandboxRelation::getId))) {
            JSONObject o = new JSONObject();
            o.set("characterName", characterNameOf(characters, r.getCharacterId()));
            o.set("targetName", characterNameOf(characters, r.getTargetId()));
            o.set("favor", r.getFavor());
            o.set("remark", r.getRemark());
            o.set("lastChange", r.getLastChange());
            relationArray.add(o);
        }
        root.set("relations", relationArray);

        JSONArray whisperArray = new JSONArray();
        for (SandboxInteraction w : interactionMapper.selectList(new LambdaQueryWrapper<SandboxInteraction>()
                .eq(SandboxInteraction::getWorldId, wid).orderByAsc(SandboxInteraction::getId))) {
            JSONObject o = new JSONObject();
            o.set("characterName", characterNameOf(characters, w.getCharacterId()));
            o.set("userName", w.getUserName());
            o.set("userAvatar", w.getUserAvatar());
            o.set("content", w.getContent());
            o.set("pointsCost", w.getPointsCost());
            o.set("createTime", w.getCreateTime() == null ? null : w.getCreateTime().format(SAVE_TIME_FORMATTER));
            whisperArray.add(o);
        }
        root.set("whispers", whisperArray);

        JSONArray giftArray = new JSONArray();
        for (SandboxGift g : giftMapper.selectList(new LambdaQueryWrapper<SandboxGift>()
                .eq(SandboxGift::getWorldId, wid).orderByAsc(SandboxGift::getId))) {
            JSONObject o = new JSONObject();
            o.set("characterName", characterNameOf(characters, g.getCharacterId()));
            o.set("itemName", g.getItemName());
            o.set("itemDescription", g.getItemDescription());
            o.set("quantity", g.getQuantity());
            o.set("pointsCost", g.getPointsCost());
            o.set("createTime", g.getCreateTime() == null ? null : g.getCreateTime().format(SAVE_TIME_FORMATTER));
            giftArray.add(o);
        }
        root.set("gifts", giftArray);

        JSONArray coinArray = new JSONArray();
        for (SandboxCoinLog log : coinLogMapper.selectList(new LambdaQueryWrapper<SandboxCoinLog>()
                .eq(SandboxCoinLog::getWorldId, wid).orderByAsc(SandboxCoinLog::getId))) {
            JSONObject o = new JSONObject();
            o.set("characterName", characterNameOf(characters, log.getCharacterId()));
            o.set("userName", log.getUserName());
            o.set("type", log.getType());
            o.set("coins", log.getCoins());
            o.set("pointsCost", log.getPointsCost());
            o.set("balance", log.getBalance());
            o.set("remark", log.getRemark());
            o.set("createTime", log.getCreateTime() == null ? null : log.getCreateTime().format(SAVE_TIME_FORMATTER));
            coinArray.add(o);
        }
        root.set("coinLogs", coinArray);

        JSONArray newsArray = new JSONArray();
        for (SandboxNews n : newsMapper.selectList(new LambdaQueryWrapper<SandboxNews>()
                .eq(SandboxNews::getWorldId, wid).orderByAsc(SandboxNews::getId))) {
            JSONObject o = new JSONObject();
            o.set("title", n.getTitle());
            o.set("content", n.getContent());
            o.set("locationName", n.getLocationName());
            o.set("x", n.getX());
            o.set("y", n.getY());
            o.set("level", n.getLevel());
            o.set("source", n.getSource());
            o.set("newsDate", n.getNewsDate() == null ? null : n.getNewsDate().toString());
            o.set("pinned", n.getPinned());
            o.set("enabled", n.getEnabled());
            newsArray.add(o);
        }
        root.set("news", newsArray);

        JSONArray shopArray = new JSONArray();
        for (SandboxShopItem s : shopItemMapper.selectList(new LambdaQueryWrapper<SandboxShopItem>()
                .eq(SandboxShopItem::getWorldId, wid).orderByAsc(SandboxShopItem::getId))) {
            JSONObject o = new JSONObject();
            o.set("name", s.getName());
            o.set("description", s.getDescription());
            o.set("icon", s.getIcon());
            o.set("rarity", s.getRarity());
            o.set("price", s.getPrice());
            o.set("originalPrice", s.getOriginalPrice());
            o.set("stock", s.getStock());
            o.set("totalStock", s.getTotalStock());
            o.set("source", s.getSource());
            o.set("pinned", s.getPinned());
            o.set("enabled", s.getEnabled());
            o.set("batchTime", s.getBatchTime() == null ? null : s.getBatchTime().format(SAVE_TIME_FORMATTER));
            shopArray.add(o);
        }
        root.set("shopItems", shopArray);

        // 打包 zip：world.json + 引用到的图片（地图、立绘、图标）
        List<String> images = new ArrayList<>();
        collectUploadPath(images, world.getMapImage());
        for (SandboxCharacter c : characters) {
            collectUploadPath(images, c.getAvatar());
        }
        for (SandboxLocation l : locations) {
            collectUploadPath(images, l.getIcon());
        }
        for (Object element : itemArray) {
            collectUploadPath(images, ((JSONObject) element).getStr("icon"));
        }
        for (Object element : shopArray) {
            collectUploadPath(images, ((JSONObject) element).getStr("icon"));
        }
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("world.json"));
            zip.write(root.toStringPretty().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (String path : images) {
                File file = new File(uploadDir, path);
                if (!file.exists() || !file.isFile()) {
                    log.warn("导出存档：图片文件不存在，已跳过 {}", path);
                    continue;
                }
                zip.putNextEntry(new ZipEntry("uploads/" + path.replace('\\', '/')));
                try (FileInputStream in = new FileInputStream(file)) {
                    byte[] chunk = new byte[8192];
                    int len;
                    while ((len = in.read(chunk)) > 0) {
                        zip.write(chunk, 0, len);
                    }
                }
                zip.closeEntry();
            }
            zip.finish();
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("导出存档失败：" + e.getMessage());
        }
    }

    /** 把 /uploads/xxx 这类路径收集起来（去重），用于打进 zip */
    /** 随机挑一个地点（每个角色各自随机，尽量分散到不同地区） */
    private SandboxLocation randomLocation(List<SandboxLocation> locations, Random random) {
        if (locations == null || locations.isEmpty()) {
            return null;
        }
        return locations.get(random.nextInt(locations.size()));
    }

    /**
     * 在地点范围内随机取一个落点：
     *   矩形区域直接在外接矩形内随机；套索画的多边形用「随机取点 + 判断是否在区域内」的方式（最多试 40 次）；
     *   都取不到时退回区域标注点（形心 / 内部点），单点地点就是它本身。
     */
    private int[] randomPointIn(SandboxLocation location, Random random) {
        if (location == null) {
            return new int[]{50, 50};
        }
        List<double[]> polygon = polygonOf(location);
        if (polygon == null) {
            return new int[]{clamp(areaX(location)), clamp(areaY(location))};
        }
        double[] box = SandboxGeo.bbox(polygon);
        double width = Math.max(1, box[2] - box[0]);
        double height = Math.max(1, box[3] - box[1]);
        for (int i = 0; i < 40; i++) {
            double x = box[0] + random.nextDouble() * width;
            double y = box[1] + random.nextDouble() * height;
            if (SandboxGeo.contains(polygon, x, y)) {
                return new int[]{clamp((int) Math.round(x)), clamp((int) Math.round(y))};
            }
        }
        double[] fallback = SandboxGeo.labelPoint(polygon);
        return new int[]{clamp((int) Math.round(fallback[0])), clamp((int) Math.round(fallback[1]))};
    }

    /**
     * 导入存档：支持 zip（world.json + 图片）或纯 world.json。
     * 覆盖模式会先清空目标世界的进度；新建模式会另起一个世界。
     * 记录之间靠**名字**重新映射（地点名/角色名），AI 服务商也按名字匹配，
     * 匹配不到就回落到系统服务商并记进导入报告，避免导完发现角色跑不动。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importWorld(MultipartFile file, Long targetWorldId, boolean overwrite) {
        JSONObject root = readSaveFile(file);
        JSONObject worldJson = root.getJSONObject("world");
        if (worldJson == null || !notBlank(worldJson.getStr("name"))) {
            throw new BusinessException("存档文件不完整：缺少 world 信息");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        // 1. 世界：覆盖模式改现有世界，新建模式另起一个
        SandboxWorld world;
        if (overwrite) {
            requireWorld(targetWorldId);
            resetWorld(targetWorldId);
            world = world(targetWorldId);
            report.put("mode", "覆盖世界 #" + targetWorldId);
        } else {
            world = new SandboxWorld();
            world.setName(truncate(worldJson.getStr("name") + "（导入）", 100));
            world.setEnabled(0);
            world.setPortalVisible(0);
            worldMapper.insert(world);
            report.put("mode", "新建世界 #" + world.getId());
        }
        String saveName = worldJson.getStr("name");
        SandboxWorld update = new SandboxWorld();
        update.setId(world.getId());
        if (overwrite) {
            update.setName(truncate(saveName, 100));
        }
        update.setDescription(truncate(worldJson.getStr("description"), 500));
        update.setMapImage(truncate(worldJson.getStr("mapImage"), 500));
        update.setWorldPrompt(worldJson.getStr("worldPrompt"));
        if (overwrite) {
            update.setEnabled(worldJson.getInt("enabled") == null ? 0 : worldJson.getInt("enabled"));
            update.setPortalVisible(worldJson.getInt("portalVisible") == null ? 0 : worldJson.getInt("portalVisible"));
        }
        worldMapper.updateById(update);
        world = world(world.getId());
        report.put("worldName", world.getName());

        // 2. 地点：按名字匹配现有地点，没有就新建
        Map<String, SandboxLocation> locationMap = new HashMap<>();
        for (SandboxLocation existing : locations(world.getId())) {
            locationMap.put(existing.getName(), existing);
        }
        int newLocations = 0;
        JSONArray locationArray = root.getJSONArray("locations");
        if (locationArray != null) {
            for (Object element : locationArray) {
                JSONObject o = toObject(element);
                if (o == null || !notBlank(o.getStr("name"))) {
                    continue;
                }
                String name = truncate(o.getStr("name"), 90);
                if (locationMap.containsKey(name)) {
                    continue;
                }
                SandboxLocation location = new SandboxLocation();
                location.setWorldId(world.getId());
                location.setName(name);
                location.setIcon(truncate(o.getStr("icon"), 500));
                location.setX(o.getInt("x"));
                location.setY(o.getInt("y"));
                location.setWidth(o.getInt("width"));
                location.setHeight(o.getInt("height"));
                location.setPolygon(o.getStr("polygon"));
                location.setDescription(truncate(o.getStr("description"), 500));
                location.setSortOrder(o.getInt("sortOrder") == null ? 0 : o.getInt("sortOrder"));
                locationMapper.insert(location);
                locationMap.put(name, location);
                newLocations++;
            }
        }
        report.put("locations", newLocations);

        // 3. 角色：按名字匹配，没有就新建；AI 服务商按名字匹配
        Map<String, SandboxCharacter> characterMap = new HashMap<>();
        for (SandboxCharacter existing : characters(world.getId())) {
            characterMap.put(existing.getName(), existing);
        }
        Map<String, Long> providerIds = providerIdsByName();
        int newCharacters = 0;
        JSONArray characterArray = root.getJSONArray("characters");
        if (characterArray != null) {
            for (Object element : characterArray) {
                JSONObject o = toObject(element);
                if (o == null || !notBlank(o.getStr("name"))) {
                    continue;
                }
                String name = truncate(o.getStr("name"), 90);
                SandboxCharacter character = characterMap.get(name);
                if (character == null) {
                    character = new SandboxCharacter();
                    character.setWorldId(world.getId());
                    character.setName(name);
                    character.setCreateTime(LocalDateTime.now());
                    newCharacters++;
                }
                character.setTitle(truncate(o.getStr("title"), 90));
                character.setAvatar(truncate(o.getStr("avatar"), 500));
                character.setAppearance(truncate(o.getStr("appearance"), 500));
                character.setPersona(o.getStr("persona"));
                String providerName = o.getStr("providerName");
                if (notBlank(providerName)) {
                    Long pid = providerIds.get(providerName.trim());
                    if (pid != null) {
                        character.setProviderId(pid);
                    } else {
                        character.setProviderId(null);
                        warnings.add("角色「" + name + "」的服务商「" + providerName + "」在本站不存在，已改为系统服务商");
                    }
                }
                character.setModel(truncate(o.getStr("model"), 90));
                character.setTemperature(o.getBigDecimal("temperature"));
                character.setIntervalMin(o.getInt("intervalMin"));
                character.setIntervalMax(o.getInt("intervalMax"));
                character.setAiIntervalMin(o.getInt("aiIntervalMin"));
                character.setAiIntervalMax(o.getInt("aiIntervalMax"));
                character.setEnabled(o.getInt("enabled") == null ? 1 : o.getInt("enabled"));
                character.setX(o.getInt("x"));
                character.setY(o.getInt("y"));
                character.setLocationName(truncate(o.getStr("locationName"), 90));
                character.setSubLocation(truncate(o.getStr("subLocation"), 90));
                character.setStatusJson(truncate(o.getStr("statusJson"), 1000));
                character.setCoins(o.getInt("coins") == null ? 0 : o.getInt("coins"));
                character.setCombatPower(o.getInt("combatPower") == null ? COMBAT_POWER_DEFAULT : o.getInt("combatPower"));
                // 下次行动时间不照搬存档：统一给一个间隔，避免导入后立刻全跑或永远不跑
                character.setNextRunTime(LocalDateTime.now()
                        .plusMinutes(Math.max(15, intConfig("sandbox_interval_max", 75))));
                character.setNextReason(null);
                character.setLastRunTime(null);
                character.setLastError(null);
                character.setFailCount(0);
                if (character.getId() == null) {
                    characterMapper.insert(character);
                } else {
                    characterMapper.updateById(character);
                }
                characterMap.put(name, character);
            }
        }
        report.put("characters", newCharacters);

        // 4. 行动记录
        int acts = 0;
        JSONArray actArray = root.getJSONArray("acts");
        if (actArray != null) {
            for (Object element : actArray) {
                JSONObject o = toObject(element);
                SandboxCharacter owner = o == null ? null : characterMap.get(truncate(o.getStr("characterName"), 90));
                if (owner == null) {
                    continue;
                }
                SandboxAct act = new SandboxAct();
                act.setWorldId(world.getId());
                act.setCharacterId(owner.getId());
                act.setCreateTime(parseSaveTime(o.getStr("createTime")));
                act.setLocationName(truncate(o.getStr("locationName"), 90));
                act.setSubLocation(truncate(o.getStr("subLocation"), 90));
                act.setX(o.getInt("x"));
                act.setY(o.getInt("y"));
                act.setActions(truncate(o.getStr("actions"), 1000));
                act.setInnerVoice(truncate(o.getStr("innerVoice"), 1000));
                act.setSummary(truncate(o.getStr("summary"), 280));
                act.setStatusJson(truncate(o.getStr("statusJson"), 1000));
                act.setCoinChange(o.getInt("coinChange"));
                act.setCombatChange(o.getInt("combatChange"));
                act.setFavorChange(truncate(o.getStr("favorChange"), 190));
                act.setItemChange(truncate(o.getStr("itemChange"), 190));
                act.setCompanions(truncate(o.getStr("companions"), 190));
                act.setNewsRef(truncate(o.getStr("newsRef"), 190));
                act.setManual(o.getInt("manual") == null ? 0 : o.getInt("manual"));
                act.setReaction(o.getInt("reaction") == null ? 0 : o.getInt("reaction"));
                act.setFromAi(o.getInt("fromAi") == null ? 1 : o.getInt("fromAi"));
                act.setRawResponse(o.getStr("rawResponse"));
                actMapper.insert(act);
                acts++;
            }
        }
        report.put("acts", acts);

        // 5. 记忆
        int memories = 0;
        JSONArray memoryArray = root.getJSONArray("memories");
        if (memoryArray != null) {
            for (Object element : memoryArray) {
                JSONObject o = toObject(element);
                SandboxCharacter owner = o == null ? null : characterMap.get(truncate(o.getStr("characterName"), 90));
                if (owner == null) {
                    continue;
                }
                SandboxMemory memory = new SandboxMemory();
                memory.setWorldId(world.getId());
                memory.setCharacterId(owner.getId());
                memory.setMemoryDate(parseSaveDate(o.getStr("memoryDate")));
                memory.setSummary(o.getStr("summary"));
                memory.setFromAi(o.getInt("fromAi") == null ? 1 : o.getInt("fromAi"));
                memory.setCreateTime(LocalDateTime.now());
                memoryMapper.insert(memory);
                memories++;
            }
        }
        report.put("memories", memories);

        // 6. 背包
        int items = 0;
        JSONArray itemArray = root.getJSONArray("items");
        if (itemArray != null) {
            for (Object element : itemArray) {
                JSONObject o = toObject(element);
                SandboxCharacter owner = o == null ? null : characterMap.get(truncate(o.getStr("characterName"), 90));
                if (owner == null || !notBlank(o.getStr("name"))) {
                    continue;
                }
                SandboxItem item = new SandboxItem();
                item.setWorldId(world.getId());
                item.setCharacterId(owner.getId());
                item.setName(truncate(o.getStr("name"), 60));
                item.setQuantity(o.getInt("quantity") == null ? 1 : o.getInt("quantity"));
                item.setRarity(o.getInt("rarity") == null ? 1 : o.getInt("rarity"));
                item.setIcon(truncate(o.getStr("icon"), 500));
                item.setDescription(truncate(o.getStr("description"), 300));
                item.setCreateTime(LocalDateTime.now());
                item.setUpdateTime(LocalDateTime.now());
                itemMapper.insert(item);
                items++;
            }
        }
        report.put("items", items);

        // 7. 好感度
        int relations = 0;
        JSONArray relationArray = root.getJSONArray("relations");
        if (relationArray != null) {
            for (Object element : relationArray) {
                JSONObject o = toObject(element);
                SandboxCharacter owner = o == null ? null : characterMap.get(truncate(o.getStr("characterName"), 90));
                SandboxCharacter target = o == null ? null : characterMap.get(truncate(o.getStr("targetName"), 90));
                if (owner == null || target == null) {
                    continue;
                }
                SandboxRelation relation = new SandboxRelation();
                relation.setWorldId(world.getId());
                relation.setCharacterId(owner.getId());
                relation.setTargetId(target.getId());
                relation.setFavor(o.getInt("favor") == null ? 0 : o.getInt("favor"));
                relation.setRemark(truncate(o.getStr("remark"), 190));
                relation.setLastChange(o.getInt("lastChange"));
                relation.setLastChangeTime(LocalDateTime.now());
                relation.setCreateTime(LocalDateTime.now());
                relation.setUpdateTime(LocalDateTime.now());
                relationMapper.insert(relation);
                relations++;
            }
        }
        report.put("relations", relations);

        // 8. 旅人低语 / 礼物 / 金币流水
        int whispers = 0;
        JSONArray whisperArray = root.getJSONArray("whispers");
        if (whisperArray != null) {
            for (Object element : whisperArray) {
                JSONObject o = toObject(element);
                SandboxCharacter owner = o == null ? null : characterMap.get(truncate(o.getStr("characterName"), 90));
                if (owner == null) {
                    continue;
                }
                SandboxInteraction w = new SandboxInteraction();
                w.setWorldId(world.getId());
                w.setCharacterId(owner.getId());
                w.setUserName(truncate(o.getStr("userName"), 90));
                w.setUserAvatar(truncate(o.getStr("userAvatar"), 500));
                w.setContent(truncate(o.getStr("content"), 200));
                w.setPointsCost(o.getInt("pointsCost") == null ? 0 : o.getInt("pointsCost"));
                w.setCreateTime(parseSaveTime(o.getStr("createTime")));
                interactionMapper.insert(w);
                whispers++;
            }
        }
        report.put("whispers", whispers);

        int gifts = 0;
        JSONArray giftArray = root.getJSONArray("gifts");
        if (giftArray != null) {
            for (Object element : giftArray) {
                JSONObject o = toObject(element);
                SandboxCharacter owner = o == null ? null : characterMap.get(truncate(o.getStr("characterName"), 90));
                if (owner == null) {
                    continue;
                }
                SandboxGift gift = new SandboxGift();
                gift.setWorldId(world.getId());
                gift.setCharacterId(owner.getId());
                gift.setItemName(truncate(o.getStr("itemName"), 90));
                gift.setItemDescription(truncate(o.getStr("itemDescription"), 300));
                gift.setQuantity(o.getInt("quantity") == null ? 1 : o.getInt("quantity"));
                gift.setPointsCost(o.getInt("pointsCost") == null ? 0 : o.getInt("pointsCost"));
                gift.setCreateTime(parseSaveTime(o.getStr("createTime")));
                giftMapper.insert(gift);
                gifts++;
            }
        }
        report.put("gifts", gifts);

        int coinLogs = 0;
        JSONArray coinArray = root.getJSONArray("coinLogs");
        if (coinArray != null) {
            for (Object element : coinArray) {
                JSONObject o = toObject(element);
                SandboxCharacter owner = o == null ? null : characterMap.get(truncate(o.getStr("characterName"), 90));
                if (owner == null) {
                    continue;
                }
                SandboxCoinLog log = new SandboxCoinLog();
                log.setWorldId(world.getId());
                log.setCharacterId(owner.getId());
                log.setUserName(truncate(o.getStr("userName"), 90));
                log.setType(truncate(o.getStr("type"), 20));
                log.setCoins(o.getInt("coins") == null ? 0 : o.getInt("coins"));
                log.setPointsCost(o.getInt("pointsCost") == null ? 0 : o.getInt("pointsCost"));
                log.setBalance(o.getInt("balance") == null ? 0 : o.getInt("balance"));
                log.setRemark(truncate(o.getStr("remark"), 190));
                log.setCreateTime(parseSaveTime(o.getStr("createTime")));
                coinLogMapper.insert(log);
                coinLogs++;
            }
        }
        report.put("coinLogs", coinLogs);

        // 9. 纪闻与集市商品
        int newsCount = 0;
        JSONArray newsArray = root.getJSONArray("news");
        if (newsArray != null) {
            for (Object element : newsArray) {
                JSONObject o = toObject(element);
                if (o == null || !notBlank(o.getStr("title"))) {
                    continue;
                }
                SandboxNews news = new SandboxNews();
                news.setWorldId(world.getId());
                news.setTitle(truncate(o.getStr("title"), 190));
                news.setContent(truncate(o.getStr("content"), 490));
                news.setLocationName(truncate(o.getStr("locationName"), 90));
                news.setX(o.getInt("x"));
                news.setY(o.getInt("y"));
                news.setLevel(o.getInt("level") == null ? 1 : o.getInt("level"));
                news.setSource(truncate(o.getStr("source"), 20));
                news.setNewsDate(parseSaveDate(o.getStr("newsDate")));
                news.setPinned(o.getInt("pinned") == null ? 0 : o.getInt("pinned"));
                news.setEnabled(o.getInt("enabled") == null ? 1 : o.getInt("enabled"));
                news.setCreateTime(LocalDateTime.now());
                newsMapper.insert(news);
                newsCount++;
            }
        }
        report.put("news", newsCount);

        int shopItems = 0;
        JSONArray shopArray = root.getJSONArray("shopItems");
        if (shopArray != null) {
            for (Object element : shopArray) {
                JSONObject o = toObject(element);
                if (o == null || !notBlank(o.getStr("name"))) {
                    continue;
                }
                SandboxShopItem item = new SandboxShopItem();
                item.setWorldId(world.getId());
                item.setName(truncate(o.getStr("name"), 60));
                item.setDescription(truncate(o.getStr("description"), 300));
                item.setIcon(truncate(o.getStr("icon"), 500));
                item.setRarity(o.getInt("rarity") == null ? 1 : o.getInt("rarity"));
                item.setPrice(o.getInt("price") == null ? 1 : o.getInt("price"));
                item.setOriginalPrice(o.getInt("originalPrice"));
                item.setStock(o.getInt("stock") == null ? 0 : o.getInt("stock"));
                item.setTotalStock(o.getInt("totalStock") == null ? 1 : o.getInt("totalStock"));
                item.setSource(truncate(o.getStr("source"), 20));
                item.setPinned(o.getInt("pinned") == null ? 0 : o.getInt("pinned"));
                item.setEnabled(o.getInt("enabled") == null ? 1 : o.getInt("enabled"));
                LocalDateTime batch = parseSaveTime(o.getStr("batchTime"));
                item.setBatchTime(batch == null ? LocalDateTime.now() : batch);
                item.setCreateTime(LocalDateTime.now());
                shopItemMapper.insert(item);
                shopItems++;
            }
        }
        report.put("shopItems", shopItems);

        report.put("warnings", warnings);
        log.info("沙盒存档导入完成：{}", report);
        return report;
    }

    /** 读取存档：zip 取里面的 world.json，否则按纯 JSON 解析 */
    private JSONObject readSaveFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的存档文件");
        }
        try (InputStream in = file.getInputStream()) {
            String text = null;
            if (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
                try (ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8)) {
                    ZipEntry entry;
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    while ((entry = zip.getNextEntry()) != null) {
                        String name = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                        if ("world.json".equals(name)) {
                            byte[] chunk = new byte[8192];
                            int len;
                            while ((len = zip.read(chunk)) > 0) {
                                buffer.write(chunk, 0, len);
                            }
                            text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                            break;
                        }
                        if (name.startsWith("uploads/") && !entry.isDirectory()) {
                            File target = new File(uploadDir, name);
                            File parent = target.getParentFile();
                            if (parent != null && !parent.exists()) {
                                parent.mkdirs();
                            }
                            try (java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                                byte[] chunk = new byte[8192];
                                int len;
                                while ((len = zip.read(chunk)) > 0) {
                                    out.write(chunk, 0, len);
                                }
                            }
                        }
                    }
                }
            } else {
                text = new String(readAll(in), StandardCharsets.UTF_8);
            }
            if (!notBlank(text)) {
                throw new BusinessException("存档里没有找到 world.json");
            }
            JSONObject root = JSONUtil.parseObj(text);
            if (root == null) {
                throw new BusinessException("存档内容不是合法的 JSON");
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("读取存档失败：" + e.getMessage());
        }
    }

    private byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int len;
        while ((len = in.read(chunk)) > 0) {
            buffer.write(chunk, 0, len);
        }
        return buffer.toByteArray();
    }

    /** 服务商名字 -> id（导入存档时按名字匹配，跨环境才有意义） */
    private Map<String, Long> providerIdsByName() {
        Map<String, Long> map = new HashMap<>();
        for (AiProvider provider : aiProviderMapper.selectList(null)) {
            if (notBlank(provider.getName())) {
                map.put(provider.getName().trim(), provider.getId());
            }
        }
        return map;
    }

    private JSONObject toObject(Object element) {
        if (element instanceof JSONObject) {
            return (JSONObject) element;
        }
        if (element instanceof Map) {
            return new JSONObject((Map<?, ?>) element);
        }
        return null;
    }

    private LocalDateTime parseSaveTime(String text) {
        if (!notBlank(text)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(text.trim(), SAVE_TIME_FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private LocalDate parseSaveDate(String text) {
        if (!notBlank(text)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private void collectUploadPath(List<String> images, String url) {
        if (!notBlank(url) || !url.startsWith("/uploads/")) {
            return;
        }
        String path = url.substring("/uploads/".length());
        if (!images.contains(path)) {
            images.add(path);
        }
    }

    /** 角色使用的服务商名（导出存档用名字，跨环境导入才有意义） */
    private String providerNameOf(Long providerId) {
        if (providerId == null) {
            return null;
        }
        AiProvider provider = aiProviderMapper.selectById(providerId);
        return provider == null ? null : provider.getName();
    }

    /**
     * 清空世界进度：删掉该世界的全部记录，并把角色恢复成"刚开局"的状态。
     * 世界设定（名称/简介/地图/世界观）、地图地点、角色卡（人设/立绘/模型绑定）都保留。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resetWorld(Long worldId) {
        requireWorld(worldId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("acts", actMapper.delete(new LambdaQueryWrapper<SandboxAct>().eq(SandboxAct::getWorldId, worldId)));
        stats.put("memories", memoryMapper.delete(new LambdaQueryWrapper<SandboxMemory>()
                .eq(SandboxMemory::getWorldId, worldId)));
        stats.put("items", itemMapper.delete(new LambdaQueryWrapper<SandboxItem>()
                .eq(SandboxItem::getWorldId, worldId)));
        stats.put("relations", relationMapper.delete(new LambdaQueryWrapper<SandboxRelation>()
                .eq(SandboxRelation::getWorldId, worldId)));
        stats.put("whispers", interactionMapper.delete(new LambdaQueryWrapper<SandboxInteraction>()
                .eq(SandboxInteraction::getWorldId, worldId)));
        stats.put("gifts", giftMapper.delete(new LambdaQueryWrapper<SandboxGift>()
                .eq(SandboxGift::getWorldId, worldId)));
        stats.put("coinLogs", coinLogMapper.delete(new LambdaQueryWrapper<SandboxCoinLog>()
                .eq(SandboxCoinLog::getWorldId, worldId)));
        stats.put("news", newsMapper.delete(new LambdaQueryWrapper<SandboxNews>()
                .eq(SandboxNews::getWorldId, worldId)));
        stats.put("shopOrders", shopOrderMapper.delete(new LambdaQueryWrapper<SandboxShopOrder>()
                .eq(SandboxShopOrder::getWorldId, worldId)));
        stats.put("shopItems", shopItemMapper.delete(new LambdaQueryWrapper<SandboxShopItem>()
                .eq(SandboxShopItem::getWorldId, worldId)));

        // 位置：每个角色随机分配到一个地点、并在该地点范围内随机落点（避免清空后全挤在同一个地方）
        List<SandboxLocation> locations = locations(worldId);
        Random random = new Random();
        // 下次行动时间：给一个间隔之后再开始，避免清空瞬间所有角色一起调用 AI
        int delay = Math.max(15, intConfig("sandbox_interval_max", 75));
        LocalDateTime next = LocalDateTime.now().plusMinutes(delay);
        int count = 0;
        for (SandboxCharacter character : characters(worldId)) {
            SandboxLocation spot = randomLocation(locations, random);
            int[] point = randomPointIn(spot, random);
            characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                    .eq(SandboxCharacter::getId, character.getId())
                    .set(SandboxCharacter::getX, point[0])
                    .set(SandboxCharacter::getY, point[1])
                    .set(SandboxCharacter::getLocationName, spot == null ? null : spot.getName())
                    .set(SandboxCharacter::getSubLocation, null)
                    // 清空「当前目标」：新的一局让 AI 自己重新立一个目标
                    .set(SandboxCharacter::getGoal, null)
                    .set(SandboxCharacter::getStatusJson, RESET_STATUS_JSON)
                    .set(SandboxCharacter::getCoins, 0)
                    .set(SandboxCharacter::getCombatPower, COMBAT_POWER_DEFAULT)
                    .set(SandboxCharacter::getLastRunTime, null)
                    .set(SandboxCharacter::getNextRunTime, next)
                    .set(SandboxCharacter::getNextReason, null)
                    .set(SandboxCharacter::getLastError, null)
                    .set(SandboxCharacter::getFailCount, 0));
            count++;
        }
        stats.put("characters", count);
        stats.put("nextRunTime", next.format(DATE_TIME_FORMATTER));
        log.info("沙盒世界 {} 已清空：{}", worldId, stats);
        return stats;
    }

    private JSONArray parseJsonArray(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return JSONUtil.parseArray(text.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 记录归属世界：优先用传入的值，其次按角色反查，最后退回第一个世界。
     * 好感度、背包物品、每日记忆这类记录都挂在角色身上，角色属于哪个世界就记哪个世界。
     */
    private Long resolveWorldId(Long preferred, Long characterId) {
        if (preferred != null) {
            return preferred;
        }
        if (characterId != null) {
            SandboxCharacter owner = characterMapper.selectById(characterId);
            if (owner != null && owner.getWorldId() != null) {
                return owner.getWorldId();
            }
        }
        return worldId(null);
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
