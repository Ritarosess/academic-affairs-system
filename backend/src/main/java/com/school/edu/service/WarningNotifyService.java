package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.edu.common.PageResult;
import com.school.edu.entity.WarningNotification;
import com.school.edu.mapper.WarningNotificationMapper;
import com.school.edu.vo.NotificationVO;
import com.school.edu.vo.NotifyResultVO;
import com.school.edu.vo.WarningVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学业预警邮件通知服务：负责批量发送的组织、逐条结果判定与发送记录落库。
 *
 * <p>发送结果分四种：SENT 已发送 / SIMULATED 模拟发送 / FAILED 发送失败 / SKIPPED 已跳过
 * （未触发预警或账号未登记邮箱）。每个学生的结果相互独立，
 * 单人失败不会影响其他人，也不会中断整批发送。</p>
 *
 * <p>本服务**不加事务**：邮件发送是外部 I/O，耗时不定的网络调用不应被包在数据库事务里；
 * 每条发送记录各自独立落库，从而保证"已发出的邮件一定有记录，失败的邮件也留有原因"。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarningNotifyService {

    private static final int ERROR_MAX_LENGTH = 480;

    private final WarningService warningService;
    private final WarningMailService mailService;
    private final WarningNotificationMapper notificationMapper;

    /* ============================================================ 发送 */

    /**
     * 向指定学生发送学业预警通知邮件。
     *
     * @param studentIds 学生 ID（按传入顺序处理）
     * @param operatorId 操作管理员 ID
     */
    public NotifyResultVO.Summary notify(List<Long> studentIds, Long operatorId) {
        boolean realSend = mailService.isRealSendEnabled();
        Map<Long, WarningVO> byId = warningService.byStudentIds(studentIds).stream()
                .collect(Collectors.toMap(WarningVO::studentId, Function.identity(), (a, b) -> a));

        List<NotifyResultVO.Item> items = new ArrayList<>();
        int sent = 0;
        int simulated = 0;
        int failed = 0;
        int skipped = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Long studentId : studentIds) {
            WarningVO vo = byId.get(studentId);
            if (vo == null) {
                items.add(item(null, studentId, "SKIPPED", "已跳过", null, "学生不存在或已被删除"));
                skipped++;
                continue;
            }
            // 只有已触发预警的学生才发送
            if (!vo.triggered()) {
                items.add(item(vo, studentId, "SKIPPED", "已跳过", null, "该生当前未触发学业预警，无需发送"));
                skipped++;
                continue;
            }
            // 账号未登记邮箱
            if (!StringUtils.hasText(vo.email())) {
                record(vo, "(未发送)", null, "SKIPPED", "该生账号未登记邮箱", operatorId, now);
                items.add(item(vo, studentId, "SKIPPED", "已跳过", null, "该生账号未登记邮箱，无法发送"));
                skipped++;
                continue;
            }

            WarningMailService.MailContent content = mailService.build(vo);
            String status;
            String statusLabel;
            String message;
            String error = null;

            if (realSend) {
                try {
                    mailService.send(vo.email(), content);
                    status = "SENT";
                    statusLabel = "已发送";
                    message = "邮件已投递至 " + vo.email();
                    sent++;
                } catch (Exception e) {
                    status = "FAILED";
                    statusLabel = "发送失败";
                    error = rootMessage(e);
                    message = "发送失败：" + error;
                    failed++;
                    log.warn("学业预警邮件发送失败 studentNo={} email={} 原因={}", vo.studentNo(), vo.email(), error);
                }
            } else {
                mailService.logSimulated(vo.email(), content);
                status = "SIMULATED";
                statusLabel = "模拟发送";
                message = "当前为模拟发送模式（app.mail.enabled=false），邮件内容已输出到服务端日志";
                simulated++;
            }

            record(vo, content.subject(), content.plainText(), status, error, operatorId, now);
            items.add(item(vo, studentId, status, statusLabel, content.subject(), message));
        }

        log.info("学业预警邮件通知完成：共 {} 人，已发送 {}，模拟 {}，失败 {}，跳过 {}（真实发送={}）",
                studentIds.size(), sent, simulated, failed, skipped, realSend);

        return new NotifyResultVO.Summary(studentIds.size(), sent, simulated, failed, skipped, realSend, items);
    }

    private NotifyResultVO.Item item(WarningVO vo, Long studentId, String status, String statusLabel,
                                     String subject, String message) {
        if (vo == null) {
            return new NotifyResultVO.Item(studentId, null, null, null, null, null,
                    status, statusLabel, subject, message);
        }
        return new NotifyResultVO.Item(vo.studentId(), vo.studentNo(), vo.studentName(), vo.email(),
                vo.level(), vo.levelLabel(), status, statusLabel, subject, message);
    }

    /** 落库一条发送记录。 */
    private void record(WarningVO vo, String subject, String content, String status,
                        String error, Long operatorId, LocalDateTime sendTime) {
        WarningNotification entity = new WarningNotification();
        entity.setStudentId(vo.studentId());
        entity.setUserId(vo.userId());
        entity.setStudentNo(vo.studentNo());
        entity.setStudentName(vo.studentName());
        entity.setEmail(vo.email());
        entity.setLevel(vo.level());
        entity.setLevelLabel(vo.levelLabel());
        entity.setFailedCredits(vo.failedCredits());
        entity.setMaxSemesterFailedCount(vo.maxSemesterFailedCount());
        entity.setSubject(subject);
        entity.setContent(content);
        entity.setStatus(status);
        entity.setErrorMessage(error == null ? null : truncate(error));
        entity.setOperatorId(operatorId);
        entity.setSendTime(sendTime);
        notificationMapper.insert(entity);
    }

    private static String truncate(String text) {
        return text.length() <= ERROR_MAX_LENGTH ? text : text.substring(0, ERROR_MAX_LENGTH) + "…";
    }

    /** 取最贴近根因的异常信息，避免把整条调用链塞进记录。 */
    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    /* ============================================================ 历史记录 */

    public PageResult<NotificationVO> history(Long studentId, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = size <= 0 ? 10 : size;
        Page<WarningNotification> result = notificationMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<WarningNotification>lambdaQuery()
                        .eq(studentId != null, WarningNotification::getStudentId, studentId)
                        .orderByDesc(WarningNotification::getSendTime)
                        .orderByDesc(WarningNotification::getId));
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(WarningNotifyService::toVO).toList());
    }

    private static NotificationVO toVO(WarningNotification entity) {
        return new NotificationVO(entity.getId(), entity.getStudentId(), entity.getStudentNo(),
                entity.getStudentName(), entity.getEmail(), entity.getLevel(), entity.getLevelLabel(),
                entity.getSubject(), entity.getStatus(), statusLabel(entity.getStatus()),
                entity.getErrorMessage(), entity.getSendTime());
    }

    private static String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "SENT" -> "已发送";
            case "SIMULATED" -> "模拟发送";
            case "FAILED" -> "发送失败";
            case "SKIPPED" -> "已跳过";
            default -> status;
        };
    }
}
