package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.dto.PointGrantDTO;
import com.bc.bcblog.entity.SysPointLog;
import com.bc.bcblog.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 后台积分管理接口。 */
@RestController
@RequestMapping("/api/admin/point")
@RequiredArgsConstructor
public class AdminPointController {

    private final PointService pointService;

    /** 给指定用户或全部普通用户发放积分 */
    @PostMapping("/grant")
    public Result<Integer> grant(@RequestBody PointGrantDTO dto) {
        String type = dto.getType() == null || dto.getType().trim().isEmpty() ? "admin" : dto.getType().trim();
        String reason = dto.getReason() == null || dto.getReason().trim().isEmpty() ? "管理员发放" : dto.getReason().trim();
        int count = pointService.grant(dto.getUserIds(), dto.getPoints() == null ? 0 : dto.getPoints(), type, reason);
        return Result.ok(count);
    }

    /** 积分流水 */
    @GetMapping("/logs")
    public Result<PageResult<SysPointLog>> logs(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Long userId) {
        return Result.ok(pointService.pageAll(userId, page, size));
    }
}
