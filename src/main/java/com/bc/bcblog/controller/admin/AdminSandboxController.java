package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxCoinLog;
import com.bc.bcblog.entity.SandboxInteraction;
import com.bc.bcblog.entity.SandboxLocation;
import com.bc.bcblog.entity.SandboxRelation;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.service.SandboxService;
import com.bc.bcblog.vo.SandboxRelationVO;
import com.bc.bcblog.vo.SandboxRunAllVO;
import com.bc.bcblog.vo.SandboxSettingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台沙盒世界管理接口：地图、地点、角色、行动日志与旅人低语。 */
@RestController
@RequestMapping("/api/admin/sandbox")
@RequiredArgsConstructor
public class AdminSandboxController {

    private final SandboxService sandboxService;

    // ---------------- 世界与地图 ----------------

    @GetMapping("/world")
    public Result<SandboxWorld> world() {
        return Result.ok(sandboxService.world());
    }

    @PostMapping("/world")
    public Result<Void> saveWorld(@RequestBody SandboxWorld world) {
        sandboxService.saveWorld(world);
        return Result.ok();
    }

    // ---------------- 地点 ----------------

    @GetMapping("/locations")
    public Result<List<SandboxLocation>> locations() {
        return Result.ok(sandboxService.locations());
    }

    @PostMapping("/locations")
    public Result<SandboxLocation> saveLocation(@RequestBody SandboxLocation location) {
        return Result.ok(sandboxService.saveLocation(location));
    }

    @DeleteMapping("/locations/{id}")
    public Result<Void> deleteLocation(@PathVariable Long id) {
        sandboxService.deleteLocation(id);
        return Result.ok();
    }

    // ---------------- 运行参数 ----------------

    @GetMapping("/settings")
    public Result<SandboxSettingVO> settings() {
        return Result.ok(sandboxService.settings());
    }

    @PostMapping("/settings")
    public Result<Void> saveSettings(@RequestBody SandboxSettingVO vo) {
        sandboxService.saveSettings(vo);
        return Result.ok();
    }

    // ---------------- 角色 ----------------

    @GetMapping("/characters")
    public Result<List<SandboxCharacter>> characters() {
        return Result.ok(sandboxService.characters());
    }

    @PostMapping("/characters")
    public Result<SandboxCharacter> saveCharacter(@RequestBody SandboxCharacter character) {
        return Result.ok(sandboxService.saveCharacter(character));
    }

    @DeleteMapping("/characters/{id}")
    public Result<Void> deleteCharacter(@PathVariable Long id) {
        sandboxService.deleteCharacter(id);
        return Result.ok();
    }

    /** 立即执行一次：管理员调试用，不占用每日额度 */
    @PostMapping("/characters/{id}/run")
    public Result<SandboxAct> run(@PathVariable Long id) {
        return Result.ok(sandboxService.runOnce(id, true));
    }

    /** 一键让全部启用角色行动一轮：多角色同时行动，便于互相遇见与互动 */
    @PostMapping("/run-all")
    public Result<SandboxRunAllVO> runAll() {
        return Result.ok(sandboxService.runAll());
    }

    // ---------------- 行动日志 ----------------

    @GetMapping("/acts")
    public Result<PageResult<SandboxAct>> acts(@RequestParam(required = false) Long characterId,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.acts(characterId, page, size));
    }

    @DeleteMapping("/acts/{id}")
    public Result<Void> deleteAct(@PathVariable Long id) {
        sandboxService.deleteAct(id);
        return Result.ok();
    }

    // ---------------- 旅人低语 ----------------

    @GetMapping("/interactions")
    public Result<PageResult<SandboxInteraction>> interactions(@RequestParam(required = false) Long characterId,
                                                               @RequestParam(defaultValue = "1") long page,
                                                               @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.interactions(characterId, page, size));
    }

    @DeleteMapping("/interactions/{id}")
    public Result<Void> deleteInteraction(@PathVariable Long id) {
        sandboxService.deleteInteraction(id);
        return Result.ok();
    }

    // ---------------- 金币流水 ----------------

    @GetMapping("/coins")
    public Result<PageResult<SandboxCoinLog>> coinLogs(@RequestParam(required = false) Long characterId,
                                                       @RequestParam(defaultValue = "1") long page,
                                                       @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.coinLogs(characterId, page, size));
    }

    @DeleteMapping("/coins/{id}")
    public Result<Void> deleteCoinLog(@PathVariable Long id) {
        sandboxService.deleteCoinLog(id);
        return Result.ok();
    }

    // ---------------- 角色好感度 ----------------

    @GetMapping("/relations")
    public Result<List<SandboxRelationVO>> relations(@RequestParam(required = false) Long characterId) {
        return Result.ok(sandboxService.relationList(characterId));
    }

    @PostMapping("/relations")
    public Result<Void> saveRelation(@RequestBody SandboxRelation relation) {
        sandboxService.saveRelation(relation);
        return Result.ok();
    }

    @DeleteMapping("/relations/{id}")
    public Result<Void> deleteRelation(@PathVariable Long id) {
        sandboxService.deleteRelation(id);
        return Result.ok();
    }
}
