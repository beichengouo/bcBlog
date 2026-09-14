package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bc.bcblog.entity.SysVisitStat;
import com.bc.bcblog.mapper.SysVisitStatMapper;
import com.bc.bcblog.service.VisitService;
import com.bc.bcblog.vo.VisitStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 访问量统计实现。 */
@Service
@RequiredArgsConstructor
public class VisitServiceImpl implements VisitService {

    private final SysVisitStatMapper visitStatMapper;

    @Override
    public void report() {
        visitStatMapper.incrementToday();
    }

    @Override
    public VisitStatsVO stats() {
        VisitStatsVO vo = new VisitStatsVO();

        List<Object> objs = visitStatMapper.selectObjs(new QueryWrapper<SysVisitStat>()
                .select("COALESCE(SUM(pv), 0)"));
        long total = 0L;
        if (objs != null && !objs.isEmpty() && objs.get(0) != null) {
            total = ((Number) objs.get(0)).longValue();
        }
        vo.setTotalPv(total);

        LocalDate today = LocalDate.now();
        SysVisitStat todayStat = visitStatMapper.selectOne(new LambdaQueryWrapper<SysVisitStat>()
                .eq(SysVisitStat::getStatDate, today));
        vo.setTodayPv(todayStat == null || todayStat.getPv() == null ? 0L : todayStat.getPv());

        // 最近 7 天，缺失日期补 0
        LocalDate start = today.minusDays(6);
        List<SysVisitStat> list = visitStatMapper.selectList(new LambdaQueryWrapper<SysVisitStat>()
                .ge(SysVisitStat::getStatDate, start)
                .orderByAsc(SysVisitStat::getStatDate));
        Map<String, Long> map = new HashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (SysVisitStat s : list) {
            map.put(s.getStatDate().format(fmt), s.getPv() == null ? 0L : s.getPv());
        }
        List<VisitStatsVO.DailyPv> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = start.plusDays(i).format(fmt);
            days.add(new VisitStatsVO.DailyPv(date, map.getOrDefault(date, 0L)));
        }
        vo.setLast7Days(days);
        return vo;
    }
}
