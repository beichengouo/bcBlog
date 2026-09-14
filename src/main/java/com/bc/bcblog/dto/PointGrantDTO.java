package com.bc.bcblog.dto;

import lombok.Data;

import java.util.List;

/** 管理员发放积分入参。 */
@Data
public class PointGrantDTO {
    /** 为空表示全部普通用户 */
    private List<Long> userIds;
    private Integer points;
    /** 积分来源，默认管理员发放 */
    private String type;
    private String reason;
}
