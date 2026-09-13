package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.mapper.BlogArticleMapper;
import com.bc.bcblog.mapper.BlogCategoryMapper;
import com.bc.bcblog.mapper.BlogCommentMapper;
import com.bc.bcblog.mapper.BlogTagMapper;
import com.bc.bcblog.service.DashboardService;
import com.bc.bcblog.vo.DashboardStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 仪表盘统计服务。
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final BlogArticleMapper articleMapper;
    private final BlogCategoryMapper categoryMapper;
    private final BlogTagMapper tagMapper;
    private final BlogCommentMapper commentMapper;

    @Override
    public DashboardStatsVO stats() {
        DashboardStatsVO vo = new DashboardStatsVO();
        vo.setArticleCount(articleMapper.selectCount(null));
        vo.setCategoryCount(categoryMapper.selectCount(null));
        vo.setTagCount(tagMapper.selectCount(null));
        vo.setCommentCount(commentMapper.selectCount(null));

        // 总浏览量：对文章 view_count 求和
        List<Object> objs = articleMapper.selectObjs(new QueryWrapper<BlogArticle>()
                .select("COALESCE(SUM(view_count), 0)"));
        long view = 0L;
        if (objs != null && !objs.isEmpty() && objs.get(0) != null) {
            view = ((Number) objs.get(0)).longValue();
        }
        vo.setViewCount(view);
        return vo;
    }
}
