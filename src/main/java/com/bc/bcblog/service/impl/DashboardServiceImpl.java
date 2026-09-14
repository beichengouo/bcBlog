package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.mapper.BlogArticleMapper;
import com.bc.bcblog.mapper.BlogCategoryMapper;
import com.bc.bcblog.mapper.BlogCommentMapper;
import com.bc.bcblog.mapper.BlogPhotoMapper;
import com.bc.bcblog.mapper.BlogResourceMapper;
import com.bc.bcblog.mapper.BlogTagMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.DashboardService;
import com.bc.bcblog.service.GitalkService;
import com.bc.bcblog.service.SystemInfoService;
import com.bc.bcblog.service.VisitService;
import com.bc.bcblog.vo.DashboardStatsVO;
import com.bc.bcblog.vo.SystemInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** 仪表盘统计服务。 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final BlogArticleMapper articleMapper;
    private final BlogCategoryMapper categoryMapper;
    private final BlogTagMapper tagMapper;
    private final BlogCommentMapper commentMapper;
    private final BlogPhotoMapper photoMapper;
    private final BlogResourceMapper resourceMapper;
    private final SysUserMapper sysUserMapper;
    private final SystemInfoService systemInfoService;
    private final GitalkService gitalkService;
    private final VisitService visitService;

    @Override
    public DashboardStatsVO stats() {
        DashboardStatsVO vo = new DashboardStatsVO();
        vo.setArticleCount(articleMapper.selectCount(null));
        vo.setPublishedCount(articleMapper.selectCount(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getStatus, 1)));
        vo.setDraftCount(articleMapper.selectCount(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getStatus, 0)));
        vo.setCategoryCount(categoryMapper.selectCount(null));
        vo.setTagCount(tagMapper.selectCount(null));
        vo.setCommentCount(commentMapper.selectCount(null));
        vo.setPhotoCount(photoMapper.selectCount(null));
        vo.setResourceCount(resourceMapper.selectCount(null));
        vo.setAdminCount(sysUserMapper.selectCount(null));

        // 总浏览量：对文章 view_count 求和
        List<Object> objs = articleMapper.selectObjs(new QueryWrapper<BlogArticle>()
                .select("COALESCE(SUM(view_count), 0)"));
        long view = 0L;
        if (objs != null && !objs.isEmpty() && objs.get(0) != null) {
            view = ((Number) objs.get(0)).longValue();
        }
        vo.setViewCount(view);

        // 系统运行信息
        SystemInfoVO info = systemInfoService.getSystemInfo();
        vo.setUptimeSeconds(info.getUptimeSeconds());
        if (info.getStartTime() != null) {
            vo.setStartTime(info.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        vo.setVisitStats(visitService.stats());

        // 热门文章 / 最新文章
        vo.setTopArticles(toBriefList(articleMapper.selectList(new LambdaQueryWrapper<BlogArticle>()
                .orderByDesc(BlogArticle::getViewCount)
                .orderByDesc(BlogArticle::getId)
                .last("LIMIT 5"))));
        vo.setRecentArticles(toBriefList(articleMapper.selectList(new LambdaQueryWrapper<BlogArticle>()
                .orderByDesc(BlogArticle::getCreateTime)
                .orderByDesc(BlogArticle::getId)
                .last("LIMIT 5"))));

        // Gitalk 最近评论（失败时返回空列表）
        try {
            vo.setRecentComments(gitalkService.recentComments(10));
        } catch (Exception e) {
            vo.setRecentComments(new ArrayList<>());
        }
        return vo;
    }

    private List<DashboardStatsVO.ArticleBrief> toBriefList(List<BlogArticle> articles) {
        if (articles == null) {
            return new ArrayList<>();
        }
        return articles.stream().map(a -> {
            DashboardStatsVO.ArticleBrief brief = new DashboardStatsVO.ArticleBrief();
            brief.setId(a.getId());
            brief.setTitle(a.getTitle());
            brief.setCover(a.getCover());
            brief.setViewCount(a.getViewCount() == null ? 0L : a.getViewCount().longValue());
            brief.setCreateTime(a.getCreateTime() == null ? null
                    : a.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            return brief;
        }).collect(Collectors.toList());
    }
}
