package com.bc.bcblog.dto;

import lombok.Data;

/** 使用指定服务商和模型生成文章的入参。 */
@Data
public class AiGenerateRequestDTO {
    private Long providerId;
    private String model;
    private String requirement;
}
