package com.school.edu.controller;

import com.school.edu.common.Result;
import com.school.edu.dto.AuthDto;
import com.school.edu.security.SecurityUtils;
import com.school.edu.service.AuthService;
import com.school.edu.vo.ProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录、当前用户档案、修改密码。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<AuthDto.LoginResponse> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return Result.ok("登录成功", authService.login(request));
    }

    @GetMapping("/me")
    public Result<ProfileVO> me() {
        return Result.ok(authService.profile(SecurityUtils.currentUserId()));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody AuthDto.ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.currentUserId(), request);
        return Result.ok("密码修改成功", null);
    }

    /** 修改本人邮箱（学业预警通知的收件地址）。 */
    @PutMapping("/email")
    public Result<ProfileVO> updateEmail(@Valid @RequestBody AuthDto.UpdateEmailRequest request) {
        return Result.ok("邮箱已更新", authService.updateEmail(SecurityUtils.currentUserId(), request));
    }
}
