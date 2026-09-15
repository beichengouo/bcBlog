package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
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
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.service.SandboxService;
import com.bc.bcblog.vo.SandboxRelationVO;
import com.bc.bcblog.vo.SandboxCharacterDraftVO;
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
import java.util.Map;
import cn.hutool.core.convert.Convert;

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

    /** AI 一键创作角色：结合当前世界观生成角色卡，供新增角色表单填充 */
    @PostMapping("/characters/generate")
    public Result<SandboxCharacterDraftVO> generateCharacter(@RequestBody SandboxCharacterGenerateDTO dto) {
        return Result.ok(sandboxService.generateCharacter(dto));
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
                                               @RequestParam(required = false) String locationName,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.acts(characterId, locationName, page, size));
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

    // ---------------- 每日记忆 ----------------

    @GetMapping("/memories")
    public Result<PageResult<SandboxMemory>> memories(@RequestParam(required = false) Long characterId,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.memoryPage(characterId, page, size));
    }

    @PostMapping("/memories")
    public Result<Void> saveMemory(@RequestBody SandboxMemory memory) {
        sandboxService.saveMemory(memory);
        return Result.ok();
    }

    @DeleteMapping("/memories/{id}")
    public Result<Void> deleteMemory(@PathVariable Long id) {
        sandboxService.deleteMemory(id);
        return Result.ok();
    }

    /** 立即为所有角色生成当天记忆（调试用，不等定时任务） */
    @PostMapping("/memories/summarize")
    public Result<Void> summarize(@RequestParam(required = false) String date) {
        sandboxService.summarizeOn(date);
        return Result.ok();
    }

    // ---------------- 角色背包 ----------------

    @GetMapping("/items")
    public Result<List<SandboxItem>> items(@RequestParam Long characterId) {
        return Result.ok(sandboxService.items(characterId));
    }

    @PostMapping("/items")
    public Result<SandboxItem> saveItem(@RequestBody SandboxItem item) {
        return Result.ok(sandboxService.saveItem(item));
    }

    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        sandboxService.deleteItem(id);
        return Result.ok();
    }

    // ---------------- 旅人纪闻 ----------------

    @GetMapping("/news")
    public Result<PageResult<SandboxNews>> news(@RequestParam(required = false) String date,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.newsPage(date, page, size));
    }

    @PostMapping("/news")
    public Result<Void> saveNews(@RequestBody SandboxNews news) {
        sandboxService.saveNews(news);
        return Result.ok();
    }

    @DeleteMapping("/news/{id}")
    public Result<Void> deleteNews(@PathVariable Long id) {
        sandboxService.deleteNews(id);
        return Result.ok();
    }

    /** 由 AI 生成若干条当天纪闻 */
    @PostMapping("/news/generate")
    public Result<Integer> generateNews(@RequestBody Map<String, Object> body) {
        Integer count = Convert.toInt(body.get("count"), null);
        Long providerId = Convert.toLong(body.get("providerId"), null);
        String model = body.get("model") == null ? null : String.valueOf(body.get("model"));
        return Result.ok(sandboxService.generateNews(count, providerId, model));
    }
}
