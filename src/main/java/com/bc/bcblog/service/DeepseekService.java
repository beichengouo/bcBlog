package com.bc.bcblog.service;

import com.bc.bcblog.vo.DeepseekBalanceVO;

public interface DeepseekService {
    String getApiKey();
    void saveApiKey(String apiKey);
    DeepseekBalanceVO queryBalance();
}
