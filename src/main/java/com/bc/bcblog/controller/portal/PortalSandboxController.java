package com.bc.bcblog.controller.portal;

import cn.hutool.core.convert.Convert;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCoinLog;
import com.bc.bcblog.entity.SandboxInteraction;
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
    public Result<SandboxPortalVO> portal() {
        return Result.ok(sandboxService.portal());
    }

    /** 行动时间线（可按角色筛选） */
    @GetMapping("/acts")
    public Result<PageResult<SandboxAct>> acts(@RequestParam(required = false) Long characterId,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.acts(characterId, page, size));
    }

    /** 某个角色收到的旅人低语 */
    @GetMapping("/interactions")
    public Result<PageResult<SandboxInteraction>> interactions(@RequestParam Long characterId,
                                                               @RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.interactions(characterId, page, size));
    }

    /** 留下旅人低语：需要登录，并消耗积分 */
    @PostMapping("/whisper")
    public Result<SandboxInteraction> whisper(@RequestBody Map<String, Object> body) {
        Long characterId = Convert.toLong(body.get("characterId"), null);
        if (characterId == null) {
            return Result.fail(400, "缺少角色 ID");
        }
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
        int points = Convert.toInt(body.get("points"), 1);
        return Result.ok(sandboxService.contributeCoins(characterId, points));
    }

    /** 某个角色的金币流水 */
    @GetMapping("/coins")
    public Result<PageResult<SandboxCoinLog>> coins(@RequestParam Long characterId,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.coinLogs(characterId, page, size));
    }
}
