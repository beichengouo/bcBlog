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
import org.springframework.web.bind.annotation.PutMapping;
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
    public Result<SandboxWorld> world(@RequestParam(required = false) Long worldId) {
        return Result.ok(sandboxService.world(worldId));
    }

    /** 全部世界（后台世界管理用） */
    @GetMapping("/worlds")
    public Result<List<SandboxWorld>> worlds() {
        return Result.ok(sandboxService.worlds());
    }

    @PostMapping("/world")
    public Result<Void> saveWorld(@RequestBody SandboxWorld world) {
        sandboxService.saveWorld(world);
        return Result.ok();
    }

    /** 删除世界：连同它的角色、地点、行动、记忆、背包、好感度、纪闻、低语、金币流水一起清掉 */
    @DeleteMapping("/world/{id}")
    public Result<Void> deleteWorld(@PathVariable Long id) {
        sandboxService.deleteWorld(id);
        return Result.ok();
    }

    /** 切换「是否运行」：关闭后该世界不再自动行动，前台可以只看历史 */
    @PutMapping("/world/{id}/enabled")
    public Result<Void> setWorldEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        sandboxService.setWorldEnabled(id, enabled);
        return Result.ok();
    }

    /** 切换「前台是否可见」 */
    @PutMapping("/world/{id}/visible")
    public Result<Void> setWorldVisible(@PathVariable Long id, @RequestParam Integer visible) {
        sandboxService.setWorldVisible(id, visible);
        return Result.ok();
    }

    /**
     * 导出存档：下载一个 zip（world.json + 地图/立绘/图标等图片）。
     * includeRawResponse=true 时连 AI 原始输出一起导出（体积会大很多）。
     */
    @GetMapping("/world/{id}/export")
    public org.springframework.http.ResponseEntity<byte[]> exportWorld(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean includeRawResponse) {
        byte[] data = sandboxService.exportWorld(id, includeRawResponse);
        String filename = "sandbox-world-" + id + "-" + java.time.LocalDate.now() + ".zip";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"");
        return new org.springframework.http.ResponseEntity<>(data, headers, org.springframework.http.HttpStatus.OK);
    }

    /**
     * 清空世界进度：删除该世界的全部记录，并把角色状态与位置恢复默认。
     * 世界设定、地图地点、角色卡都会保留（相当于"新开一局"）。
     */
    @PostMapping("/world/{id}/reset")
    public Result<Map<String, Object>> resetWorld(@PathVariable Long id) {
        return Result.ok(sandboxService.resetWorld(id));
    }

    /**
     * 导入存档。
     * overwrite=false（推荐）：把存档导入成一个新世界「xxx（导入）」，不动现有数据；
     * overwrite=true：先清空 targetWorldId 指定世界的进度，再把存档写进去（真正的"读档"）。
     */
    @PostMapping("/world/import")
    public Result<Map<String, Object>> importWorld(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(required = false) Long targetWorldId,
            @RequestParam(defaultValue = "false") boolean overwrite) {
        return Result.ok(sandboxService.importWorld(file, targetWorldId, overwrite));
    }

    // ---------------- 地点 ----------------

    @GetMapping("/locations")
    public Result<List<SandboxLocation>> locations(@RequestParam(required = false) Long worldId) {
        return Result.ok(sandboxService.locations(worldId));
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
    public Result<List<SandboxCharacter>> characters(@RequestParam(required = false) Long worldId) {
        return Result.ok(sandboxService.characters(worldId));
    }

    @PostMapping("/characters")
    public Result<SandboxCharacter> saveCharacter(@RequestBody SandboxCharacter character) {
        return Result.ok(sandboxService.saveCharacter(character));
    }

    /** AI 一键创作角色：结合当前世界观生成角色卡，供新增角色表单填充 */
    @PostMapping("/characters/generate")
    public Result<SandboxCharacterDraftVO> generateCharacter(@RequestBody SandboxCharacterGenerateDTO dto,
                                                             @RequestParam(required = false) Long worldId) {
        return Result.ok(sandboxService.generateCharacter(dto, worldId));
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
    public Result<SandboxRunAllVO> runAll(@RequestParam(required = false) Long worldId) {
        return Result.ok(sandboxService.runAll(worldId));
    }

    // ---------------- 行动日志 ----------------

    @GetMapping("/acts")
    public Result<PageResult<SandboxAct>> acts(@RequestParam(required = false) Long characterId,
                                               @RequestParam(required = false) String locationName,
                                               @RequestParam(required = false) Long worldId,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.acts(characterId, locationName, worldId, page, size));
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
                                                @RequestParam(required = false) Long worldId,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.newsPage(date, page, size, worldId));
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
        Long worldId = Convert.toLong(body.get("worldId"), null);
        return Result.ok(sandboxService.generateNews(count, providerId, model, worldId));
    }

    // ---------------- 旅人集市 ----------------

    /** 后台：最新一批集市商品（含已下架的） */
    @GetMapping("/shop")
    public Result<List<com.bc.bcblog.entity.SandboxShopItem>> shop(@RequestParam(required = false) Long worldId) {
        return Result.ok(sandboxService.shopItemsForAdmin(worldId));
    }

    /** 后台：手动上架 / 编辑商品 */
    @PostMapping("/shop")
    public Result<com.bc.bcblog.entity.SandboxShopItem> saveShopItem(
            @RequestBody com.bc.bcblog.entity.SandboxShopItem item) {
        return Result.ok(sandboxService.saveShopItem(item));
    }

    @DeleteMapping("/shop/{id}")
    public Result<Void> deleteShopItem(@PathVariable Long id) {
        sandboxService.deleteShopItem(id);
        return Result.ok();
    }

    /** 后台：立即生成一批新商品 */
    @PostMapping("/shop/generate")
    public Result<Integer> generateShop(@RequestBody Map<String, Object> body) {
        Integer count = Convert.toInt(body.get("count"), null);
        Long providerId = Convert.toLong(body.get("providerId"), null);
        String model = body.get("model") == null ? null : String.valueOf(body.get("model"));
        Long worldId = Convert.toLong(body.get("worldId"), null);
        return Result.ok(sandboxService.generateShopItems(count, providerId, model, worldId));
    }

    /** 后台：购买记录 */
    @GetMapping("/shop/orders")
    public Result<PageResult<com.bc.bcblog.entity.SandboxShopOrder>> shopOrders(
            @RequestParam(required = false) Long worldId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(sandboxService.shopOrders(worldId, page, size));
    }

    /** 后台：今日集市统计（卖出件数 / 回收积分） */
    @GetMapping("/shop/stats")
    public Result<Map<String, Object>> shopStats(@RequestParam(required = false) Long worldId) {
        return Result.ok(sandboxService.shopStats(worldId));
    }
}
