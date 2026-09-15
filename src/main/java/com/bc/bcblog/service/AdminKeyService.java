package com.bc.bcblog.service;

/** 管理员个人密钥服务（加密存储）。 */
public interface AdminKeyService {

    /** 读取指定管理员的某个密钥，返回明文；没有则返回空串 */
    String get(Long adminId, String keyName);

    /** 保存（自动加密；提交空值或掩码表示不修改） */
    void save(Long adminId, String keyName, String value);
}
