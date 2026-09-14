package com.bc.bcblog.service;

import com.bc.bcblog.entity.BlogResource;

import java.util.List;

/** 智库资源服务。 */
public interface ResourceService {
    List<BlogResource> list();
    void save(BlogResource resource);
    void delete(Long id);
}
