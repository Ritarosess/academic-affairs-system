package com.school.edu.controller;

import com.school.edu.common.Result;
import com.school.edu.security.LoginUser;
import com.school.edu.security.SecurityUtils;
import com.school.edu.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作台统计接口：按当前登录角色返回统计卡片数据。
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        LoginUser loginUser = SecurityUtils.currentUser();
        return Result.ok(dashboardService.summary(loginUser.getRole(), loginUser.getId()));
    }
}
