package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.component.SensitiveWordFilter;
import com.bc.bcblog.dto.CommentDTO;
import com.bc.bcblog.dto.CommentStatusDTO;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.entity.BlogComment;
import com.bc.bcblog.mapper.BlogArticleMapper;
import com.bc.bcblog.mapper.BlogCommentMapper;
import com.bc.bcblog.service.CommentService;
import com.bc.bcblog.vo.AdminCommentVO;
import com.bc.bcblog.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评论管理实现：游客发表时自动过滤敏感词，前台只返回已通过评论，后台返回全量。
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final BlogCommentMapper commentMapper;
    private final BlogArticleMapper articleMapper;
    private final SensitiveWordFilter sensitiveWordFilter;

    @Override
    public PageResult<CommentVO> pageByArticle(Long articleId, long page, long size) {
        Page<BlogComment> p = new Page<>(page, size);
        IPage<BlogComment> result = commentMapper.selectPage(p, new LambdaQueryWrapper<BlogComment>()
                .eq(BlogComment::getArticleId, articleId)
                .eq(BlogComment::getStatus, 1)
                .orderByDesc(BlogComment::getCreateTime));
        List<CommentVO> vos = result.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), vos);
    }

    @Override
    public void save(CommentDTO dto) {
        if (dto.getArticleId() == null) {
            throw new BusinessException("缺少文章ID");
        }
        BlogArticle article = articleMapper.selectById(dto.getArticleId());
        if (article == null || article.getStatus() == null || article.getStatus() != 1) {
            throw new BusinessException("文章不存在");
        }

        String nickname = dto.getNickname() == null ? "" : dto.getNickname().trim();
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (nickname.isEmpty()) {
            throw new BusinessException("昵称不能为空");
        }
        if (content.isEmpty()) {
            throw new BusinessException("评论内容不能为空");
        }
        if (nickname.length() > 50) {
            throw new BusinessException("昵称过长");
        }
        if (content.length() > 1000) {
            throw new BusinessException("评论内容过长");
        }

        String email = dto.getEmail() == null ? null : dto.getEmail().trim();
        if (email != null && !email.isEmpty() && !email.contains("@")) {
            throw new BusinessException("邮箱格式不正确");
        }

        BlogComment c = new BlogComment();
        c.setArticleId(dto.getArticleId());
        c.setParentId(0L);
        c.setNickname(sensitiveWordFilter.filter(nickname));
        c.setEmail(email);
        c.setContent(sensitiveWordFilter.filter(content));
        c.setStatus(1);
        c.setCreateTime(LocalDateTime.now());
        commentMapper.insert(c);
    }

    @Override
    public PageResult<AdminCommentVO> pageAdmin(long page, long size, Integer status) {
        Page<BlogComment> p = new Page<>(page, size);
        LambdaQueryWrapper<BlogComment> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(BlogComment::getStatus, status);
        }
        wrapper.orderByDesc(BlogComment::getCreateTime);
        IPage<BlogComment> result = commentMapper.selectPage(p, wrapper);

        Set<Long> articleIds = result.getRecords().stream()
                .map(BlogComment::getArticleId).collect(Collectors.toSet());
        Map<Long, String> titleMap = new HashMap<>();
        if (!articleIds.isEmpty()) {
            for (BlogArticle a : articleMapper.selectBatchIds(articleIds)) {
                titleMap.put(a.getId(), a.getTitle());
            }
        }
        List<AdminCommentVO> vos = result.getRecords().stream()
                .map(c -> toAdminVo(c, titleMap.get(c.getArticleId())))
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), vos);
    }

    @Override
    public void delete(Long id) {
        commentMapper.deleteById(id);
    }

    @Override
    public void updateStatus(CommentStatusDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("缺少评论ID");
        }
        BlogComment c = new BlogComment();
        c.setId(dto.getId());
        c.setStatus(dto.getStatus());
        commentMapper.updateById(c);
    }

    private CommentVO toVo(BlogComment c) {
        CommentVO vo = new CommentVO();
        vo.setId(c.getId());
        vo.setArticleId(c.getArticleId());
        vo.setNickname(c.getNickname());
        vo.setContent(c.getContent());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }

    private AdminCommentVO toAdminVo(BlogComment c, String articleTitle) {
        AdminCommentVO vo = new AdminCommentVO();
        vo.setId(c.getId());
        vo.setArticleId(c.getArticleId());
        vo.setArticleTitle(articleTitle);
        vo.setNickname(c.getNickname());
        vo.setEmail(c.getEmail());
        vo.setContent(c.getContent());
        vo.setStatus(c.getStatus());
        vo.setCreateTime(c.getCreateTime());
        return vo;
    }
}
