package com.school.edu.controller;

import com.school.edu.common.PageResult;
import com.school.edu.common.Result;
import com.school.edu.dto.UserDto;
import com.school.edu.security.SecurityUtils;
import com.school.edu.service.UserService;
import com.school.edu.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户管理接口（管理员）：学生 / 教师 / 管理员用户维护与密码重置。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public Result<PageResult<UserVO>> page(@RequestParam(required = false) String role,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(userService.page(role, keyword, page, size));
    }

    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody UserDto.UserSaveRequest request) {
        return Result.ok("创建成功", userService.create(request));
    }

    @PutMapping("/{id}")
    public Result<UserVO> update(@PathVariable Long id,
                                 @Valid @RequestBody UserDto.UserSaveRequest request) {
        return Result.ok("保存成功", userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id, SecurityUtils.currentUserId());
        return Result.ok("删除成功", null);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestBody UserDto.StatusRequest request) {
        userService.updateStatus(id, request.status(), SecurityUtils.currentUserId());
        return Result.ok("状态已更新", null);
    }

    @PutMapping("/{id}/reset-password")
    public Result<Map<String, String>> resetPassword(@PathVariable Long id) {
        return Result.ok("密码已重置为默认密码", userService.resetPassword(id));
    }
}
