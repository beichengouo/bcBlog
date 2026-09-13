package com.bc.bcblog.service;

import com.bc.bcblog.vo.DeepseekBalanceVO;
import com.bc.bcblog.vo.AiArticleVO;

public interface DeepseekService {
    String getApiKey();
    void saveApiKey(String apiKey);
    DeepseekBalanceVO queryBalance();
    AiArticleVO generateArticle(String requirement);
}
