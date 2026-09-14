package com.bc.bcblog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.dto.UserLoginDTO;
import com.bc.bcblog.dto.UserRegisterDTO;
import com.bc.bcblog.entity.BlogArticle;
import com.bc.bcblog.entity.BlogComment;
import com.bc.bcblog.entity.SysInviteCode;
import com.bc.bcblog.entity.SysLevel;
import com.bc.bcblog.entity.SysPointLog;
import com.bc.bcblog.entity.SysSignLog;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.BlogArticleMapper;
import com.bc.bcblog.mapper.BlogCommentMapper;
import com.bc.bcblog.mapper.SysSignLogMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.EmailCodeService;
import com.bc.bcblog.service.InviteCodeService;
import com.bc.bcblog.service.LevelService;
import com.bc.bcblog.service.PointService;
import com.bc.bcblog.service.UserService;
import com.bc.bcblog.vo.CommentVO;
import com.bc.bcblog.vo.EmailCodeResultVO;
import com.bc.bcblog.vo.LoginResultVO;
import com.bc.bcblog.vo.SignResultVO;
import com.bc.bcblog.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 前台用户服务实现。 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final BlogCommentMapper commentMapper;
    private final BlogArticleMapper articleMapper;
    private final SysSignLogMapper signLogMapper;
    private final ConfigService configService;
    private final EmailCodeService emailCodeService;
    private final InviteCodeService inviteCodeService;
    private final LevelService levelService;
    private final PointService pointService;

    @Override
    public EmailCodeResultVO sendEmailCode(String email) {
        return emailCodeService.send(email);
    }

    @Override
    public LoginResultVO register(UserRegisterDTO dto) {
        String email = dto.getEmail() == null ? "" : dto.getEmail().trim();
        String password = dto.getPassword() == null ? "" : dto.getPassword();
        String confirmPassword = dto.getConfirmPassword() == null ? "" : dto.getConfirmPassword();
        if (email.isEmpty() || !email.contains("@")) {
            throw new BusinessException("邮箱格式不正确");
        }
        // 密码必须同时包含字母和数字，且至少 8 位
        if (!password.matches("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")) {
            throw new BusinessException("密码至少 8 位，且必须同时包含字母和数字");
        }
        if (!password.equals(confirmPassword)) {
            throw new BusinessException("两次输入的密码不一致");
        }
        if ("1".equals(configService.getConfigValue("register_email_verify", "1"))) {
            emailCodeService.validate(email, dto.getEmailCode());
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, email)) > 0) {
            throw new BusinessException("邮箱已被注册");
        }
        if ("1".equals(configService.getConfigValue("register_invite_required", "0"))) {
            inviteCodeService.validateAndUse(dto.getInviteCode());
        }

        SysUser user = new SysUser();
        // 普通用户直接用邮箱作为账号
        user.setUsername(email);
        user.setPassword(BCrypt.hashpw(password));
        user.setEmail(email);
        String nickname = dto.getNickname() == null ? "" : dto.getNickname().trim();
        user.setNickname(nickname.isEmpty() ? email.substring(0, email.indexOf('@')) : nickname);
        user.setRole("USER");
        user.setStatus(1);
        user.setExp(0);
        user.setPoints(0);
        user.setLevel(1);
        user.setCanInvite(0);
        user.setSignDays(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("uk_username")) {
                throw new BusinessException("用户名已存在");
            }
            if (msg.contains("uk_email")) {
                throw new BusinessException("邮箱已被注册");
            }
            throw new BusinessException("注册失败，请稍后重试");
        }

        StpUtil.login(user.getId());
        LoginResultVO result = new LoginResultVO();
        result.setToken(StpUtil.getTokenValue());
        result.setUser(toInfo(user));
        return result;
    }

    @Override
    public LoginResultVO login(UserLoginDTO dto) {
        String account = dto.getAccount() == null ? "" : dto.getAccount().trim();
        if (account.isEmpty() || dto.getPassword() == null) {
            throw new BusinessException("请输入账号和密码");
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .and(w -> w.eq(SysUser::getUsername, account).or().eq(SysUser::getEmail, account))
                .last("limit 1"));
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        StpUtil.login(user.getId());
        LoginResultVO result = new LoginResultVO();
        result.setToken(StpUtil.getTokenValue());
        result.setUser(toInfo(user));
        return result;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserInfoVO info() {
        return toInfo(currentUser());
    }

    @Override
    public SignResultVO signIn() {
        SysUser user = currentUser();
        LocalDate today = LocalDate.now();
        if (today.equals(user.getLastSignDate())) {
            throw new BusinessException("今天已经签到过了");
        }
        int exp = parseInt(configService.getConfigValue("sign_exp", "5"), 5);
        SysSignLog log = new SysSignLog();
        log.setUserId(user.getId());
        log.setSignDate(today);
        log.setExp(exp);
        log.setCreateTime(LocalDateTime.now());
        signLogMapper.insert(log);

        int signDays = (user.getSignDays() == null ? 0 : user.getSignDays()) + 1;
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setSignDays(signDays);
        update.setLastSignDate(today);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        user.setSignDays(signDays);
        user.setLastSignDate(today);

        SysLevel level = levelService.addExp(user, exp);
        SysLevel next = levelService.nextOf(user.getExp() == null ? 0 : user.getExp());
        int gainedPoints = pointService.randomSignPoints();
        int points = pointService.addPoints(user.getId(), gainedPoints, "sign", "每日签到");
        SignResultVO vo = new SignResultVO();
        vo.setGainedExp(exp);
        vo.setExp(user.getExp());
        vo.setGainedPoints(gainedPoints);
        vo.setPoints(points);
        vo.setLevel(level.getLevel());
        vo.setLevelName(level.getName());
        vo.setNextLevelExp(next == null ? null : next.getExpRequired());
        vo.setSignDays(signDays);
        return vo;
    }

    @Override
    public UserInfoVO updateAvatar(String url) {
        SysUser user = currentUser();
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setAvatar(url);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        user.setAvatar(url);
        return toInfo(user);
    }

    @Override
    public PageResult<CommentVO> myComments(long page, long size) {
        SysUser user = currentUser();
        Page<BlogComment> p = new Page<>(page, size);
        IPage<BlogComment> result = commentMapper.selectPage(p, new LambdaQueryWrapper<BlogComment>()
                .eq(BlogComment::getUserId, user.getId())
                .eq(BlogComment::getStatus, 1)
                .orderByDesc(BlogComment::getCreateTime));
        Map<Long, String> titleMap = new HashMap<>();
        List<Long> articleIds = result.getRecords().stream().map(BlogComment::getArticleId).distinct().collect(Collectors.toList());
        if (!articleIds.isEmpty()) {
            for (BlogArticle a : articleMapper.selectBatchIds(articleIds)) {
                titleMap.put(a.getId(), a.getTitle());
            }
        }
        List<CommentVO> list = result.getRecords().stream().map(c -> {
            CommentVO vo = new CommentVO();
            vo.setId(c.getId());
            vo.setArticleId(c.getArticleId());
            vo.setArticleTitle(titleMap.get(c.getArticleId()));
            vo.setUserId(c.getUserId());
            vo.setNickname(c.getNickname());
            vo.setAvatar(c.getAvatar());
            vo.setLevel(c.getLevel());
            vo.setLevelName(c.getLevelName());
            vo.setContent(c.getContent());
            vo.setCreateTime(c.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), list);
    }

    @Override
    public PageResult<SysPointLog> pointLogs(long page, long size) {
        return pointService.page(currentUser().getId(), page, size);
    }

    @Override
    public SysInviteCode myInviteCode() {
        return inviteCodeService.getOrCreate(currentUser());
    }

    @Override
    public void addCommentExp(SysUser user) {
        if (user == null) {
            return;
        }
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Long count = commentMapper.selectCount(new LambdaQueryWrapper<BlogComment>()
                .eq(BlogComment::getUserId, user.getId())
                .ge(BlogComment::getCreateTime, start));
        int limit = parseInt(configService.getConfigValue("comment_exp_limit", "3"), 3);
        if (count != null && count <= limit) {
            int exp = parseInt(configService.getConfigValue("comment_exp", "3"), 3);
            levelService.addExp(user, exp);
            // 每天前三次评论固定获得 1 积分
            pointService.addPoints(user.getId(), 1, "comment", "评论奖励");
        }
    }

    private SysUser currentUser() {
        Long id = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        return user;
    }

    private UserInfoVO toInfo(SysUser user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setEmail(user.getEmail());
        int exp = user.getExp() == null ? 0 : user.getExp();
        vo.setExp(exp);
        vo.setPoints(user.getPoints() == null ? 0 : user.getPoints());
        SysLevel level = levelService.levelOf(exp);
        vo.setLevel(level.getLevel());
        vo.setLevelName(level.getName());
        SysLevel next = levelService.nextOf(exp);
        vo.setNextLevelExp(next == null ? null : next.getExpRequired());
        vo.setCanInvite(user.getCanInvite() == null ? 0 : user.getCanInvite());
        vo.setSignDays(user.getSignDays() == null ? 0 : user.getSignDays());
        vo.setSignedToday(LocalDate.now().equals(user.getLastSignDate()));
        if (user.getMenus() != null && !user.getMenus().trim().isEmpty()) {
            vo.setMenus(Arrays.asList(user.getMenus().split(",")));
        } else {
            vo.setMenus(new ArrayList<>());
        }
        if (user.getCanInvite() != null && user.getCanInvite() == 1) {
            SysInviteCode invite = inviteCodeService.getOrCreate(user);
            vo.setInviteCode(invite == null ? null : invite.getCode());
        }
        return vo;
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
