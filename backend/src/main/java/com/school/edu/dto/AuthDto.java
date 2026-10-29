package com.school.edu.dto;

import com.school.edu.vo.UserVO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 认证相关请求/响应体。 */
public final class AuthDto {

    private AuthDto() {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "原密码不能为空") String oldPassword,
            @NotBlank(message = "新密码不能为空")
            @Size(min = 6, max = 32, message = "新密码长度需在 6~32 位之间") String newPassword) {
    }

    /**
     * 修改本人邮箱。邮箱用于接收学业预警通知，因此需要保证唯一性 ——
     * 否则学生可填写他人邮箱从而收到他人的成绩通知。
     */
    public record UpdateEmailRequest(
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Size(max = 100, message = "邮箱长度不能超过 100 个字符") String email) {
    }

    public record LoginResponse(String token, String tokenType, long expiresIn, UserVO user) {
    }
}
