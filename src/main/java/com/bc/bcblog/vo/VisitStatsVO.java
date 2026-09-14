package com.bc.bcblog.vo;

import lombok.Data;

import java.util.List;

/** 访问量统计。 */
@Data
public class VisitStatsVO {
    private Long totalPv;
    private Long todayPv;
    private List<DailyPv> last7Days;

    @Data
    public static class DailyPv {
        private String date;
        private Long pv;

        public DailyPv() {
        }

        public DailyPv(String date, Long pv) {
            this.date = date;
            this.pv = pv;
        }
    }
}
