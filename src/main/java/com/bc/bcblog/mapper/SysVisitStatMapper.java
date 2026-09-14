package com.bc.bcblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.bcblog.entity.SysVisitStat;
import org.apache.ibatis.annotations.Insert;

public interface SysVisitStatMapper extends BaseMapper<SysVisitStat> {

    /** 今日访问量 +1，没有记录时自动插入。 */
    @Insert("INSERT INTO sys_visit_stat (stat_date, pv) VALUES (CURDATE(), 1) "
            + "ON DUPLICATE KEY UPDATE pv = pv + 1")
    int incrementToday();
}
