package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.AcgCoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台随机封面接口。 */
@RestController
@RequestMapping("/api/admin/acg-cover")
@RequiredArgsConstructor
public class AdminAcgCoverController {

    private final AcgCoverService acgCoverService;

    @GetMapping("/random")
    public Result<String> random() {
        return Result.ok(acgCoverService.randomCover());
    }
}
