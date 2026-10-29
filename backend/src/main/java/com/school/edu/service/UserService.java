package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.edu.common.BusinessException;
import com.school.edu.common.Labels;
import com.school.edu.common.PageResult;
import com.school.edu.dto.UserDto;
import com.school.edu.entity.Clazz;
import com.school.edu.entity.Course;
import com.school.edu.entity.CourseSelection;
import com.school.edu.entity.Student;
import com.school.edu.entity.SysUser;
import com.school.edu.entity.Teacher;
import com.school.edu.mapper.CourseMapper;
import com.school.edu.mapper.CourseSelectionMapper;
import com.school.edu.mapper.StudentMapper;
import com.school.edu.mapper.SysUserMapper;
import com.school.edu.mapper.TeacherMapper;
import com.school.edu.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 用户管理服务（管理员）：学生/教师/管理员三类用户的增删改查、启停与密码重置。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final CourseMapper courseMapper;
    private final CourseSelectionMapper courseSelectionMapper;
    private final PasswordEncoder passwordEncoder;
    private final LookupService lookupService;

    @Value("${app.default-password:123456}")
    private String defaultPassword;

    public PageResult<UserVO> page(String role, String keyword, long page, long size) {
        Page<SysUser> result = sysUserMapper.selectPage(new Page<>(page, size),
                Wrappers.<SysUser>lambdaQuery()
                        .eq(StringUtils.hasText(role), SysUser::getRole, role)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(SysUser::getUsername, keyword).or()
                                .like(SysUser::getRealName, keyword))
                        .orderByAsc(SysUser::getId));
        List<UserVO> records = assemble(result.getRecords());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    /** 批量组装 UserVO：一次性取出学生/教师档案与组织架构映射，避免 N+1 查询。 */
    private List<UserVO> assemble(List<SysUser> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        Map<Long, Student> students = lookupService.studentsByUserIds(userIds);
        Map<Long, Teacher> teachers = lookupService.teachersByUserIds(userIds);
        Map<Long, Clazz> clazzMap = lookupService.clazzMap();
        Map<Long, String> majorNames = lookupService.majorNameMap();
        Map<Long, String> gradeNames = lookupService.gradeNameMap();

        return users.stream().map(user -> {
            Student student = students.get(user.getId());
            Teacher teacher = teachers.get(user.getId());
            Clazz clazz = student == null || student.getClazzId() == null
                    ? null : clazzMap.get(student.getClazzId());
            String majorName = clazz == null ? null : majorNames.get(clazz.getMajorId());
            String gradeName = clazz == null ? null : gradeNames.get(clazz.getGradeId());
            String gender = student != null ? student.getGender()
                    : (teacher != null ? teacher.getGender() : null);
            return new UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getRole(),
                    Labels.role(user.getRole()), user.getPhone(), user.getEmail(), user.getStatus(),
                    user.getMustChangePassword(), user.getLastLoginTime(), user.getCreateTime(),
                    student == null ? null : student.getStudentNo(),
                    clazz == null ? null : clazz.getId(),
                    clazz == null ? null : clazz.getName(),
                    majorName, gradeName,
                    teacher == null ? null : teacher.getTeacherNo(),
                    teacher == null ? null : teacher.getTitle(),
                    teacher == null ? null : teacher.getDepartment(),
                    gender);
        }).toList();
    }

    @Transactional
    public UserVO create(UserDto.UserSaveRequest request) {
        assertUsernameFree(request.username(), null);
        assertEmailFree(request.email(), null);
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(defaultPassword));
        user.setRealName(request.realName());
        user.setRole(request.role());
        user.setPhone(request.phone());
        user.setEmail(request.email());
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setMustChangePassword(1);
        sysUserMapper.insert(user);

        if ("STUDENT".equals(request.role())) {
            createStudent(user.getId(), request);
        } else if ("TEACHER".equals(request.role())) {
            createTeacher(user.getId(), request);
        }
        return assemble(List.of(sysUserMapper.selectById(user.getId()))).get(0);
    }

    private void createStudent(Long userId, UserDto.UserSaveRequest request) {
        if (!StringUtils.hasText(request.studentNo())) {
            throw new BusinessException("学生学号不能为空");
        }
        if (studentMapper.selectCount(Wrappers.<Student>lambdaQuery()
                .eq(Student::getStudentNo, request.studentNo())) > 0) {
            throw new BusinessException("学号已存在：" + request.studentNo());
        }
        Student student = new Student();
        student.setUserId(userId);
        student.setStudentNo(request.studentNo());
        student.setName(request.realName());
        student.setGender(StringUtils.hasText(request.gender()) ? request.gender() : "男");
        student.setClazzId(request.clazzId());
        student.setPhone(request.phone());
        studentMapper.insert(student);
    }

    private void createTeacher(Long userId, UserDto.UserSaveRequest request) {
        if (!StringUtils.hasText(request.teacherNo())) {
            throw new BusinessException("教师工号不能为空");
        }
        if (teacherMapper.selectCount(Wrappers.<Teacher>lambdaQuery()
                .eq(Teacher::getTeacherNo, request.teacherNo())) > 0) {
            throw new BusinessException("工号已存在：" + request.teacherNo());
        }
        Teacher teacher = new Teacher();
        teacher.setUserId(userId);
        teacher.setTeacherNo(request.teacherNo());
        teacher.setName(request.realName());
        teacher.setGender(StringUtils.hasText(request.gender()) ? request.gender() : "男");
        teacher.setTitle(request.title());
        teacher.setDepartment(request.department());
        teacher.setPhone(request.phone());
        teacherMapper.insert(teacher);
    }

    @Transactional
    public UserVO update(Long id, UserDto.UserSaveRequest request) {
        SysUser existing = sysUserMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("用户不存在");
        }
        // 角色决定用户拥有的档案结构（学生档案 / 教师档案），变更角色会造成历史数据悬空，故不允许修改。
        if (!existing.getRole().equals(request.role())) {
            throw new BusinessException("不允许修改用户角色，请删除后重新创建");
        }
        assertUsernameFree(request.username(), id);
        assertEmailFree(request.email(), id);

        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setUsername(request.username());
        patch.setRealName(request.realName());
        patch.setPhone(request.phone());
        patch.setEmail(request.email());
        patch.setStatus(request.status());
        sysUserMapper.updateById(patch);

        if ("STUDENT".equals(existing.getRole())) {
            updateStudent(id, request);
        } else if ("TEACHER".equals(existing.getRole())) {
            updateTeacher(id, request);
        }
        return assemble(List.of(sysUserMapper.selectById(id))).get(0);
    }

    private void updateStudent(Long userId, UserDto.UserSaveRequest request) {
        Student student = studentMapper.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId, userId));
        if (student == null) {
            createStudent(userId, request);
            return;
        }
        if (StringUtils.hasText(request.studentNo())
                && studentMapper.selectCount(Wrappers.<Student>lambdaQuery()
                .eq(Student::getStudentNo, request.studentNo())
                .ne(Student::getId, student.getId())) > 0) {
            throw new BusinessException("学号已存在：" + request.studentNo());
        }
        Student patch = new Student();
        patch.setId(student.getId());
        patch.setStudentNo(request.studentNo());
        patch.setName(request.realName());
        patch.setGender(request.gender());
        patch.setClazzId(request.clazzId());
        patch.setPhone(request.phone());
        studentMapper.updateById(patch);
    }

    private void updateTeacher(Long userId, UserDto.UserSaveRequest request) {
        Teacher teacher = teacherMapper.selectOne(Wrappers.<Teacher>lambdaQuery().eq(Teacher::getUserId, userId));
        if (teacher == null) {
            createTeacher(userId, request);
            return;
        }
        if (StringUtils.hasText(request.teacherNo())
                && teacherMapper.selectCount(Wrappers.<Teacher>lambdaQuery()
                .eq(Teacher::getTeacherNo, request.teacherNo())
                .ne(Teacher::getId, teacher.getId())) > 0) {
            throw new BusinessException("工号已存在：" + request.teacherNo());
        }
        Teacher patch = new Teacher();
        patch.setId(teacher.getId());
        patch.setTeacherNo(request.teacherNo());
        patch.setName(request.realName());
        patch.setGender(request.gender());
        patch.setTitle(request.title());
        patch.setDepartment(request.department());
        patch.setPhone(request.phone());
        teacherMapper.updateById(patch);
    }

    @Transactional
    public void delete(Long id, Long operatorId) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (id.equals(operatorId)) {
            throw new BusinessException("不能删除当前登录的账号");
        }
        if ("ADMIN".equals(user.getRole()) && sysUserMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRole, "ADMIN")) <= 1) {
            throw new BusinessException("系统必须保留至少一个管理员账号");
        }
        if ("STUDENT".equals(user.getRole())) {
            Student student = studentMapper.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId, id));
            if (student != null) {
                if (courseSelectionMapper.selectCount(Wrappers.<CourseSelection>lambdaQuery()
                        .eq(CourseSelection::getStudentId, student.getId())) > 0) {
                    throw new BusinessException("该学生已有选课记录，无法删除，请改为停用账号");
                }
                studentMapper.deleteById(student.getId());
            }
        } else if ("TEACHER".equals(user.getRole())) {
            Teacher teacher = teacherMapper.selectOne(Wrappers.<Teacher>lambdaQuery().eq(Teacher::getUserId, id));
            if (teacher != null) {
                if (courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                        .eq(Course::getTeacherId, teacher.getId())) > 0) {
                    throw new BusinessException("该教师已开设课程，无法删除，请改为停用账号");
                }
                teacherMapper.deleteById(teacher.getId());
            }
        }
        sysUserMapper.deleteById(id);
    }

    @Transactional
    public void updateStatus(Long id, Integer status, Long operatorId) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值非法");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (id.equals(operatorId) && status == 0) {
            throw new BusinessException("不能停用当前登录的账号");
        }
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setStatus(status);
        sysUserMapper.updateById(patch);
    }

    @Transactional
    public Map<String, String> resetPassword(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        SysUser patch = new SysUser();
        patch.setId(id);
        patch.setPassword(passwordEncoder.encode(defaultPassword));
        patch.setMustChangePassword(1);
        sysUserMapper.updateById(patch);
        return Map.of("password", defaultPassword, "username", user.getUsername());
    }

    private void assertUsernameFree(String username, Long excludeId) {
        Long count = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("用户名已被占用：" + username);
        }
    }

    /**
     * 邮箱唯一性校验（排除自身）。邮箱是学业预警通知的收件地址，
     * 若允许重复，学生填写同学邮箱即可收到本应发给同学的成绩通知。
     */
    private void assertEmailFree(String email, Long excludeId) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        Long count = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmail, email.trim())
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("该邮箱已被其他账号使用：" + email);
        }
    }
}
