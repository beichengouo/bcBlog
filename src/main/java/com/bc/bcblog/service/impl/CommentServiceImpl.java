package com.bc.bcblog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
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
import com.bc.bcblog.entity.SysLevel;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.BlogArticleMapper;
import com.bc.bcblog.mapper.BlogCommentMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.CommentService;
import com.bc.bcblog.service.LevelService;
import com.bc.bcblog.service.UserService;
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
 * 原生评论实现：未登录可以查看，发表评论必须登录；
 * 发表成功后按规则给用户增加经验。
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final BlogCommentMapper commentMapper;
    private final BlogArticleMapper articleMapper;
    private final SysUserMapper sysUserMapper;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final LevelService levelService;
    private final UserService userService;

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

    /**
     * 首页「最近评论」：最新的已通过评论（跨文章），原生评论模式下前台读这个。
     * 顺带补上文章标题，前台要显示"评论于《xxx》"。
     */
    @Override
    public List<CommentVO> recentForPortal(int limit) {
        int size = Math.max(1, Math.min(20, limit));
        List<BlogComment> list = commentMapper.selectList(new LambdaQueryWrapper<BlogComment>()
                .eq(BlogComment::getStatus, 1)
                .orderByDesc(BlogComment::getCreateTime)
                .last("limit " + size));
        if (list.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        List<CommentVO> vos = list.stream().map(this::toVo).collect(Collectors.toList());
        Set<Long> articleIds = vos.stream().map(CommentVO::getArticleId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (!articleIds.isEmpty()) {
            Map<Long, String> titles = new HashMap<>();
            for (BlogArticle article : articleMapper.selectBatchIds(articleIds)) {
                titles.put(article.getId(), article.getTitle());
            }
            for (CommentVO vo : vos) {
                vo.setArticleTitle(titles.get(vo.getArticleId()));
            }
        }
        return vos;
    }

    @Override
    public void save(CommentDTO dto) {
        Long userId;
        try {
            userId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            throw new BusinessException(401, "请先登录后再发表评论");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "请先登录后再发表评论");
        }
        if (dto.getArticleId() == null) {
            throw new BusinessException("缺少文章ID");
        }
        BlogArticle article = articleMapper.selectById(dto.getArticleId());
        if (article == null || article.getStatus() == null || article.getStatus() != 1) {
            throw new BusinessException("文章不存在");
        }
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException("评论内容不能为空");
        }
        if (content.length() > 1000) {
            throw new BusinessException("评论内容过长");
        }

        String nickname = user.getNickname() == null || user.getNickname().trim().isEmpty()
                ? user.getUsername() : user.getNickname();
        SysLevel level = levelService.levelOf(user.getExp() == null ? 0 : user.getExp());

        BlogComment c = new BlogComment();
        c.setArticleId(dto.getArticleId());
        c.setParentId(0L);
        c.setUserId(userId);
        c.setNickname(sensitiveWordFilter.filter(nickname));
        c.setEmail(user.getEmail());
        c.setAvatar(user.getAvatar());
        c.setLevel(level.getLevel());
        c.setLevelName(level.getName());
        c.setContent(sensitiveWordFilter.filter(content));
        c.setStatus(1);
        c.setCreateTime(LocalDateTime.now());
        commentMapper.insert(c);

        // 当日前三次评论获得经验
        userService.addCommentExp(user);
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
        vo.setUserId(c.getUserId());
        vo.setNickname(c.getNickname());
        vo.setAvatar(c.getAvatar());
        vo.setLevel(c.getLevel());
        vo.setLevelName(c.getLevelName());
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
