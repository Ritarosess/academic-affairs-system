package com.school.edu.service;

import com.school.edu.config.MailProperties;
import com.school.edu.dto.MailConfigDto;
import com.school.edu.entity.MailConfig;
import com.school.edu.mapper.MailConfigMapper;
import com.school.edu.vo.MailConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * 邮件 SMTP 配置的唯一数据来源（数据库驱动，运行时可由管理员修改）。
 *
 * <p>应用首次启动若配置表为空，会用 application.yml 的 {@code spring.mail.*} 与
 * {@code app.mail.*} 写入一行种子配置（固定 id=1），之后一律以数据库为准，
 * 修改配置无需重启。每次发送邮件时按当前配置即时构建 {@link JavaMailSender}，
 * 保证授权码 / 服务器变更立刻生效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailConfigService {

    /** 单行配置的固定主键 */
    private static final Long ROW_ID = 1L;

    private final MailConfigMapper mailConfigMapper;
    private final MailProperties seedProperties;

    @Value("${spring.mail.host:}")
    private String seedHost;
    @Value("${spring.mail.port:465}")
    private Integer seedPort;
    @Value("${spring.mail.username:}")
    private String seedUsername;
    @Value("${spring.mail.password:}")
    private String seedPassword;

    private volatile MailConfig cached;

    /* ============================================================ 读取 */

    /** 取当前配置（带缓存）。 */
    public MailConfig config() {
        MailConfig c = cached;
        if (c == null) {
            c = mailConfigMapper.selectById(ROW_ID);
            if (c == null) {
                c = seedFromYml();
                mailConfigMapper.insert(c);
                log.info("mail_config 为空，已用 application.yml 初始化邮件配置");
            }
            cached = c;
        }
        return c;
    }

    /** 脱敏回显：不返回授权码，仅告知是否已设置。 */
    public MailConfigVO view() {
        MailConfig c = config();
        return new MailConfigVO(
                Boolean.TRUE.equals(c.getEnabled()), c.getHost(), c.getPort(), c.getUsername(),
                StringUtils.hasText(c.getPassword()),
                Boolean.TRUE.equals(c.getUseAuth()), Boolean.TRUE.equals(c.getUseSsl()),
                Boolean.TRUE.equals(c.getUseStarttls()),
                c.getFromAddr(), c.getFromName(), c.getSubjectPrefix(), c.getPortalUrl(), c.getSender());
    }

    /* ============================================================ 保存 */

    /** 保存配置；password 为空时保留原授权码不变。 */
    public MailConfigVO save(MailConfigDto.SaveRequest req) {
        MailConfig c = config();
        c.setEnabled(Boolean.TRUE.equals(req.enabled()));
        c.setHost(trim(req.host()));
        c.setPort(req.port() == null ? 465 : req.port());
        c.setUsername(trim(req.username()));
        if (StringUtils.hasText(req.password())) {
            c.setPassword(req.password().trim());
        }
        c.setUseAuth(req.useAuth() == null || req.useAuth());
        c.setUseSsl(Boolean.TRUE.equals(req.useSsl()));
        c.setUseStarttls(Boolean.TRUE.equals(req.useStarttls()));
        c.setFromAddr(StringUtils.hasText(req.fromAddr()) ? req.fromAddr().trim() : null);
        c.setFromName(defaultIfBlank(req.fromName(), seedProperties.getFromName()));
        c.setSubjectPrefix(defaultIfBlank(req.subjectPrefix(), seedProperties.getSubjectPrefix()));
        c.setPortalUrl(defaultIfBlank(req.portalUrl(), seedProperties.getPortalUrl()));
        c.setSender(defaultIfBlank(req.sender(), seedProperties.getSender()));
        mailConfigMapper.updateById(c);
        cached = c;
        return view();
    }

    /* ============================================================ 发送器 */

    /**
     * 按当前配置即时构建邮件发送器。发送频率低，每次构建可保证配置始终最新。
     *
     * @throws IllegalStateException 未配置服务器 / 账号时抛出
     */
    public JavaMailSender mailSender() {
        MailConfig c = config();
        if (!StringUtils.hasText(c.getHost()) || !StringUtils.hasText(c.getUsername())) {
            throw new IllegalStateException("邮件服务尚未配置 SMTP 服务器与登录账号");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(c.getHost().trim());
        sender.setPort(c.getPort() == null ? 465 : c.getPort());
        sender.setUsername(c.getUsername().trim());
        sender.setPassword(c.getPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties p = sender.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", Boolean.TRUE.equals(c.getUseAuth()) ? "true" : "false");
        p.put("mail.smtp.ssl.enable", Boolean.TRUE.equals(c.getUseSsl()) ? "true" : "false");
        p.put("mail.smtp.starttls.enable", Boolean.TRUE.equals(c.getUseStarttls()) ? "true" : "false");
        p.put("mail.smtp.connectiontimeout", "8000");
        p.put("mail.smtp.timeout", "8000");
        p.put("mail.smtp.writetimeout", "8000");
        return sender;
    }

    /* ============================================================ 便捷访问（供邮件文案使用） */

    public boolean isRealSendEnabled() {
        return Boolean.TRUE.equals(config().getEnabled());
    }

    /** 实际生效的发件人：fromAddr 留空时回退到登录账号。 */
    public String effectiveFrom() {
        MailConfig c = config();
        if (StringUtils.hasText(c.getFromAddr())) {
            return c.getFromAddr().trim();
        }
        if (StringUtils.hasText(c.getUsername())) {
            return c.getUsername().trim();
        }
        return "noreply@school.edu.cn";
    }

    public String host() {
        return config().getHost();
    }

    public Integer port() {
        return config().getPort();
    }

    public String username() {
        return config().getUsername();
    }

    public String fromName() {
        return config().getFromName();
    }

    public String subjectPrefix() {
        return config().getSubjectPrefix();
    }

    public String portalUrl() {
        return config().getPortalUrl();
    }

    public String sender() {
        return config().getSender();
    }

    /* ============================================================ 私有 */

    /** 首次启动用 application.yml 生成种子行。 */
    private MailConfig seedFromYml() {
        MailConfig c = new MailConfig();
        c.setId(ROW_ID);
        c.setEnabled(Boolean.TRUE.equals(seedProperties.getEnabled()));
        c.setHost(StringUtils.hasText(seedHost) ? seedHost.trim() : null);
        c.setPort(seedPort == null ? 465 : seedPort);
        c.setUsername(StringUtils.hasText(seedUsername) ? seedUsername.trim() : null);
        c.setPassword(StringUtils.hasText(seedPassword) ? seedPassword.trim() : null);
        c.setUseAuth(true);
        c.setUseSsl(true);
        c.setUseStarttls(false);
        c.setFromAddr(StringUtils.hasText(seedProperties.getFrom()) ? seedProperties.getFrom().trim() : null);
        c.setFromName(seedProperties.getFromName());
        c.setSubjectPrefix(seedProperties.getSubjectPrefix());
        c.setPortalUrl(seedProperties.getPortalUrl());
        c.setSender(seedProperties.getSender());
        return c;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String defaultIfBlank(String s, String fallback) {
        return StringUtils.hasText(s) ? s.trim() : fallback;
    }
}
