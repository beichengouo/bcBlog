package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.GamePackageService;
import com.bc.bcblog.vo.GamePackageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/portal/game-packages")
@RequiredArgsConstructor
public class PortalGamePackageController {
    private final GamePackageService service;
    @GetMapping
    public Result<List<GamePackageVO>> list() { return Result.ok(service.list(true)); }
    @GetMapping("/{platform}/download")
    public ResponseEntity<Resource> download(@PathVariable String platform) {
        GamePackageService.StoredPackage record = service.downloadable(platform);
        GamePackageVO info = record.getInfo();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(info.getSize()).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(info.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(service.downloadPath(record)));
    }
}
