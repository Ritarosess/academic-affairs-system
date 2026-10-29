package com.school.edu.controller;

import com.school.edu.common.Result;
import com.school.edu.dto.OrgDto;
import com.school.edu.entity.Grade;
import com.school.edu.entity.Major;
import com.school.edu.service.OrgService;
import com.school.edu.vo.ClazzVO;
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
 * 组织架构管理接口（管理员）：年级 / 专业 / 班级。
 */
@RestController
@RequestMapping("/api/admin/org")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrgController {

    private final OrgService orgService;

    /* ------------------------------------------------------------ 年级 */

    @GetMapping("/grades")
    public Result<List<Grade>> listGrades() {
        return Result.ok(orgService.listGrades());
    }

    @PostMapping("/grades")
    public Result<Grade> createGrade(@Valid @RequestBody OrgDto.GradeRequest request) {
        return Result.ok("创建成功", orgService.saveGrade(null, request));
    }

    @PutMapping("/grades/{id}")
    public Result<Grade> updateGrade(@PathVariable Long id, @Valid @RequestBody OrgDto.GradeRequest request) {
        return Result.ok("保存成功", orgService.saveGrade(id, request));
    }

    @DeleteMapping("/grades/{id}")
    public Result<Void> deleteGrade(@PathVariable Long id) {
        orgService.deleteGrade(id);
        return Result.ok("删除成功", null);
    }

    /* ------------------------------------------------------------ 专业 */

    @GetMapping("/majors")
    public Result<List<Major>> listMajors() {
        return Result.ok(orgService.listMajors());
    }

    @PostMapping("/majors")
    public Result<Major> createMajor(@Valid @RequestBody OrgDto.MajorRequest request) {
        return Result.ok("创建成功", orgService.saveMajor(null, request));
    }

    @PutMapping("/majors/{id}")
    public Result<Major> updateMajor(@PathVariable Long id, @Valid @RequestBody OrgDto.MajorRequest request) {
        return Result.ok("保存成功", orgService.saveMajor(id, request));
    }

    @DeleteMapping("/majors/{id}")
    public Result<Void> deleteMajor(@PathVariable Long id) {
        orgService.deleteMajor(id);
        return Result.ok("删除成功", null);
    }

    /* ------------------------------------------------------------ 班级 */

    @GetMapping("/clazzs")
    public Result<List<ClazzVO>> listClazzes() {
        return Result.ok(orgService.listClazzes());
    }

    @PostMapping("/clazzs")
    public Result<ClazzVO> createClazz(@Valid @RequestBody OrgDto.ClazzRequest request) {
        return Result.ok("创建成功", orgService.saveClazz(null, request));
    }

    @PutMapping("/clazzs/{id}")
    public Result<ClazzVO> updateClazz(@PathVariable Long id, @Valid @RequestBody OrgDto.ClazzRequest request) {
        return Result.ok("保存成功", orgService.saveClazz(id, request));
    }

    @DeleteMapping("/clazzs/{id}")
    public Result<Void> deleteClazz(@PathVariable Long id) {
        orgService.deleteClazz(id);
        return Result.ok("删除成功", null);
    }
}
