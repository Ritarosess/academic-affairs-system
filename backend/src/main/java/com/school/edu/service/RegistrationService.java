package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.edu.common.BusinessException;
import com.school.edu.common.Labels;
import com.school.edu.common.PageResult;
import com.school.edu.dto.RegistrationDto;
import com.school.edu.entity.Clazz;
import com.school.edu.entity.RegistrationApply;
import com.school.edu.entity.Student;
import com.school.edu.entity.SysUser;
import com.school.edu.entity.Teacher;
import com.school.edu.mapper.RegistrationApplyMapper;
import com.school.edu.mapper.StudentMapper;
import com.school.edu.mapper.SysUserMapper;
import com.school.edu.mapper.TeacherMapper;
import com.school.edu.vo.RegistrationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 注册申请服务。自助注册先写入 registration_apply（PENDING），
 * 由管理员审核；通过后在同一事务内生成 sys_user 与 student/teacher 正式账号。
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private final RegistrationApplyMapper applyMapper;
    private final SysUserMapper sysUserMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final PasswordEncoder passwordEncoder;
    private final LookupService lookupService;

    /** 提交注册申请：用户名/邮箱/学号/工号需同时与正式账号、待审核申请保持唯一。 */
    @Transactional
    public void register(RegistrationDto.RegisterRequest request) {
        String role = request.role();
        String username = request.username().trim();
        String email = trimToNull(request.email());

        assertUsernameFree(username);
        // 邮箱必填，必须全局唯一
        assertEmailFree(email);
        if ("STUDENT".equals(role)) {
            if (!StringUtils.hasText(request.studentNo())) {
                throw new BusinessException("学号不能为空");
            }
            assertStudentNoFree(request.studentNo().trim());
        } else {
            if (!StringUtils.hasText(request.teacherNo())) {
                throw new BusinessException("工号不能为空");
            }
            assertTeacherNoFree(request.teacherNo().trim());
        }

        RegistrationApply apply = new RegistrationApply();
        apply.setUsername(username);
        apply.setPassword(passwordEncoder.encode(request.password()));
        apply.setRealName(request.realName().trim());
        apply.setRole(role);
        apply.setPhone(trimToNull(request.phone()));
        apply.setEmail(email);
        apply.setGender(StringUtils.hasText(request.gender()) ? request.gender() : "男");
        if ("STUDENT".equals(role)) {
            apply.setStudentNo(request.studentNo().trim());
            apply.setClazzId(request.clazzId());
        } else {
            apply.setTeacherNo(request.teacherNo().trim());
            apply.setTitle(trimToNull(request.title()));
            apply.setDepartment(trimToNull(request.department()));
        }
        apply.setStatus(PENDING);
        applyMapper.insert(apply);
    }

    /** 管理员分页查询注册申请。 */
    public PageResult<RegistrationVO> page(String status, long page, long size) {
        Page<RegistrationApply> result = applyMapper.selectPage(new Page<>(page, size),
                Wrappers.<RegistrationApply>lambdaQuery()
                        .eq(StringUtils.hasText(status), RegistrationApply::getStatus, status)
                        // 待审核置顶，其余按申请时间倒序
                        .orderByAsc(RegistrationApply::getStatus)
                        .orderByDesc(RegistrationApply::getCreateTime)
                        .orderByDesc(RegistrationApply::getId));
        return PageResult.of(result, this::toVO);
    }

    /** 审核通过：创建正式账号与对应档案，全部成功才提交。 */
    @Transactional
    public void approve(Long id, Long reviewerId, RegistrationDto.ReviewRequest request) {
        RegistrationApply apply = requirePending(id);
        // 终审唯一性：防止申请提交后同一用户名/学号/工号被其他途径占用
        if (sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, apply.getUsername())) > 0) {
            throw new BusinessException("用户名「" + apply.getUsername() + "」已被占用，无法通过");
        }

        SysUser user = new SysUser();
        user.setUsername(apply.getUsername());
        user.setPassword(apply.getPassword());
        user.setRealName(apply.getRealName());
        user.setRole(apply.getRole());
        user.setPhone(apply.getPhone());
        user.setEmail(apply.getEmail());
        user.setStatus(1);
        user.setMustChangePassword(0);
        sysUserMapper.insert(user);

        if ("STUDENT".equals(apply.getRole())) {
            if (studentMapper.selectCount(Wrappers.<Student>lambdaQuery()
                    .eq(Student::getStudentNo, apply.getStudentNo())) > 0) {
                throw new BusinessException("学号「" + apply.getStudentNo() + "」已被占用，无法通过");
            }
            Student student = new Student();
            student.setUserId(user.getId());
            student.setStudentNo(apply.getStudentNo());
            student.setName(apply.getRealName());
            student.setGender(apply.getGender());
            student.setClazzId(apply.getClazzId());
            student.setPhone(apply.getPhone());
            studentMapper.insert(student);
        } else {
            if (teacherMapper.selectCount(Wrappers.<Teacher>lambdaQuery()
                    .eq(Teacher::getTeacherNo, apply.getTeacherNo())) > 0) {
                throw new BusinessException("工号「" + apply.getTeacherNo() + "」已被占用，无法通过");
            }
            Teacher teacher = new Teacher();
            teacher.setUserId(user.getId());
            teacher.setTeacherNo(apply.getTeacherNo());
            teacher.setName(apply.getRealName());
            teacher.setGender(apply.getGender());
            teacher.setTitle(apply.getTitle());
            teacher.setDepartment(apply.getDepartment());
            teacher.setPhone(apply.getPhone());
            teacherMapper.insert(teacher);
        }
        updateStatus(apply.getId(), APPROVED, reviewerId, request.remark());
    }

    /** 审核拒绝：仅更新状态与审核意见。 */
    @Transactional
    public void reject(Long id, Long reviewerId, RegistrationDto.ReviewRequest request) {
        requirePending(id);
        updateStatus(id, REJECTED, reviewerId, request.remark());
    }

    private RegistrationApply requirePending(Long id) {
        RegistrationApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw BusinessException.notFound("注册申请不存在");
        }
        if (!PENDING.equals(apply.getStatus())) {
            throw new BusinessException("该申请已处理，不能重复审核");
        }
        return apply;
    }

    private void updateStatus(Long id, String status, Long reviewerId, String remark) {
        RegistrationApply patch = new RegistrationApply();
        patch.setId(id);
        patch.setStatus(status);
        patch.setReviewerId(reviewerId);
        patch.setReviewRemark(trimToNull(remark));
        patch.setReviewTime(LocalDateTime.now());
        applyMapper.updateById(patch);
    }

    private RegistrationVO toVO(RegistrationApply apply) {
        Clazz clazz = apply.getClazzId() == null ? null : lookupService.clazzMap().get(apply.getClazzId());
        String majorName = clazz == null ? null : lookupService.majorNameMap().get(clazz.getMajorId());
        String gradeName = clazz == null ? null : lookupService.gradeNameMap().get(clazz.getGradeId());
        return new RegistrationVO(
                apply.getId(), apply.getUsername(), apply.getRealName(), apply.getRole(),
                Labels.role(apply.getRole()), apply.getPhone(), apply.getEmail(), apply.getGender(),
                apply.getStudentNo(), clazz == null ? null : clazz.getId(),
                clazz == null ? null : clazz.getName(), majorName, gradeName,
                apply.getTeacherNo(), apply.getTitle(), apply.getDepartment(),
                apply.getStatus(), statusLabel(apply.getStatus()), apply.getReviewRemark(),
                apply.getReviewerId(), apply.getReviewTime(), apply.getCreateTime());
    }

    private void assertUsernameFree(String username) {
        if (sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)) > 0) {
            throw new BusinessException("用户名已被占用：" + username);
        }
        if (applyMapper.selectCount(Wrappers.<RegistrationApply>lambdaQuery()
                .eq(RegistrationApply::getUsername, username)
                .eq(RegistrationApply::getStatus, PENDING)) > 0) {
            throw new BusinessException("该用户名已有待审核的注册申请");
        }
    }

    private void assertEmailFree(String email) {
        if (sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmail, email)) > 0) {
            throw new BusinessException("该邮箱已被其他账号使用：" + email);
        }
        if (applyMapper.selectCount(Wrappers.<RegistrationApply>lambdaQuery()
                .eq(RegistrationApply::getEmail, email)
                .eq(RegistrationApply::getStatus, PENDING)) > 0) {
            throw new BusinessException("该邮箱已有待审核的注册申请");
        }
    }

    private void assertStudentNoFree(String studentNo) {
        if (studentMapper.selectCount(Wrappers.<Student>lambdaQuery()
                .eq(Student::getStudentNo, studentNo)) > 0) {
            throw new BusinessException("学号已存在：" + studentNo);
        }
        if (applyMapper.selectCount(Wrappers.<RegistrationApply>lambdaQuery()
                .eq(RegistrationApply::getStudentNo, studentNo)
                .eq(RegistrationApply::getStatus, PENDING)) > 0) {
            throw new BusinessException("该学号已有待审核的注册申请");
        }
    }

    private void assertTeacherNoFree(String teacherNo) {
        if (teacherMapper.selectCount(Wrappers.<Teacher>lambdaQuery()
                .eq(Teacher::getTeacherNo, teacherNo)) > 0) {
            throw new BusinessException("工号已存在：" + teacherNo);
        }
        if (applyMapper.selectCount(Wrappers.<RegistrationApply>lambdaQuery()
                .eq(RegistrationApply::getTeacherNo, teacherNo)
                .eq(RegistrationApply::getStatus, PENDING)) > 0) {
            throw new BusinessException("该工号已有待审核的注册申请");
        }
    }

    private String statusLabel(String status) {
        return switch (status) {
            case PENDING -> "待审核";
            case APPROVED -> "已通过";
            case REJECTED -> "已拒绝";
            default -> status;
        };
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
