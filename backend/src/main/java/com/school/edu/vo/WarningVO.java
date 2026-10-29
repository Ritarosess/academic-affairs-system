package com.school.edu.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学业红线预警视图对象：既用于学生查看本人预警，也用于管理员预警名单。
 */
public record WarningVO(
        Long studentId,
        /** 关联账号 ID，邮件通知记录使用 */
        Long userId,
        String studentNo,
        String studentName,
        String gender,
        Long clazzId,
        String clazzName,
        String majorName,
        String gradeName,
        /** 账号中登记的邮箱，用于发送预警通知邮件；为空表示该生未登记邮箱 */
        String email,
        /** 最终等级：NORMAL / YELLOW / ORANGE / RED */
        String level,
        String levelLabel,
        /** 前端标签类型提示：success / warning / danger */
        String tagType,
        boolean triggered,
        /** 一句话结论 */
        String summary,
        /** 处置建议 */
        String advice,
        /* ---- 指标 ---- */
        BigDecimal failedCredits,
        Integer failedCourseCount,
        Integer scoredCourseCount,
        BigDecimal scoredCredits,
        BigDecimal passedCredits,
        Integer maxSemesterFailedCount,
        /* ---- 逐条规则判定 ---- */
        List<WarningRuleVO> rules) {
}
