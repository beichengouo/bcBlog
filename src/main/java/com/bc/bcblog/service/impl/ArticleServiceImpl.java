package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.dto.ArticleDTO;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.entity.BlogArticleTag;
import com.bc.bcblog.mapper.BlogArticleMapper;
import com.bc.bcblog.mapper.BlogArticleTagMapper;
import com.bc.bcblog.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {

    private final BlogArticleMapper articleMapper;
    private final BlogArticleTagMapper articleTagMapper;

    @Override
    public PageResult<BlogArticle> pagePublished(long page, long size) {
        Page<BlogArticle> p = new Page<>(page, size);
        IPage<BlogArticle> result = articleMapper.selectPage(p,
                new LambdaQueryWrapper<BlogArticle>()
                        .eq(BlogArticle::getStatus, 1)
                        .orderByDesc(BlogArticle::getIsTop)
                        .orderByDesc(BlogArticle::getCreateTime));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public BlogArticle detail(Long id) {
        BlogArticle article = articleMapper.selectById(id);
        if (article == null || article.getStatus() == null || article.getStatus() != 1) {
            throw new BusinessException(404, "文章不存在");
        }
        return article;
    }

    @Override
    public PageResult<BlogArticle> pageAdmin(long page, long size, String keyword) {
        Page<BlogArticle> p = new Page<>(page, size);
        LambdaQueryWrapper<BlogArticle> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(BlogArticle::getTitle, keyword.trim());
        }
        wrapper.orderByDesc(BlogArticle::getCreateTime);
        IPage<BlogArticle> result = articleMapper.selectPage(p, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public void save(ArticleDTO dto) {
        BlogArticle article = new BlogArticle();
        applyDto(article, dto);
        article.setViewCount(0);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        articleMapper.insert(article);
        saveTags(article.getId(), dto.getTagIds());
    }

    @Override
    public void update(ArticleDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("缺少文章ID");
        }
        if (articleMapper.selectById(dto.getId()) == null) {
            throw new BusinessException("文章不存在");
        }
        BlogArticle article = new BlogArticle();
        article.setId(dto.getId());
        applyDto(article, dto);
        article.setUpdateTime(LocalDateTime.now());
        articleMapper.updateById(article);
        saveTags(dto.getId(), dto.getTagIds());
    }

    @Override
    public void delete(Long id) {
        articleMapper.deleteById(id);
        articleTagMapper.delete(new LambdaQueryWrapper<BlogArticleTag>()
                .eq(BlogArticleTag::getArticleId, id));
    }

    @Override
    public ArticleDTO getForEdit(Long id) {
        BlogArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        ArticleDTO dto = new ArticleDTO();
        dto.setId(article.getId());
        dto.setTitle(article.getTitle());
        dto.setSummary(article.getSummary());
        dto.setContent(article.getContent());
        dto.setCover(article.getCover());
        dto.setCategoryId(article.getCategoryId());
        dto.setStatus(article.getStatus());
        dto.setIsTop(article.getIsTop());
        List<Long> tagIds = articleTagMapper.selectList(new LambdaQueryWrapper<BlogArticleTag>()
                        .eq(BlogArticleTag::getArticleId, id))
                .stream().map(BlogArticleTag::getTagId).collect(Collectors.toList());
        dto.setTagIds(tagIds);
        return dto;
    }

    /** 校验并填充文章基础字段 */
    private void applyDto(BlogArticle article, ArticleDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BusinessException("标题不能为空");
        }
        article.setTitle(dto.getTitle().trim());
        article.setSummary(dto.getSummary());
        article.setContent(dto.getContent());
        article.setCover(dto.getCover());
        article.setCategoryId(dto.getCategoryId());
        article.setStatus(dto.getStatus() == null ? 0 : dto.getStatus());
        article.setIsTop(dto.getIsTop() == null ? 0 : dto.getIsTop());
    }

    /** 先删后插，维护文章与标签的关联 */
    private void saveTags(Long articleId, List<Long> tagIds) {
        articleTagMapper.delete(new LambdaQueryWrapper<BlogArticleTag>()
                .eq(BlogArticleTag::getArticleId, articleId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        tagIds.stream().filter(id -> id != null).distinct().forEach(tagId -> {
            BlogArticleTag rel = new BlogArticleTag();
            rel.setArticleId(articleId);
            rel.setTagId(tagId);
            articleTagMapper.insert(rel);
        });
    }
}
