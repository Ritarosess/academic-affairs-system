package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件 SMTP 配置（单行表，固定 id = 1）。
 * 由管理员在「邮件配置」页维护，应用运行时据此动态构建 {@code JavaMailSender}。
 */
@Data
@TableName("mail_config")
public class MailConfig {

    /** 固定主键，恒为 1 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 是否真实发送；false = 模拟发送（仅记录，不连 SMTP） */
    private Boolean enabled;

    private String host;
    private Integer port;
    private String username;
    /** SMTP 授权码（不是邮箱登录密码） */
    private String password;

    private Boolean useAuth;
    private Boolean useSsl;
    private Boolean useStarttls;

    /** 发件人地址，留空则使用 {@link #username} */
    private String fromAddr;
    private String fromName;
    private String subjectPrefix;
    private String portalUrl;
    private String sender;

    private LocalDateTime updateTime;
}
