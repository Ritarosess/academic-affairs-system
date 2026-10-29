package com.school.edu.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 邮件配置相关请求体。 */
public final class MailConfigDto {

    private MailConfigDto() {
    }

    /**
     * 保存邮件配置。
     *
     * @param password SMTP 授权码；**留空表示不修改原密码**
     */
    public record SaveRequest(
            Boolean enabled,
            @NotBlank(message = "SMTP 服务器地址不能为空")
            @Size(max = 120, message = "服务器地址过长") String host,
            @Min(value = 1, message = "端口非法") @Max(value = 65535, message = "端口非法") Integer port,
            @Size(max = 120, message = "登录账号过长") String username,
            @Size(max = 200, message = "授权码过长") String password,
            Boolean useAuth,
            Boolean useSsl,
            Boolean useStarttls,
            @Email(message = "发件人邮箱格式不正确") String fromAddr,
            @Size(max = 80, message = "发件人名称过长") String fromName,
            @Size(max = 80, message = "主题前缀过长") String subjectPrefix,
            @Size(max = 200, message = "查询入口过长") String portalUrl,
            @Size(max = 80, message = "落款单位过长") String sender) {
    }

    /** 发送测试邮件；to 留空时发给当前登录管理员的邮箱。 */
    public record TestRequest(
            @Email(message = "收件邮箱格式不正确") String to) {
    }
}
