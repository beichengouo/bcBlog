package com.bc.bcblog.controller.portal;

import cn.hutool.json.JSONObject;
import com.bc.bcblog.common.Result;
import com.bc.bcblog.service.GitalkService;
import com.bc.bcblog.vo.GitalkCommentVO;
import com.bc.bcblog.vo.GitalkConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 前台 Gitalk 评论接口。 */
@RestController
@RequestMapping("/api/portal/gitalk")
@RequiredArgsConstructor
public class PortalGitalkController {

    private final GitalkService gitalkService;

    /** 前端渲染 Gitalk 需要的配置（Gitalk 本身需要 clientSecret，无法只放后端）。 */
    @GetMapping("/config")
    public Result<GitalkConfigVO> config() {
        return Result.ok(gitalkService.getConfig());
    }

    /** GitHub OAuth 代理，Gitalk 用它把 code 换成 access_token。 */
    @PostMapping("/oauth")
    public JSONObject oauth(@RequestBody Map<String, String> body) {
        return gitalkService.exchangeToken(body.get("code"));
    }

    /** 最近评论，默认 10 条。 */
    @GetMapping("/recent-comments")
    public Result<List<GitalkCommentVO>> recentComments(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(gitalkService.recentComments(limit));
    }
}
