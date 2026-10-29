package com.school.edu.controller;

import com.school.edu.common.Result;
import com.school.edu.dto.MailConfigDto;
import com.school.edu.security.SecurityUtils;
import com.school.edu.service.MailConfigService;
import com.school.edu.service.WarningMailService;
import com.school.edu.service.WarningService;
import com.school.edu.vo.MailConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员「邮件配置」接口：查看 / 保存 SMTP 配置、发送测试邮件。
 * 配置存于数据库单行表，保存后即时生效，无需重启。
 */
@RestController
@RequestMapping("/api/admin/mail-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMailConfigController {

    private final MailConfigService mailConfigService;
    private final WarningMailService warningMailService;
    private final WarningService warningService;

    /** 查看当前邮件配置（授权码脱敏，仅返回是否已设置）。 */
    @GetMapping
    public Result<MailConfigVO> get() {
        return Result.ok(mailConfigService.view());
    }

    /** 保存邮件配置；请求体中授权码留空表示不修改原授权码。 */
    @PutMapping
    public Result<MailConfigVO> save(@Valid @RequestBody MailConfigDto.SaveRequest request) {
        return Result.ok("邮件配置已保存", mailConfigService.save(request));
    }

    /**
     * 发送测试邮件。请求体 to 留空时发送给当前登录管理员登记的邮箱。
     * 返回结构化诊断信息（成功/失败、原始 SMTP 错误、针对性提示）。
     */
    @PostMapping("/test")
    public Result<Map<String, Object>> test(@Valid @RequestBody(required = false) MailConfigDto.TestRequest request) {
        String to = request == null ? null : request.to();
        if (!StringUtils.hasText(to)) {
            to = warningService.emailOf(SecurityUtils.currentUserId());
        }
        Map<String, Object> data = warningMailService.sendTestMail(to);
        boolean ok = Boolean.TRUE.equals(data.get("success"));
        return Result.ok(ok ? "测试邮件发送成功" : "测试邮件发送失败", data);
    }
}
