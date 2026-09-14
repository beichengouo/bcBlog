package com.bc.bcblog.service;

import com.bc.bcblog.vo.CleanupResultVO;

/** 数据定期清理服务。 */
public interface DataCleanupService {

    /** 按配置保留天数清理历史数据，返回各表清理条数。 */
    CleanupResultVO clean();
}
