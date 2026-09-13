package com.bc.bcblog.service;

import com.bc.bcblog.dto.AdminUserDTO;
import com.bc.bcblog.vo.AdminUserVO;

import java.util.List;

public interface AdminUserService {
    List<AdminUserVO> list();
    void register(AdminUserDTO dto);
    void update(AdminUserDTO dto);
    void delete(Long id);
}
