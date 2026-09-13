package com.bc.bcblog.vo;

import lombok.Data;

import java.util.List;

/**
 * DeepSeek 余额查询返回对象。
 */
@Data
public class DeepseekBalanceVO {
    private boolean available;
    private List<BalanceInfo> balanceInfos;

    @Data
    public static class BalanceInfo {
        private String currency;
        private String totalBalance;
        private String grantedBalance;
        private String toppedUpBalance;
    }
}
