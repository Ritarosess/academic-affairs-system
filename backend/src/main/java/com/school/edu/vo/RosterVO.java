package com.school.edu.vo;

import java.math.BigDecimal;

/** 选课学生名单视图对象（教师端成绩录入使用）。 */
public record RosterVO(
        Long selectionId,
        Long studentId,
        String studentNo,
        String studentName,
        String gender,
        String clazzName,
        Long scoreId,
        BigDecimal usualScore,
        BigDecimal examScore,
        BigDecimal totalScore,
        /** 绩点（五级制换算结果，未录入成绩为 null） */
        BigDecimal gradePoint,
        Boolean scored,
        /** 学业预警等级：NORMAL / YELLOW / ORANGE / RED（教师关联查看用） */
        String warningLevel,
        String warningLevelLabel) {
}
