package com.bc.bcblog.service;

import com.bc.bcblog.entity.BlogPhoto;

import java.util.List;

/** 流光忆庭照片服务。 */
public interface PhotoService {
    List<BlogPhoto> list();
    void save(BlogPhoto photo);
    void delete(Long id);
}
