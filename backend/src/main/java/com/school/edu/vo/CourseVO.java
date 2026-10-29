package com.school.edu.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程视图对象。remaining 为剩余名额，selected 表示「当前学生是否已选」该课程。
 * auditStatus 为课程审核状态（教师申报、管理员审核）。
 */
public record CourseVO(
        Long id,
        String courseNo,
        String name,
        BigDecimal credit,
        Integer hours,
        String courseType,
        Long teacherId,
        String teacherName,
        Long semesterId,
        String semesterName,
        Integer capacity,
        Integer selectedCount,
        Integer remaining,
        Integer weekday,
        String weekdayLabel,
        Integer startSection,
        Integer endSection,
        String location,
        String description,
        Integer status,
        Boolean selected,
        String auditStatus,
        String auditStatusLabel,
        String reviewRemark,
        LocalDateTime reviewTime) {
}
