package com.school.edu.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成绩视图对象（学生端成绩查询）。 */
public record ScoreVO(
        Long id,
        Long courseId,
        String courseNo,
        String courseName,
        BigDecimal credit,
        String courseType,
        String teacherName,
        String semesterName,
        BigDecimal usualScore,
        BigDecimal examScore,
        BigDecimal totalScore,
        /** 绩点（五级制换算结果，未出成绩为 null） */
        BigDecimal gradePoint,
        LocalDateTime submitTime) {
}
