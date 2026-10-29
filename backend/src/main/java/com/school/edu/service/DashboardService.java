package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.school.edu.entity.Course;
import com.school.edu.entity.CourseSelection;
import com.school.edu.entity.Score;
import com.school.edu.entity.Semester;
import com.school.edu.entity.Student;
import com.school.edu.entity.Teacher;
import com.school.edu.mapper.CourseMapper;
import com.school.edu.mapper.CourseSelectionMapper;
import com.school.edu.mapper.ScoreMapper;
import com.school.edu.mapper.StudentMapper;
import com.school.edu.mapper.SysUserMapper;
import com.school.edu.mapper.TeacherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作台统计：按当前角色返回首页统计卡片数据。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SysUserMapper sysUserMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CourseMapper courseMapper;
    private final CourseSelectionMapper selectionMapper;
    private final ScoreMapper scoreMapper;
    private final MetaService metaService;
    private final LookupService lookupService;
    private final WarningService warningService;

    public Map<String, Object> summary(String role, Long userId) {
        return switch (role) {
            case "ADMIN" -> adminSummary();
            case "TEACHER" -> teacherSummary(userId);
            case "STUDENT" -> studentSummary(userId);
            default -> Map.of();
        };
    }

    private Map<String, Object> adminSummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("studentCount", sysUserMapper.selectCount(
                Wrappers.<com.school.edu.entity.SysUser>lambdaQuery().eq(com.school.edu.entity.SysUser::getRole, "STUDENT")));
        map.put("teacherCount", sysUserMapper.selectCount(
                Wrappers.<com.school.edu.entity.SysUser>lambdaQuery().eq(com.school.edu.entity.SysUser::getRole, "TEACHER")));
        map.put("courseCount", courseMapper.selectCount(null));
        map.put("selectionCount", selectionMapper.selectCount(
                Wrappers.<CourseSelection>lambdaQuery().eq(CourseSelection::getStatus, "SELECTED")));
        Semester current = lookupService.currentSemester();
        map.put("currentSemester", current == null ? null : current.getName());
        map.put("selectionOpen", metaService.isSelectionOpen());
        // 学业红线预警概览
        Map<String, Object> warning = warningService.summary();
        map.put("warningFlagged", warning.get("flaggedStudents"));
        map.put("warningRed", warning.get("redCount"));
        map.put("warningOrange", warning.get("orangeCount"));
        map.put("warningYellow", warning.get("yellowCount"));
        return map;
    }

    private Map<String, Object> teacherSummary(Long userId) {
        Map<String, Object> map = new LinkedHashMap<>();
        Teacher teacher = teacherMapper.selectOne(Wrappers.<Teacher>lambdaQuery().eq(Teacher::getUserId, userId));
        if (teacher == null) {
            map.put("courseCount", 0);
            map.put("studentCount", 0);
            map.put("gradedCount", 0);
            map.put("ungradedCount", 0);
            return map;
        }
        // 教师工作台只统计已通过审核、实际开设的课程
        List<Course> courses = courseMapper.selectList(
                Wrappers.<Course>lambdaQuery()
                        .eq(Course::getTeacherId, teacher.getId())
                        .eq(Course::getAuditStatus, CourseService.APPROVED));
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        long studentCount = 0;
        long gradedCount = 0;
        if (!courseIds.isEmpty()) {
            studentCount = selectionMapper.selectCount(Wrappers.<CourseSelection>lambdaQuery()
                    .in(CourseSelection::getCourseId, courseIds)
                    .eq(CourseSelection::getStatus, "SELECTED"));
            gradedCount = scoreMapper.selectCount(Wrappers.<Score>lambdaQuery()
                    .in(Score::getCourseId, courseIds));
        }
        map.put("courseCount", courses.size());
        map.put("studentCount", studentCount);
        map.put("gradedCount", gradedCount);
        map.put("ungradedCount", Math.max(studentCount - gradedCount, 0));

        // 教师关联查看：本人任教课程中处于学业预警状态的学生人数
        long warned = 0;
        if (!courseIds.isEmpty()) {
            Set<Long> studentIds = selectionMapper.selectList(Wrappers.<CourseSelection>lambdaQuery()
                            .in(CourseSelection::getCourseId, courseIds)
                            .eq(CourseSelection::getStatus, "SELECTED"))
                    .stream().map(CourseSelection::getStudentId).collect(Collectors.toSet());
            warned = warningService.levelNames(studentIds).values().stream()
                    .filter(level -> !"NORMAL".equals(level))
                    .count();
        }
        map.put("warningStudentCount", warned);
        return map;
    }

    private Map<String, Object> studentSummary(Long userId) {
        Map<String, Object> map = new LinkedHashMap<>();
        Student student = studentMapper.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId, userId));
        if (student == null) {
            map.put("selectedCount", 0);
            map.put("totalCredit", BigDecimal.ZERO);
            map.put("scoredCount", 0);
            map.put("selectionOpen", metaService.isSelectionOpen());
            return map;
        }
        Semester current = lookupService.currentSemester();
        List<CourseSelection> selections = selectionMapper.selectList(Wrappers.<CourseSelection>lambdaQuery()
                .eq(CourseSelection::getStudentId, student.getId())
                .eq(CourseSelection::getStatus, "SELECTED"));
        Set<Long> courseIds = selections.stream().map(CourseSelection::getCourseId).collect(Collectors.toSet());
        BigDecimal totalCredit = BigDecimal.ZERO;
        if (!courseIds.isEmpty() && current != null) {
            totalCredit = courseMapper.selectBatchIds(courseIds).stream()
                    .filter(c -> current.getId().equals(c.getSemesterId()))
                    .map(Course::getCredit)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        map.put("selectedCount", courseIds.size());
        map.put("totalCredit", totalCredit);
        map.put("scoredCount", scoreMapper.selectCount(
                Wrappers.<Score>lambdaQuery().eq(Score::getStudentId, student.getId())));
        map.put("selectionOpen", metaService.isSelectionOpen());
        return map;
    }
}
