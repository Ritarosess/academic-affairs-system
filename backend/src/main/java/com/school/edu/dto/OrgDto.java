package com.school.edu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 组织架构（年级/专业/班级）请求体。 */
public final class OrgDto {

    private OrgDto() {
    }

    public record GradeRequest(
            Long id,
            @NotBlank(message = "年级名称不能为空") String name,
            @NotNull(message = "入学年份不能为空") Integer enrollYear) {
    }

    public record MajorRequest(
            Long id,
            @NotBlank(message = "专业名称不能为空") String name,
            @NotBlank(message = "所属院系不能为空") String department) {
    }

    public record ClazzRequest(
            Long id,
            @NotBlank(message = "班级名称不能为空") String name,
            @NotNull(message = "请选择所属专业") Long majorId,
            @NotNull(message = "请选择所属年级") Long gradeId,
            String counselor) {
    }
}
