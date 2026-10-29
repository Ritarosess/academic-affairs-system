package com.school.edu.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 课程相关请求体。课程由教师申报、管理员审核，任课教师取登录教师，无需前端传入。 */
public final class CourseDto {

    private CourseDto() {
    }

    /** 教师申报 / 修改课程请求（不含 teacherId，由服务端绑定当前登录教师）。 */
    public record TeacherCourseRequest(
            @NotBlank(message = "课程号不能为空") String courseNo,
            @NotBlank(message = "课程名称不能为空") String name,
            @NotNull(message = "学分不能为空")
            @DecimalMin(value = "0.5", message = "学分必须大于 0") BigDecimal credit,
            @NotNull(message = "学时不能为空")
            @Min(value = 1, message = "学时必须大于 0") Integer hours,
            @NotBlank(message = "课程性质不能为空") String courseType,
            @NotNull(message = "请选择开课学期") Long semesterId,
            @NotNull(message = "容量不能为空")
            @Min(value = 1, message = "容量必须大于 0") Integer capacity,
            Integer weekday,
            Integer startSection,
            Integer endSection,
            String location,
            String description,
            Integer status) {
    }

    /** 管理员审核课程请求。 */
    public record CourseReviewRequest(
            @Size(max = 255, message = "审核意见不能超过 255 个字符") String remark) {
    }
}
