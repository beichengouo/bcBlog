package com.bc.bcblog.vo;

import lombok.Data;

/** 贡献金币的结果：角色金币余额 + 本人物积分余额。 */
@Data
public class SandboxCoinResultVO {
    /** 角色金币余额 */
    private Integer coins;
    /** 本次获得的贡献金币（已换算） */
    private Integer gained;
    /** 本次消耗的积分（管理员为 0） */
    private Integer pointsCost;
    /** 本人物积分余额 */
    private Integer points;
}
