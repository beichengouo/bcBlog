package com.bc.bcblog.service;

import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.vo.AiArticleVO;

import java.util.List;

public interface AiProviderService {
    List<AiProvider> list();
    void save(AiProvider provider);
    void delete(Long id);
    void setDefault(Long id);
    List<String> listModels(Long id);
    AiArticleVO generate(Long providerId, String model, String requirement);
}
