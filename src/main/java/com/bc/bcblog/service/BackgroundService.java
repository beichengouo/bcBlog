package com.bc.bcblog.service;

import com.bc.bcblog.entity.Background;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BackgroundService {
    List<Background> list();
    Background upload(MultipartFile file);
    void delete(Long id);
    void setActive(Long id, String scope);
    Background activeByScope(String scope);
    void clearActive(String scope);
}
