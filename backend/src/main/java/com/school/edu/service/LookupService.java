package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.school.edu.entity.Clazz;
import com.school.edu.entity.Grade;
import com.school.edu.entity.Major;
import com.school.edu.entity.Semester;
import com.school.edu.entity.Student;
import com.school.edu.entity.Teacher;
import com.school.edu.mapper.ClazzMapper;
import com.school.edu.mapper.GradeMapper;
import com.school.edu.mapper.MajorMapper;
import com.school.edu.mapper.SemesterMapper;
import com.school.edu.mapper.StudentMapper;
import com.school.edu.mapper.TeacherMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 公共查询与拼装辅助。基础数据表（年级/专业/班级/教师/学期）规模很小，
 * 直接全量载入内存做映射，避免为列表查询编写大量关联 SQL。
 */
@Service
@RequiredArgsConstructor
public class LookupService {

    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final ClazzMapper clazzMapper;
    private final MajorMapper majorMapper;
    private final GradeMapper gradeMapper;
    private final SemesterMapper semesterMapper;

    public List<Teacher> allTeachers() {
        return teacherMapper.selectList(Wrappers.<Teacher>lambdaQuery().orderByAsc(Teacher::getTeacherNo));
    }

    public List<Semester> allSemesters() {
        return semesterMapper.selectList(Wrappers.<Semester>lambdaQuery().orderByDesc(Semester::getId));
    }

    public List<Clazz> allClazzes() {
        return clazzMapper.selectList(Wrappers.<Clazz>lambdaQuery().orderByAsc(Clazz::getName));
    }

    public List<Major> allMajors() {
        return majorMapper.selectList(Wrappers.<Major>lambdaQuery().orderByAsc(Major::getId));
    }

    public List<Grade> allGrades() {
        return gradeMapper.selectList(Wrappers.<Grade>lambdaQuery().orderByDesc(Grade::getEnrollYear));
    }

    public Map<Long, Teacher> teacherMap() {
        return allTeachers().stream().collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));
    }

    public Map<Long, String> teacherNameMap() {
        return allTeachers().stream()
                .collect(Collectors.toMap(Teacher::getId, Teacher::getName, (a, b) -> a));
    }

    public Map<Long, String> semesterNameMap() {
        return allSemesters().stream()
                .collect(Collectors.toMap(Semester::getId, Semester::getName, (a, b) -> a));
    }

    public Map<Long, Clazz> clazzMap() {
        return allClazzes().stream().collect(Collectors.toMap(Clazz::getId, Function.identity(), (a, b) -> a));
    }

    public Map<Long, String> majorNameMap() {
        return allMajors().stream().collect(Collectors.toMap(Major::getId, Major::getName, (a, b) -> a));
    }

    public Map<Long, String> gradeNameMap() {
        return allGrades().stream().collect(Collectors.toMap(Grade::getId, Grade::getName, (a, b) -> a));
    }

    /** 当前学期（取 is_current = 1 的第一条）。 */
    public Semester currentSemester() {
        return semesterMapper.selectOne(
                Wrappers.<Semester>lambdaQuery().eq(Semester::getIsCurrent, 1).last("LIMIT 1"));
    }

    /** 按用户 ID 批量取学生档案。 */
    public Map<Long, Student> studentsByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectList(
                        Wrappers.<Student>lambdaQuery().in(Student::getUserId, userIds))
                .stream().collect(Collectors.toMap(Student::getUserId, Function.identity(), (a, b) -> a));
    }

    /** 按用户 ID 批量取教师档案。 */
    public Map<Long, Teacher> teachersByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return teacherMapper.selectList(
                        Wrappers.<Teacher>lambdaQuery().in(Teacher::getUserId, userIds))
                .stream().collect(Collectors.toMap(Teacher::getUserId, Function.identity(), (a, b) -> a));
    }
}
