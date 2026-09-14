package com.bc.bcblog.controller.portal;

import cn.dev33.satoken.stp.StpUtil;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.ResourceService;
import com.bc.bcblog.vo.ResourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 前台智库资源接口（游客可访问）。 */
@RestController
@RequestMapping("/api/portal/resource")
@RequiredArgsConstructor
public class PortalResourceController {

    private final ResourceService resourceService;

    @GetMapping("/list")
    public Result<List<ResourceVO>> list() {
        return Result.ok(resourceService.portalList(currentUserId()));
    }

    @GetMapping("/{id}")
    public Result<ResourceVO> detail(@PathVariable Long id) {
        return Result.ok(resourceService.detail(id, currentUserId()));
    }

    /** 消耗积分解锁资源，返回资源链接和密码 */
    @PostMapping("/{id}/unlock")
    public Result<ResourceVO> unlock(@PathVariable Long id) {
        return Result.ok(resourceService.unlock(id, currentUserId()));
    }

    private Long currentUserId() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
