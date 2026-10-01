package com.bc.bcblog.controller.portal;

import cn.hutool.core.convert.Convert;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCoinLog;
import com.bc.bcblog.entity.SandboxInteraction;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.service.SandboxService;
import com.bc.bcblog.vo.SandboxCoinResultVO;
import com.bc.bcblog.vo.SandboxPortalVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

/**
 * 前台沙盒接口。
 *
 * 地图、角色与行动时间线游客都可以查看；
 * 只有「旅人低语」需要登录，并且会消耗积分。
 */
@RestController
@RequestMapping("/api/portal/sandbox")
@RequiredArgsConstructor
public class PortalSandboxController {

    private final SandboxService sandboxService;

    /** 沙盒首页：世界、地图地点、角色与最近行动 */
    @GetMapping
    public Result<SandboxPortalVO> portal(@RequestParam(required = false) Long worldId) {
        // 不传 worldId 时用第一个「前台可见」的世界（兼容旧链接）；
        // 传了没开放的世界会直接报错——服务层里统一校验（见 resolvePortalWorldId）
        return Result.ok(sandboxService.portal(worldId));
    }

    /** 前台可切换的世界列表：只列「前台可见」的，包含已停止运行的（游客可以只看历史） */
    @GetMapping("/worlds")
    public Result<List<SandboxWorld>> worlds() {
        return Result.ok(sandboxService.visibleWorlds());
    }

    /** 行动时间线（可按角色筛选） */
    @GetMapping("/acts")
    public Result<PageResult<SandboxAct>> acts(@RequestParam(required = false) Long characterId,
                                               @RequestParam(required = false) String locationName,
                                               @RequestParam(required = false) Long worldId,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        // 角色已知时按角色所在世界校验；否则按 worldId 解析出「前台可见」的世界
        Long wid = characterId != null
                ? sandboxService.requirePortalVisibleCharacter(characterId)
                : sandboxService.resolvePortalWorldId(worldId);
        if (wid == null) {
            // 一个「前台可见」的世界都没有：别再不带世界条件去查行动记录，那会把隐藏世界的内容全捞出来
            return Result.ok(PageResult.of(0, java.util.Collections.emptyList()));
        }
        PageResult<SandboxAct> result = sandboxService.acts(characterId, locationName, wid, page, size);
        // 前台不需要 AI 原始回复（里面可能含 <draft>/<review> 这类提示词段落），这里直接抹掉再返回
        if (result != null && result.getList() != null) {
            result.getList().forEach(act -> act.setRawResponse(null));
        }
        return Result.ok(result);
    }

    /** 某个角色收到的旅人低语 */
    /** 旅人集市：把商品赠送给某个角色（扣积分、进角色背包） */
    @PostMapping("/shop/buy")
    public Result<com.bc.bcblog.entity.SandboxShopOrder> buyShopItem(@RequestBody Map<String, Object> body) {
        Long itemId = Convert.toLong(body.get("itemId"), null);
        Long characterId = Convert.toLong(body.get("characterId"), null);
        if (itemId == null || characterId == null) {
            return Result.fail(400, "请选择要赠送的商品和角色");
        }
        sandboxService.requirePortalVisibleCharacter(characterId);
        return Result.ok(sandboxService.buyShopItem(itemId, characterId));
    }

    @GetMapping("/interactions")
    public Result<PageResult<SandboxInteraction>> interactions(@RequestParam Long characterId,
                                                               @RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "10") long size) {
        sandboxService.requirePortalVisibleCharacter(characterId);
        return Result.ok(sandboxService.interactions(characterId, page, size));
    }

    /** 留下旅人低语：需要登录，并消耗积分 */
    @PostMapping("/whisper")
    public Result<SandboxInteraction> whisper(@RequestBody Map<String, Object> body) {
        Long characterId = Convert.toLong(body.get("characterId"), null);
        if (characterId == null) {
            return Result.fail(400, "缺少角色 ID");
        }
        sandboxService.requirePortalVisibleCharacter(characterId);
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
        return Result.ok(sandboxService.whisper(characterId, content));
    }

    /** 用积分为角色贡献金币：需要登录，按后台配置的比例换算 */
    @PostMapping("/coin")
    public Result<SandboxCoinResultVO> coin(@RequestBody Map<String, Object> body) {
        Long characterId = Convert.toLong(body.get("characterId"), null);
        if (characterId == null) {
            return Result.fail(400, "缺少角色 ID");
        }
        sandboxService.requirePortalVisibleCharacter(characterId);
        int points = Convert.toInt(body.get("points"), 1);
        return Result.ok(sandboxService.contributeCoins(characterId, points));
    }

    /** 某个角色的金币流水 */
    @GetMapping("/coins")
    public Result<PageResult<SandboxCoinLog>> coins(@RequestParam Long characterId,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        sandboxService.requirePortalVisibleCharacter(characterId);
        return Result.ok(sandboxService.coinLogs(characterId, page, size));
    }
}
