package com.school.edu.controller;

import com.school.edu.common.PageResult;
import com.school.edu.common.Result;
import com.school.edu.dto.CourseDto;
import com.school.edu.security.SecurityUtils;
import com.school.edu.service.CourseService;
import com.school.edu.vo.CourseVO;
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

/**
 * 课程审核接口（管理员）。课程由教师申报，管理员仅负责查看与审核（通过 / 拒绝）。
 */
@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseController {

    private final CourseService courseService;

    /** 课程审核列表，可按审核状态（PENDING/APPROVED/REJECTED）、学期、关键字过滤。 */
    @GetMapping
    public Result<PageResult<CourseVO>> page(@RequestParam(required = false) Long semesterId,
                                             @RequestParam(required = false) String auditStatus,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(courseService.pageForAdmin(semesterId, auditStatus, keyword, page, size));
    }

    /** 审核通过。 */
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                @Valid @RequestBody(required = false) CourseDto.CourseReviewRequest request) {
        String remark = request == null ? null : request.remark();
        courseService.approve(id, SecurityUtils.currentUserId(), remark);
        return Result.ok("已通过该课程申报", null);
    }

    /** 审核拒绝。 */
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @Valid @RequestBody(required = false) CourseDto.CourseReviewRequest request) {
        String remark = request == null ? null : request.remark();
        courseService.reject(id, SecurityUtils.currentUserId(), remark);
        return Result.ok("已拒绝该课程申报", null);
    }
}
