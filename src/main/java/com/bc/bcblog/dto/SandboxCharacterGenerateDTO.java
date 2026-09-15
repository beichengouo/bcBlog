package com.bc.bcblog.dto;

import lombok.Data;

/** AI 一键创作沙盒角色的请求参数。 */
@Data
public class SandboxCharacterGenerateDTO {
    /** AI 服务商 ID，为空时用后台默认服务商 */
    private Long providerId;
    /** 模型名 */
    private String model;
    /** 管理员填写的角色需求 */
    private String requirement;
}
