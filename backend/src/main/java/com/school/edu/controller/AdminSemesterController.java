package com.school.edu.controller;

import com.school.edu.common.Result;
import com.school.edu.dto.SemesterDto;
import com.school.edu.entity.Semester;
import com.school.edu.service.SemesterService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学期与选课周期接口（管理员）。
 */
@RestController
@RequestMapping("/api/admin/semesters")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSemesterController {

    private final SemesterService semesterService;

    @GetMapping
    public Result<List<Semester>> list() {
        return Result.ok(semesterService.list());
    }

    @PostMapping
    public Result<Semester> create(@Valid @RequestBody SemesterDto.SemesterRequest request) {
        return Result.ok("创建成功", semesterService.save(null, request));
    }

    @PutMapping("/{id}")
    public Result<Semester> update(@PathVariable Long id,
                                   @Valid @RequestBody SemesterDto.SemesterRequest request) {
        return Result.ok("保存成功", semesterService.save(id, request));
    }

    @PutMapping("/{id}/current")
    public Result<Void> setCurrent(@PathVariable Long id) {
        semesterService.setCurrent(id);
        return Result.ok("已设为当前学期", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        semesterService.delete(id);
        return Result.ok("删除成功", null);
    }
}
