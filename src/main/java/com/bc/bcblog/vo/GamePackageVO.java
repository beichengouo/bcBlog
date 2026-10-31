package com.bc.bcblog.vo;

import lombok.Data;

@Data
public class GamePackageVO {
    private String platform;
    private String version;
    private String notes;
    private String fileName;
    private long size;
    private String sha256;
    private String uploadedAt;
    private boolean published;
    private String downloadUrl;
}
