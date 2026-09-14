package com.bc.bcblog.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SysConfig;
import com.bc.bcblog.mapper.SysConfigMapper;
import com.bc.bcblog.service.GitalkService;
import com.bc.bcblog.vo.GitalkCommentVO;
import com.bc.bcblog.vo.GitalkConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Gitalk 配置与 OAuth 代理实现。 */
@Service
@RequiredArgsConstructor
public class GitalkServiceImpl implements GitalkService {

    private static final String KEY_CLIENT_ID = "gitalk_client_id";
    private static final String KEY_CLIENT_SECRET = "gitalk_client_secret";
    private static final String KEY_REPO = "gitalk_repo";
    private static final String KEY_OWNER = "gitalk_owner";
    private static final String KEY_ADMIN = "gitalk_admin";

    /** 最近评论缓存 5 分钟，避免频繁调用 GitHub API 触发限流 */
    private static final long COMMENT_CACHE_MILLIS = 5 * 60 * 1000L;
    private volatile List<GitalkCommentVO> commentCache;
    private volatile long commentCacheAt;

    private final SysConfigMapper configMapper;

    @Override
    public GitalkConfigVO getConfig() {
        Map<String, String> map = loadMap();
        GitalkConfigVO vo = new GitalkConfigVO();
        vo.setClientId(map.get(KEY_CLIENT_ID));
        vo.setClientSecret(map.get(KEY_CLIENT_SECRET));
        vo.setRepo(map.get(KEY_REPO));
        vo.setOwner(map.get(KEY_OWNER));
        String admin = map.get(KEY_ADMIN);
        if (admin == null || admin.trim().isEmpty()) {
            vo.setAdmin(new ArrayList<>());
        } else {
            vo.setAdmin(Arrays.stream(admin.split(",")).map(String::trim)
                    .filter(s -> !s.isEmpty()).collect(Collectors.toList()));
        }
        return vo;
    }

    @Override
    public void save(GitalkConfigVO vo) {
        upsert(KEY_CLIENT_ID, vo.getClientId());
        upsert(KEY_CLIENT_SECRET, vo.getClientSecret());
        upsert(KEY_REPO, vo.getRepo());
        upsert(KEY_OWNER, vo.getOwner());
        if (vo.getAdmin() != null) {
            upsert(KEY_ADMIN, String.join(",", vo.getAdmin()));
        }
    }

    @Override
    public JSONObject exchangeToken(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException("缺少 GitHub 授权 code");
        }
        GitalkConfigVO config = getConfig();
        if (isBlank(config.getClientId()) || isBlank(config.getClientSecret())) {
            throw new BusinessException("请先配置 Gitalk 的 Client ID 和 Client Secret");
        }
        JSONObject body = new JSONObject();
        body.set("client_id", config.getClientId().trim());
        body.set("client_secret", config.getClientSecret().trim());
        body.set("code", code.trim());

        HttpResponse resp;
        try {
            resp = HttpRequest.post("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout(15000)
                    .execute();
        } catch (Exception e) {
            throw new BusinessException("GitHub 授权失败：" + e.getMessage());
        }
        if (resp.getStatus() != 200) {
            throw new BusinessException("GitHub 授权失败（HTTP " + resp.getStatus() + "）");
        }
        return JSONUtil.parseObj(resp.body());
    }

    @Override
    public List<GitalkCommentVO> recentComments(int limit) {
        int size = Math.max(1, Math.min(30, limit));
        List<GitalkCommentVO> cache = commentCache;
        if (cache != null && System.currentTimeMillis() - commentCacheAt < COMMENT_CACHE_MILLIS) {
            return cache.size() <= size ? new ArrayList<>(cache) : new ArrayList<>(cache.subList(0, size));
        }

        List<GitalkCommentVO> result = new ArrayList<>();
        GitalkConfigVO config = getConfig();
        if (isBlank(config.getOwner()) || isBlank(config.getRepo())) {
            return result;
        }
        boolean ok = false;
        try {
            String base = "https://api.github.com/repos/" + config.getOwner().trim() + "/" + config.getRepo().trim();
            // 先取 Gitalk 的 issues，建立 issue 编号 -> issue 信息映射，用于回链到文章
            Map<Long, JSONObject> issueMap = new HashMap<>();
            HttpResponse issuesResp = githubGet(base + "/issues?labels=Gitalk&state=all&per_page=100&sort=created&direction=desc", config);
            if (issuesResp != null && issuesResp.getStatus() == 200) {
                ok = true;
                JSONArray issues = JSONUtil.parseArray(issuesResp.body());
                for (int i = 0; i < issues.size(); i++) {
                    JSONObject issue = issues.getJSONObject(i);
                    issueMap.put(issue.getLong("number"), issue);
                }
            }
            // 再取最近评论
            HttpResponse resp = githubGet(base + "/issues/comments?per_page=" + size + "&sort=created&direction=desc", config);
            if (resp == null || resp.getStatus() != 200) {
                if (ok) {
                    commentCache = new ArrayList<>(result);
                    commentCacheAt = System.currentTimeMillis();
                }
                return result;
            }
            ok = true;
            JSONArray comments = JSONUtil.parseArray(resp.body());
            for (int i = 0; i < comments.size() && result.size() < size; i++) {
                JSONObject c = comments.getJSONObject(i);
                GitalkCommentVO vo = new GitalkCommentVO();
                vo.setId(c.getLong("id"));
                JSONObject user = c.getJSONObject("user");
                if (user != null) {
                    vo.setAuthor(user.getStr("login"));
                    vo.setAvatar(user.getStr("avatar_url"));
                }
                vo.setBody(c.getStr("body"));
                vo.setCreatedAt(c.getStr("created_at"));
                vo.setHtmlUrl(c.getStr("html_url"));
                Long issueNumber = parseIssueNumber(c.getStr("issue_url"));
                vo.setIssueNumber(issueNumber);
                JSONObject issue = issueMap.get(issueNumber);
                if (issue != null) {
                    vo.setPageTitle(issue.getStr("title"));
                    vo.setPagePath(extractPagePath(issue.getStr("body")));
                }
                result.add(vo);
            }
        } catch (Exception ignored) {
            // GitHub 接口异常时返回空列表，不影响仪表盘其它数据
        }
        if (ok) {
            commentCache = new ArrayList<>(result);
            commentCacheAt = System.currentTimeMillis();
        }
        return result;
    }

    /** 调用 GitHub API，带 Client ID / Secret 基础认证以提升限额。 */
    private HttpResponse githubGet(String url, GitalkConfigVO config) {
        try {
            HttpRequest request = HttpRequest.get(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "bcBlog")
                    .timeout(15000);
            if (!isBlank(config.getClientId()) && !isBlank(config.getClientSecret())) {
                request.basicAuth(config.getClientId().trim(), config.getClientSecret().trim());
            }
            return request.execute();
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseIssueNumber(String issueUrl) {
        if (issueUrl == null || issueUrl.trim().isEmpty()) {
            return null;
        }
        String url = issueUrl.trim();
        int idx = url.lastIndexOf('/');
        if (idx < 0 || idx == url.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(url.substring(idx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从 Gitalk issue 的 body 中提取站内页面路径。 */
    private String extractPagePath(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = Pattern.compile("https?://[^\\s]+").matcher(body);
        if (!matcher.find()) {
            return null;
        }
        try {
            URI uri = URI.create(matcher.group());
            return uri.getPath();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private Map<String, String> loadMap() {
        Map<String, String> map = new HashMap<>();
        for (SysConfig c : configMapper.selectList(null)) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        return map;
    }

    private void upsert(String key, String value) {
        SysConfig existing = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key));
        if (existing == null) {
            SysConfig c = new SysConfig();
            c.setConfigKey(key);
            c.setConfigValue(value);
            configMapper.insert(c);
        } else {
            existing.setConfigValue(value);
            configMapper.updateById(existing);
        }
    }
}
