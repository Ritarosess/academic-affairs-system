package com.school.edu.controller;

import com.school.edu.common.PageResult;
import com.school.edu.common.Result;
import com.school.edu.dto.WarningNotifyDto;
import com.school.edu.security.SecurityUtils;
import com.school.edu.service.MailConfigService;
import com.school.edu.service.WarningMailService;
import com.school.edu.service.WarningNotifyService;
import com.school.edu.service.WarningService;
import com.school.edu.vo.NotificationVO;
import com.school.edu.vo.NotifyResultVO;
import com.school.edu.vo.WarningVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学业红线预警接口（教务处 / 管理员），含预警名单、总览与邮件通知。
 */
@RestController
@RequestMapping("/api/admin/warnings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminWarningController {

    private final WarningService warningService;
    private final WarningNotifyService warningNotifyService;
    private final WarningMailService warningMailService;
    private final MailConfigService mailConfigService;

    /**
     * 全校学业预警名单（内存分页，按严重程度倒序）。
     *
     * @param level   等级过滤：留空 = 只看已触发预警的学生；ALL = 全部学生；也可传 RED/ORANGE/YELLOW/NORMAL
     * @param clazzId 班级过滤
     * @param majorId 专业过滤
     * @param keyword 学号或姓名模糊查询
     */
    @GetMapping
    public Result<PageResult<WarningVO>> page(@RequestParam(required = false) String level,
                                              @RequestParam(required = false) Long clazzId,
                                              @RequestParam(required = false) Long majorId,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.ok(warningService.page(level, clazzId, majorId, keyword, page, size));
    }

    /** 预警总览：各等级人数、当前生效阈值，以及邮件发送模式。 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        Map<String, Object> data = new LinkedHashMap<>(warningService.summary());
        // 供前端提示"当前为模拟发送模式"
        data.put("mailEnabled", mailConfigService.isRealSendEnabled());
        // 返回**实际生效**的发件人：app.mail.from 留空时会回退到 SMTP 登录账号，
        // 直接返回原始配置会得到空值，与真实发送行为不一致。
        data.put("mailFrom", warningMailService.effectiveFrom());
        return Result.ok(data);
    }

    /** 邮件内容预览：发送前供管理员核对收件人与文案（返回纯文本，前端不做 HTML 渲染）。 */
    @GetMapping("/preview/{studentId}")
    public Result<Map<String, Object>> preview(@PathVariable Long studentId) {
        WarningVO vo = warningService.byStudentIds(java.util.List.of(studentId)).stream()
                .findFirst()
                .orElseThrow(() -> com.school.edu.common.BusinessException.notFound("学生不存在"));
        WarningMailService.MailContent content = warningMailService.build(vo);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentId", vo.studentId());
        data.put("studentNo", vo.studentNo());
        data.put("studentName", vo.studentName());
        data.put("email", vo.email());
        data.put("level", vo.level());
        data.put("levelLabel", vo.levelLabel());
        data.put("triggered", vo.triggered());
        data.put("subject", content.subject());
        data.put("content", content.plainText());
        data.put("mailEnabled", mailConfigService.isRealSendEnabled());
        return Result.ok(data);
    }

    /**
     * 批量发送学业预警邮件通知。
     * 仅对已触发预警且账号登记了邮箱的学生发送；未触发或无邮箱的学生记为"已跳过"。
     */
    @PostMapping("/notify")
    public Result<NotifyResultVO.Summary> notify(@Valid @RequestBody WarningNotifyDto.NotifyRequest request) {
        NotifyResultVO.Summary summary = warningNotifyService.notify(
                request.studentIds(), SecurityUtils.currentUserId());
        String message = "共 %d 人：已发送 %d，模拟发送 %d，失败 %d，跳过 %d".formatted(
                summary.total(), summary.sent(), summary.simulated(), summary.failed(), summary.skipped());
        return Result.ok(message, summary);
    }

    /** 邮件发送历史（可按学生过滤）。 */
    @GetMapping("/notifications")
    public Result<PageResult<NotificationVO>> notifications(@RequestParam(required = false) Long studentId,
                                                            @RequestParam(defaultValue = "1") long page,
                                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(warningNotifyService.history(studentId, page, size));
    }

    /**
     * 邮件自检：向当前登录管理员自己的邮箱发送一封测试邮件。
     * 接入真实邮箱后用它验证配置，失败时返回原始 SMTP 错误与针对性提示。
     */
    @PostMapping("/test-mail")
    public Result<Map<String, Object>> testMail() {
        String email = warningService.emailOf(SecurityUtils.currentUserId());
        Map<String, Object> data = warningMailService.sendTestMail(email);
        boolean ok = Boolean.TRUE.equals(data.get("success"));
        return Result.ok(ok ? "测试邮件发送成功" : "测试邮件发送失败", data);
    }

    /** 当前 SMTP 配置摘要（不含密码），供前端展示与排错。 */
    @GetMapping("/mail-config")
    public Result<Map<String, Object>> mailConfig() {
        return Result.ok(warningMailService.smtpConfig());
    }
}
