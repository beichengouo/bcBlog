package com.bc.bcblog.service;

import com.bc.bcblog.vo.VisitStatsVO;

/** 访问量统计服务。 */
public interface VisitService {

    /** 记录一次访问（今日 PV +1）。 */
    void report();

    /** 查询访问量统计。 */
    VisitStatsVO stats();
}
