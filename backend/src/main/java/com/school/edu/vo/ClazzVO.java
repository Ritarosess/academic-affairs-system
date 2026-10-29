package com.school.edu.vo;

/** 班级视图对象，附带所属专业与年级名称。 */
public record ClazzVO(
        Long id,
        String name,
        Long majorId,
        String majorName,
        Long gradeId,
        String gradeName,
        String counselor) {
}
