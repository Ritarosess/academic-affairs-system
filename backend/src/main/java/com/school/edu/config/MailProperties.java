package com.school.edu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 学业预警邮件通知配置（对应 application.yml 的 app.mail）。
 *
 * <p><b>关于 enabled 开关</b>：邮件发送依赖真实的 SMTP 服务器与授权码。为了在
 * 没有 SMTP 凭据的环境下也能完整演示与测试该功能，enabled = false 时系统进入
 * 「模拟发送」模式：照常生成邮件内容、写入通知记录，但不连接 SMTP 服务器，
 * 通知记录的状态记为 SIMULATED。配置好 SMTP 并把 enabled 改为 true 后即可真实投递，
 * 业务代码无需任何改动。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    /** 是否真实发送；false 表示模拟发送（仅生成内容与记录，不连网络） */
    private Boolean enabled = false;

    /**
     * 发件人地址。**留空表示自动使用 spring.mail.username**。
     * 这一点很重要：QQ、163 等邮箱服务商会拒绝「发件人与登录账号不一致」的邮件
     * （报 550 或 "From header is missing or invalid"），因此接入真实邮箱时通常必须留空。
     */
    private String from = "";

    /** 发件人显示名 */
    private String fromName = "高校教务系统";

    /** 邮件主题前缀 */
    private String subjectPrefix = "【学业预警】";

    /** 学生端查询入口，写入邮件正文便于学生自助查看 */
    private String portalUrl = "http://localhost:8080";

    /** 落款单位 */
    private String sender = "教务处";
}
