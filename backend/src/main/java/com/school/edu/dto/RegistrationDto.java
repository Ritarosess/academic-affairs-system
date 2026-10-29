package com.school.edu.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 注册相关请求体。 */
public final class RegistrationDto {

    private RegistrationDto() {
    }

    /** 自助注册申请：只允许注册学生或教师账号（管理员账号由系统内置）。 */
    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 50, message = "用户名长度需在 3~50 位之间") String username,
            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间") String password,
            @NotBlank(message = "姓名不能为空")
            @Size(max = 50, message = "姓名不能超过 50 个字符") String realName,
            @NotBlank(message = "角色不能为空")
            @Pattern(regexp = "STUDENT|TEACHER", message = "只能注册学生或教师账号") String role,
            @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确") String email,
            @Pattern(regexp = "^$|^男$|^女$", message = "性别只能是男或女") String gender,
            @Size(max = 30, message = "学号不能超过 30 个字符") String studentNo,
            Long clazzId,
            @Size(max = 30, message = "工号不能超过 30 个字符") String teacherNo,
            @Size(max = 30, message = "职称不能超过 30 个字符") String title,
            @Size(max = 100, message = "院系不能超过 100 个字符") String department) {
    }

    /** 管理员审核意见（通过/拒绝共用，remark 可选）。 */
    public record ReviewRequest(
            @Size(max = 255, message = "审核意见不能超过 255 个字符") String remark) {
    }
}
