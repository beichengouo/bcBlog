package com.bc.bcblog.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.dto.AdminUserDTO;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.AdminUserService;
import com.bc.bcblog.vo.AdminUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 管理员管理服务：仅超级管理员可操作。 */
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final SysUserMapper sysUserMapper;

    @Override
    public List<AdminUserVO> list() {
        requireSuper();
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public void register(AdminUserDTO dto) {
        requireSuper();
        if (dto.getUsername() == null || dto.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (dto.getPassword() == null || dto.getPassword().length() < 8) {
            throw new BusinessException("密码长度至少 8 位");
        }
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername().trim())) > 0) {
            throw new BusinessException("用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(dto.getUsername().trim());
        u.setPassword(BCrypt.hashpw(dto.getPassword()));
        u.setNickname(dto.getNickname() == null || dto.getNickname().trim().isEmpty()
                ? dto.getUsername().trim() : dto.getNickname().trim());
        u.setRole(normalizeRole(dto.getRole()));
        u.setMenus(joinMenus(dto.getMenus()));
        u.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        u.setCreateTime(LocalDateTime.now());
        u.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(u);
    }

    @Override
    public void update(AdminUserDTO dto) {
        requireSuper();
        if (dto.getId() == null) {
            throw new BusinessException("缺少用户 ID");
        }
        SysUser target = sysUserMapper.selectById(dto.getId());
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        if ("SUPER".equals(target.getRole())) {
            throw new BusinessException("不能修改超级管理员");
        }
        SysUser u = new SysUser();
        u.setId(dto.getId());
        if (dto.getNickname() != null) {
            u.setNickname(dto.getNickname().trim());
        }
        if (dto.getRole() != null) {
            u.setRole(normalizeRole(dto.getRole()));
        }
        if (dto.getMenus() != null) {
            u.setMenus(joinMenus(dto.getMenus()));
        }
        if (dto.getStatus() != null) {
            u.setStatus(dto.getStatus());
        }
        if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
            if (dto.getPassword().length() < 8) {
                throw new BusinessException("密码长度至少 8 位");
            }
            u.setPassword(BCrypt.hashpw(dto.getPassword()));
        }
        u.setUpdateTime(LocalDateTime.now());
        sysUserMapper.updateById(u);
    }

    @Override
    public void delete(Long id) {
        requireSuper();
        SysUser target = sysUserMapper.selectById(id);
        if (target == null) {
            return;
        }
        if ("SUPER".equals(target.getRole())) {
            throw new BusinessException("不能删除超级管理员");
        }
        if (id.equals(StpUtil.getLoginIdAsLong())) {
            throw new BusinessException("不能删除当前登录账号");
        }
        sysUserMapper.deleteById(id);
    }

    private void requireSuper() {
        try {
            Long uid = StpUtil.getLoginIdAsLong();
            SysUser u = sysUserMapper.selectById(uid);
            if (u == null || !"SUPER".equals(u.getRole())) {
                throw new BusinessException(403, "仅超级管理员可操作");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(403, "仅超级管理员可操作");
        }
    }

    private String normalizeRole(String role) {
        return "ADMIN2".equals(role) ? "ADMIN2" : "ADMIN1";
    }

    private String joinMenus(List<String> menus) {
        if (menus == null) {
            return "";
        }
        return menus.stream().filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(","));
    }

    private AdminUserVO toVO(SysUser u) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setRole(u.getRole());
        vo.setStatus(u.getStatus());
        if (u.getMenus() != null && !u.getMenus().trim().isEmpty()) {
            vo.setMenus(Arrays.asList(u.getMenus().split(",")));
        } else {
            vo.setMenus(new ArrayList<>());
        }
        vo.setCreateTime(u.getCreateTime());
        return vo;
    }
}
