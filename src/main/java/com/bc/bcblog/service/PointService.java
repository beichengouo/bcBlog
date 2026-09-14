package com.bc.bcblog.service;

import com.bc.bcblog.common.PageResult;
import com.bc.bcblog.entity.SysPointLog;

import java.util.List;

/** 积分服务。 */
public interface PointService {

    /** 签到随机积分 1~3 */
    int randomSignPoints();

    /** 增加/扣减积分，返回变化后余额 */
    int addPoints(Long userId, int points, String type, String reason);

    /** 扣减积分，积分不足时抛出异常 */
    int deductPoints(Long userId, int points, String type, String reason);

    /** 给指定用户或全部普通用户发放积分，返回发放人数 */
    int grant(List<Long> userIds, int points, String type, String reason);

    /** 积分流水 */
    PageResult<SysPointLog> page(Long userId, long page, long size);

    /** 后台积分流水查询，userId 为空时查全部 */
    PageResult<SysPointLog> pageAll(Long userId, long page, long size);
}
