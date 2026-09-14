package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.GitalkService;
import com.bc.bcblog.vo.GitalkConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 后台 Gitalk 评论配置接口。 */
@RestController
@RequestMapping("/api/admin/gitalk")
@RequiredArgsConstructor
public class AdminGitalkController {

    private final GitalkService gitalkService;

    @GetMapping("/config")
    public Result<GitalkConfigVO> config() {
        return Result.ok(gitalkService.getConfig());
    }

    @PostMapping("/config")
    public Result<Void> save(@RequestBody GitalkConfigVO vo) {
        gitalkService.save(vo);
        return Result.ok();
    }
}
