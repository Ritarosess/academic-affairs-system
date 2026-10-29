package com.school.edu.controller;

import com.school.edu.common.Result;
import com.school.edu.entity.Grade;
import com.school.edu.entity.Major;
import com.school.edu.entity.Semester;
import com.school.edu.service.MetaService;
import com.school.edu.vo.ClazzVO;
import com.school.edu.vo.TeacherOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 公共元数据接口：为前端下拉框与选课周期提示提供数据。需登录访问。
 */
@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {

    private final MetaService metaService;

    @GetMapping("/teachers")
    public Result<List<TeacherOptionVO>> teachers() {
        return Result.ok(metaService.teachers());
    }

    @GetMapping("/semesters")
    public Result<List<Semester>> semesters() {
        return Result.ok(metaService.semesters());
    }

    @GetMapping("/grades")
    public Result<List<Grade>> grades() {
        return Result.ok(metaService.grades());
    }

    @GetMapping("/majors")
    public Result<List<Major>> majors() {
        return Result.ok(metaService.majors());
    }

    @GetMapping("/clazzs")
    public Result<List<ClazzVO>> clazzes() {
        return Result.ok(metaService.clazzes());
    }

    @GetMapping("/selection-window")
    public Result<Map<String, Object>> selectionWindow() {
        return Result.ok(metaService.selectionWindow());
    }
}
