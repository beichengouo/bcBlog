package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.GamePackageService;
import com.bc.bcblog.vo.GamePackageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/admin/game-packages")
@RequiredArgsConstructor
public class AdminGamePackageController {
    private final GamePackageService service;
    private final com.bc.bcblog.service.GamePackageUploadService uploads;
    @PostMapping("/sessions")
    public Result<com.bc.bcblog.service.GamePackageUploadService.Session> start(@RequestBody com.bc.bcblog.service.GamePackageUploadService.Session input) throws java.io.IOException {
        return Result.ok(uploads.start(input));
    }
    @GetMapping("/sessions/{id}")
    public Result<com.bc.bcblog.service.GamePackageUploadService.Session> status(@PathVariable String id) throws java.io.IOException { return Result.ok(uploads.status(id)); }
    @PostMapping("/sessions/{id}/chunks/{index}")
    public Result<Void> chunk(@PathVariable String id, @PathVariable int index, @RequestParam("file") MultipartFile file) throws java.io.IOException {
        uploads.chunk(id, index, file); return Result.ok();
    }
    @PostMapping("/sessions/{id}/complete")
    public Result<GamePackageVO> complete(@PathVariable String id) throws java.io.IOException { return Result.ok(uploads.complete(id)); }
    @GetMapping
    public Result<List<GamePackageVO>> list() { return Result.ok(service.list(false)); }
    @PostMapping("/{platform}")
    public Result<GamePackageVO> upload(@PathVariable String platform, @RequestParam String version,
            @RequestParam(defaultValue = "") String notes, @RequestParam("file") MultipartFile file) {
        return Result.ok(service.upload(platform, version, notes, file));
    }
    @PutMapping("/{platform}/published")
    public Result<GamePackageVO> publish(@PathVariable String platform, @RequestParam boolean published) {
        return Result.ok(service.publish(platform, published));
    }
}
