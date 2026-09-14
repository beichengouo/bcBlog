package com.bc.bcblog.service;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.dto.UserLoginDTO;
import com.bc.bcblog.dto.UserRegisterDTO;
import com.bc.bcblog.entity.SysInviteCode;
import com.bc.bcblog.entity.SysPointLog;
import com.bc.bcblog.entity.SysUser;
import com.bc.bcblog.vo.CommentVO;
import com.bc.bcblog.vo.EmailCodeResultVO;
import com.bc.bcblog.vo.LoginResultVO;
import com.bc.bcblog.vo.SignResultVO;
import com.bc.bcblog.vo.UserInfoVO;

/** 前台用户服务。 */
public interface UserService {
    EmailCodeResultVO sendEmailCode(String email);
    LoginResultVO register(UserRegisterDTO dto);
    LoginResultVO login(UserLoginDTO dto);
    void logout();
    UserInfoVO info();
    SignResultVO signIn();
    UserInfoVO updateAvatar(String url);
    PageResult<CommentVO> myComments(long page, long size);
    PageResult<SysPointLog> pointLogs(long page, long size);
    SysInviteCode myInviteCode();

    /** 评论后按规则增加经验。 */
    void addCommentExp(SysUser user);
}
