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
    /** 是否还在进行中（1 进行中 / 0 已结束）：全员行动改成异步后，前端靠它轮询进度 */
    private int running;
    /** 已经处理完几个角色（含跳过与失败） */
    private int done;
}
