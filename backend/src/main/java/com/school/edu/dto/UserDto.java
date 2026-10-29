package com.school.edu.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 用户管理（学生/教师/管理员）请求体。 */
public final class UserDto {

    private UserDto() {
    }

    /**
     * 创建/编辑用户。密码不在此处设置：创建时使用系统默认密码，遗忘时由管理员重置，
     * 这样可以避免管理员接触到用户的自定义密码。
     */
    public record UserSaveRequest(
            Long id,
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 50, message = "用户名长度需在 3~50 位之间") String username,
            @NotBlank(message = "姓名不能为空") String realName,
            @NotBlank(message = "角色不能为空")
            @Pattern(regexp = "ADMIN|TEACHER|STUDENT", message = "角色只能是 ADMIN / TEACHER / STUDENT") String role,
            @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
            @Email(message = "邮箱格式不正确") String email,
            Integer status,
            /* 学生专属 */
            String studentNo,
            String gender,
            Long clazzId,
            /* 教师专属 */
            String teacherNo,
            String title,
            String department) {
    }

    public record StatusRequest(Integer status) {
    }
}
