package com.school.edu.service;

import com.school.edu.vo.WarningRuleVO;
import com.school.edu.vo.WarningVO;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学业预警通知邮件的文案生成与发送。
 *
 * <p>邮件为「纯文本 + HTML」的 alternative 多部分邮件：不支持 HTML 的客户端显示纯文本，
 * 其余客户端显示排版后的 HTML。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarningMailService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    private final MailConfigService mailConfigService;

    /**
     * 实际使用的发件人地址。
     * 优先取管理员配置的发件人；留空时回退到 SMTP 登录账号 —— 多数邮箱服务商
     * 要求发件人与登录账号一致，用别的地址会被直接拒收。
     */
    public String effectiveFrom() {
        return mailConfigService.effectiveFrom();
    }

    /** 当前 SMTP 配置摘要（不含密码），用于自检与排错。 */
    public Map<String, Object> smtpConfig() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("mailEnabled", isRealSendEnabled());
        info.put("host", mailConfigService.host());
        info.put("port", mailConfigService.port());
        info.put("username", mailConfigService.username());
        info.put("from", effectiveFrom());
        info.put("auth", StringUtils.hasText(mailConfigService.username()));
        return info;
    }

    /** 邮件正文（纯文本 + HTML）。 */
    public record MailContent(String subject, String plainText, String html) {
    }

    /** 等级对应的强调色，与前端配色保持一致。 */
    private static String levelColor(String level) {
        return switch (level == null ? "" : level) {
            case "RED" -> "#f56c6c";
            case "ORANGE" -> "#e6862c";
            case "YELLOW" -> "#d9a404";
            default -> "#67c23a";
        };
    }

    private static String nz(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private static String fmt(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    /* ============================================================ 文案生成 */

    public String buildSubject(WarningVO vo) {
        return mailConfigService.subjectPrefix() + "学业情况通知 —— "
                + vo.studentName() + "（" + vo.studentNo() + "）";
    }

    public MailContent build(WarningVO vo) {
        return new MailContent(buildSubject(vo), buildPlainText(vo), buildHtml(vo));
    }

    /** 纯文本版本。 */
    public String buildPlainText(WarningVO vo) {
        StringBuilder sb = new StringBuilder();
        sb.append(vo.studentName()).append(" 同学（学号 ").append(vo.studentNo()).append("）：\n\n");
        sb.append("    您好。经教务管理系统核算，你当前的学业情况已触发学业红线预警，")
                .append("现将具体情况通知如下，请予以重视。\n\n");

        sb.append("一、预警等级\n");
        sb.append("    ").append(vo.levelLabel()).append("\n\n");

        sb.append("二、学业指标\n");
        sb.append("    累计不及格学分：").append(fmt(vo.failedCredits())).append("\n");
        sb.append("    累计不及格门数：").append(nz(vo.failedCourseCount())).append("\n");
        sb.append("    单学期最多不及格门数：").append(nz(vo.maxSemesterFailedCount())).append("\n");
        sb.append("    已评课程门数：").append(nz(vo.scoredCourseCount())).append("\n");
        sb.append("    已修学分：").append(fmt(vo.scoredCredits())).append("\n");
        sb.append("    已获学分：").append(fmt(vo.passedCredits())).append("\n\n");

        sb.append("三、触发规则判定\n");
        List<WarningRuleVO> rules = vo.rules() == null ? List.of() : vo.rules();
        for (int i = 0; i < rules.size(); i++) {
            WarningRuleVO rule = rules.get(i);
            sb.append("    ").append(i + 1).append(". ").append(rule.label()).append("：")
                    .append(rule.value()).append("（阈值 ").append(rule.threshold()).append("）—— ")
                    .append(rule.triggered() ? "已触发" : "未触发").append("\n");
        }
        sb.append("\n");

        sb.append("四、处置建议\n");
        sb.append("    ").append(vo.advice()).append("\n\n");

        sb.append("五、说明\n");
        sb.append("    1. 本邮件由高校教务系统自动发送，请勿直接回复。\n");
        sb.append("    2. 如对成绩有疑问，请联系任课教师或所在院系教务员核实。\n");
        sb.append("    3. 查询入口：").append(mailConfigService.portalUrl())
                .append("（登录后进入「我的成绩」可查看完整预警明细）。\n\n");

        sb.append("                                         ").append(mailConfigService.sender()).append("\n");
        sb.append("                                         ").append(LocalDate.now().format(DATE));
        return sb.toString();
    }

    /** HTML 版本（使用内联样式以兼容邮件客户端）。 */
    public String buildHtml(WarningVO vo) {
        String color = levelColor(vo.level());
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:'Microsoft YaHei',Arial,sans-serif;font-size:14px;")
                .append("color:#303133;line-height:1.9;max-width:660px\">");

        sb.append("<p style=\"margin:0 0 12px\">").append(esc(vo.studentName()))
                .append(" 同学（学号 ").append(esc(vo.studentNo())).append("）：</p>");
        sb.append("<p style=\"margin:0 0 16px\">您好。经教务管理系统核算，你当前的学业情况已触发学业红线预警，")
                .append("现将具体情况通知如下，请予以重视。</p>");

        // 预警等级
        sb.append("<h3 style=\"margin:18px 0 8px;font-size:15px;color:#303133;border-left:4px solid ")
                .append(color).append(";padding-left:8px\">一、预警等级</h3>");
        sb.append("<p style=\"margin:0 0 12px\"><span style=\"display:inline-block;padding:3px 12px;")
                .append("border-radius:3px;background:").append(color).append(";color:#fff;font-weight:600\">")
                .append(esc(vo.levelLabel())).append("</span></p>");
        sb.append("<p style=\"margin:0 0 12px;color:#606266\">").append(esc(vo.summary())).append("</p>");

        // 学业指标
        sb.append("<h3 style=\"margin:18px 0 8px;font-size:15px;color:#303133;border-left:4px solid ")
                .append(color).append(";padding-left:8px\">二、学业指标</h3>");
        sb.append("<table style=\"border-collapse:collapse;width:100%;font-size:13px\">");
        sb.append(metricRow("累计不及格学分", fmt(vo.failedCredits()), true));
        sb.append(metricRow("累计不及格门数", nz(vo.failedCourseCount()), false));
        sb.append(metricRow("单学期最多不及格门数", nz(vo.maxSemesterFailedCount()), false));
        sb.append(metricRow("已评课程门数", nz(vo.scoredCourseCount()), true));
        sb.append(metricRow("已修学分", fmt(vo.scoredCredits()), false));
        sb.append(metricRow("已获学分", fmt(vo.passedCredits()), false));
        sb.append("</table>");

        // 触发规则
        sb.append("<h3 style=\"margin:18px 0 8px;font-size:15px;color:#303133;border-left:4px solid ")
                .append(color).append(";padding-left:8px\">三、触发规则判定</h3>");
        sb.append("<table style=\"border-collapse:collapse;width:100%;font-size:13px\">");
        sb.append("<tr style=\"background:#f5f7fa\">")
                .append(th("规则", 200)).append(th("当前值", 90)).append(th("阈值", 260)).append(th("判定", 90))
                .append("</tr>");
        for (WarningRuleVO rule : (vo.rules() == null ? List.<WarningRuleVO>of() : vo.rules())) {
            String rc = warningRuleColor(rule);
            sb.append("<tr>")
                    .append(td(esc(rule.label()), 200))
                    .append("<td style=\"border:1px solid #dcdfe6;padding:6px 10px;text-align:center;color:")
                    .append(rc).append(";font-weight:600\">").append(esc(rule.value())).append("</td>")
                    .append("<td style=\"border:1px solid #dcdfe6;padding:6px 10px;color:#909399\">")
                    .append(esc(rule.threshold())).append("</td>")
                    .append("<td style=\"border:1px solid #dcdfe6;padding:6px 10px;text-align:center;color:")
                    .append(rc).append(";font-weight:600\">")
                    .append(rule.triggered() ? "已触发" : "未触发").append("</td>")
                    .append("</tr>");
        }
        sb.append("</table>");

        // 处置建议
        sb.append("<h3 style=\"margin:18px 0 8px;font-size:15px;color:#303133;border-left:4px solid ")
                .append(color).append(";padding-left:8px\">四、处置建议</h3>");
        sb.append("<p style=\"margin:0 0 12px;padding:10px 12px;background:#fafafa;border-radius:4px;color:#606266\">")
                .append(esc(vo.advice())).append("</p>");

        // 说明
        sb.append("<h3 style=\"margin:18px 0 8px;font-size:15px;color:#303133;border-left:4px solid ")
                .append(color).append(";padding-left:8px\">五、说明</h3>");
        sb.append("<ol style=\"margin:0 0 12px;padding-left:22px;color:#606266;font-size:13px\">")
                .append("<li>本邮件由高校教务系统自动发送，请勿直接回复。</li>")
                .append("<li>如对成绩有疑问，请联系任课教师或所在院系教务员核实。</li>")
                .append("<li>查询入口：<a href=\"").append(esc(mailConfigService.portalUrl()))
                .append("\" style=\"color:#409eff\">").append(esc(mailConfigService.portalUrl()))
                .append("</a>（登录后进入「我的成绩」可查看完整预警明细）。</li>")
                .append("</ol>");

        sb.append("<p style=\"margin:24px 0 0;text-align:right;color:#606266\">")
                .append(esc(mailConfigService.sender())).append("<br>")
                .append(LocalDate.now().format(DATE)).append("</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private static String warningRuleColor(WarningRuleVO rule) {
        return rule.triggered() ? levelColor(rule.level()) : "#909399";
    }

    private static String th(String text, int width) {
        return "<th style=\"border:1px solid #dcdfe6;padding:6px 10px;text-align:left;font-weight:600;width:"
                + width + "px\">" + text + "</th>";
    }

    private static String td(String text, int width) {
        return "<td style=\"border:1px solid #dcdfe6;padding:6px 10px;width:" + width + "px\">" + text + "</td>";
    }

    private static String metricRow(String label, String value, boolean shaded) {
        String bg = shaded ? "background:#f5f7fa;" : "";
        return "<tr>"
                + "<td style=\"border:1px solid #dcdfe6;padding:6px 10px;" + bg + "width:60%\">" + esc(label) + "</td>"
                + "<td style=\"border:1px solid #dcdfe6;padding:6px 10px;" + bg
                + "text-align:center;font-weight:600\">" + esc(value) + "</td>"
                + "</tr>";
    }

    private static String esc(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /* ============================================================ 发送 */

    /** 是否为真实发送模式（由管理员在「邮件配置」页控制）。 */
    public boolean isRealSendEnabled() {
        return mailConfigService.isRealSendEnabled();
    }

    /**
     * 真实投递邮件。仅在邮件配置开启真实发送时调用。
     *
     * @throws Exception SMTP 异常，由调用方记录为发送失败
     */
    public void send(String to, MailContent content) throws Exception {
        JavaMailSender sender = mailConfigService.mailSender();
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(effectiveFrom(), mailConfigService.fromName(), "UTF-8"));
        helper.setTo(to);
        helper.setSubject(content.subject());
        // 同时提供纯文本与 HTML，客户端自行选择
        helper.setText(content.plainText(), content.html());
        sender.send(message);
    }

    /** 模拟发送：把邮件内容写入日志，便于在无 SMTP 环境下核对文案。 */
    public void logSimulated(String to, MailContent content) {
        log.info("""
                
                ==================== 学业预警邮件（模拟发送，未真实投递） ====================
                收件人：{}
                主  题：{}
                ---------------------------------------------------------------------------
                {}
                ===========================================================================
                """, to, content.subject(), content.plainText());
    }

    /* ============================================================ 自检 */

    /**
     * 向指定地址发送一封测试邮件，用于接入真实邮箱后自检。
     * 无论成功失败都返回结构化的诊断信息（含 SMTP 配置摘要与原始错误），
     * 便于快速定位"配置了但发不出去"的原因。
     */
    public Map<String, Object> sendTestMail(String to) {
        Map<String, Object> result = new LinkedHashMap<>(smtpConfig());
        result.put("to", to);

        if (!StringUtils.hasText(to)) {
            result.put("success", false);
            result.put("message", "当前登录账号尚未登记邮箱，无法接收测试邮件。请先在「个人中心 → 邮箱设置」中填写。");
            return result;
        }
        if (!isRealSendEnabled()) {
            result.put("success", false);
            result.put("message", "当前为模拟发送模式，不会真实投递。"
                    + "请在「系统管理 → 邮件配置」中填写 SMTP 信息并开启真实发送。");
            return result;
        }

        MailContent content = buildTestContent();
        try {
            send(to, content);
            result.put("success", true);
            result.put("message", "测试邮件已投递至 " + to + "，请查收（也请检查垃圾邮件箱）。");
        } catch (Exception e) {
            String reason = rootMessage(e);
            result.put("success", false);
            result.put("message", "发送失败：" + reason);
            result.put("hint", hintFor(reason));
            log.warn("测试邮件发送失败 to={} 原因={}", to, reason);
        }
        return result;
    }

    private MailContent buildTestContent() {
        String subject = mailConfigService.subjectPrefix() + "邮件发送测试";
        String plain = """
                %s：

                    这是一封来自高校教务系统的测试邮件，用于验证邮件通知功能是否配置成功。

                    收到本邮件说明 SMTP 配置正确，学业预警通知可以正常投递。

                    配置摘要：
                      SMTP 服务器：%s:%s
                      登录账号：%s
                      发件人：%s

                    本邮件由系统自动发送，请勿回复。

                                                        %s
                """.formatted(
                mailConfigService.fromName(), mailConfigService.host(), mailConfigService.port(),
                mailConfigService.username(), effectiveFrom(),
                mailConfigService.sender());
        String html = """
                <div style="font-family:'Microsoft YaHei',Arial,sans-serif;font-size:14px;color:#303133;line-height:1.9">
                  <h3 style="margin:0 0 12px;color:#409eff">邮件发送测试成功</h3>
                  <p>这是一封来自高校教务系统的测试邮件，用于验证邮件通知功能是否配置成功。</p>
                  <p>收到本邮件说明 SMTP 配置正确，<b>学业预警通知可以正常投递</b>。</p>
                  <table style="border-collapse:collapse;font-size:13px;margin-top:12px">
                    <tr><td style="border:1px solid #dcdfe6;padding:6px 10px;background:#f5f7fa">SMTP 服务器</td>
                        <td style="border:1px solid #dcdfe6;padding:6px 10px">%s:%s</td></tr>
                    <tr><td style="border:1px solid #dcdfe6;padding:6px 10px;background:#f5f7fa">登录账号</td>
                        <td style="border:1px solid #dcdfe6;padding:6px 10px">%s</td></tr>
                    <tr><td style="border:1px solid #dcdfe6;padding:6px 10px;background:#f5f7fa">发件人</td>
                        <td style="border:1px solid #dcdfe6;padding:6px 10px">%s</td></tr>
                  </table>
                  <p style="margin-top:16px;color:#909399;font-size:12px">本邮件由系统自动发送，请勿回复。</p>
                </div>
                """.formatted(mailConfigService.host(), mailConfigService.port(),
                        mailConfigService.username(), effectiveFrom());
        return new MailContent(subject, plain, html);
    }

    /** 按常见错误给出针对性提示。 */
    private static String hintFor(String reason) {
        String r = reason == null ? "" : reason.toLowerCase();
        if (r.contains("535") || r.contains("authentication") || r.contains("username and password")) {
            return "认证失败：授权码必须填邮箱服务商生成的「SMTP 授权码」，不是邮箱登录密码。"
                    + "请先在邮箱设置中开启 SMTP 服务并生成授权码，再到「邮件配置」中填写。";
        }
        if (r.contains("550") || r.contains("from")) {
            return "发件人被拒：多数邮箱要求发件人与登录账号一致，请把「发件人地址」留空（自动使用登录账号）。";
        }
        if (r.contains("timeout") || r.contains("timed out") || r.contains("connect")) {
            return "连接失败：请检查端口与加密方式是否匹配 —— 端口 465 勾选 SSL；"
                    + "端口 587 勾选 STARTTLS（二者择一，不要同时勾选）。";
        }
        if (r.contains("unknown host") || r.contains("nodename")) {
            return "无法解析 SMTP 服务器地址，请检查「SMTP 服务器」拼写。";
        }
        return "请在「邮件配置」中核对 SMTP 信息；完整错误见服务端日志。";
    }

    /** 取最贴近根因的异常信息。 */
    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
