package com.bc.bcblog.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 管理员一键让全部角色行动一轮的结果。 */
@Data
public class SandboxRunAllVO {
    /** 本轮参与的角色数 */
    private int total;
    /** 成功数 */
    private int success;
    /** 失败数 */
    private int failed;
    /** 每个角色的一行结果，方便后台直接看 */
    private List<String> items = new ArrayList<>();
}
