package com.bc.bcblog.vo;

import lombok.Data;

/** 邮件发送配置（QQ 邮箱 SMTP）。 */
@Data
public class EmailConfigVO {
    /** QQ 邮箱账号 */
    private String username;
    /** QQ 邮箱授权码 */
    private String authCode;
    /** 发件人昵称 */
    private String senderName;
    private String host;
    private Integer port;
    private Integer ssl;
}
