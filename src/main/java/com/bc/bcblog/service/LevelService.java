package com.bc.bcblog.service;

import com.bc.bcblog.entity.SysLevel;
import com.bc.bcblog.entity.SysUser;

import java.util.List;

/** 等级服务。 */
public interface LevelService {
    List<SysLevel> list();
    SysLevel levelOf(int exp);
    SysLevel nextOf(int exp);
    SysLevel addExp(SysUser user, int exp);
    void save(SysLevel level);
    void delete(Long id);
}
