package com.school.edu.controller;

import com.school.edu.common.PageResult;
import com.school.edu.common.Result;
import com.school.edu.dto.RegistrationDto;
import com.school.edu.security.SecurityUtils;
import com.school.edu.service.RegistrationService;
import com.school.edu.vo.RegistrationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 注册申请审核接口：仅管理员可用（/api/admin/** 与 @PreAuthorize 双重约束）。 */
@RestController
@RequestMapping("/api/admin/registrations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRegistrationController {

    private final RegistrationService registrationService;

    /** 分页查询注册申请，可按状态过滤（PENDING / APPROVED / REJECTED）。 */
    @GetMapping
    public Result<PageResult<RegistrationVO>> page(@RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size) {
        return Result.ok(registrationService.page(status, page, size));
    }

    /** 审核通过：创建正式账号与对应学生/教师档案。 */
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @Valid @RequestBody RegistrationDto.ReviewRequest request) {
        registrationService.approve(id, SecurityUtils.currentUserId(), request);
        return Result.ok("已通过该注册申请", null);
    }

    /** 审核拒绝。 */
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @Valid @RequestBody RegistrationDto.ReviewRequest request) {
        registrationService.reject(id, SecurityUtils.currentUserId(), request);
        return Result.ok("已拒绝该注册申请", null);
    }
}
