package com.school.edu.vo;

/**
 * 邮件配置回显对象（不含授权码明文）。
 *
 * @param passwordSet 是否已配置授权码（前端据此决定密码框占位提示）
 */
public record MailConfigVO(
        Boolean enabled,
        String host,
        Integer port,
        String username,
        Boolean passwordSet,
        Boolean useAuth,
        Boolean useSsl,
        Boolean useStarttls,
        String fromAddr,
        String fromName,
        String subjectPrefix,
        String portalUrl,
        String sender) {
}
