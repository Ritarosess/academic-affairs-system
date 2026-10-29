package com.school.edu.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 选课记录视图对象（学生课表 / 已选课程）。 */
public record SelectionVO(
        Long selectionId,
        Long courseId,
        String courseNo,
        String courseName,
        BigDecimal credit,
        String courseType,
        String teacherName,
        String semesterName,
        Integer weekday,
        String weekdayLabel,
        Integer startSection,
        Integer endSection,
        String location,
        String status,
        String statusLabel,
        Boolean scored,
        LocalDateTime selectTime,
        LocalDateTime dropTime) {
}
