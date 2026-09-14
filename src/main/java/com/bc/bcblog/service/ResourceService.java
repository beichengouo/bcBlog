package com.bc.bcblog.service;

import com.bc.bcblog.entity.BlogResource;
import com.bc.bcblog.vo.ResourceVO;

import java.util.List;

/** 智库资源服务。 */
public interface ResourceService {
    List<BlogResource> list();
    void save(BlogResource resource);
    void delete(Long id);

    /** 前台资源列表，未解锁时隐藏链接和密码 */
    List<ResourceVO> portalList(Long userId);

    /** 前台资源详情，未解锁时隐藏链接和密码 */
    ResourceVO detail(Long id, Long userId);

    /** 消耗积分解锁资源，已解锁则直接返回 */
    ResourceVO unlock(Long id, Long userId);
}
