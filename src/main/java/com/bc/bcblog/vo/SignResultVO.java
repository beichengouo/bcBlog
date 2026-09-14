package com.bc.bcblog.vo;

import lombok.Data;

/** 签到结果。 */
@Data
public class SignResultVO {
    private Integer gainedExp;
    private Integer exp;
    private Integer gainedPoints;
    private Integer points;
    private Integer level;
    private String levelName;
    private Integer nextLevelExp;
    private Integer signDays;
}
