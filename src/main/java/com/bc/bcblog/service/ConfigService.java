package com.bc.bcblog.service;

import com.bc.bcblog.vo.SiteConfigVO;

public interface ConfigService {
    SiteConfigVO get();
    void save(SiteConfigVO vo);
}
