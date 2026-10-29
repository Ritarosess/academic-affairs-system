package com.school.edu.controller;

import com.school.edu.common.Result;
import com.school.edu.dto.RegistrationDto;
import com.school.edu.service.MetaService;
import com.school.edu.service.RegistrationService;
import com.school.edu.vo.ClazzVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自助注册接口：无需登录（已在 SecurityConfig 放行 /api/auth/register/**）。
 */
@RestController
@RequestMapping("/api/auth/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;
    private final MetaService metaService;

    /** 提交注册申请，提交后需等待管理员审核。 */
    @PostMapping
    public Result<Void> register(@Valid @RequestBody RegistrationDto.RegisterRequest request) {
        registrationService.register(request);
        return Result.ok("注册申请已提交，请等待管理员审核", null);
    }

    /** 注册页的班级下拉框数据（学生注册时需要选择班级）。 */
    @GetMapping("/clazzs")
    public Result<List<ClazzVO>> clazzes() {
        return Result.ok(metaService.clazzes());
    }
}
