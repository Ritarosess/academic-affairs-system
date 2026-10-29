package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.school.edu.common.BusinessException;
import com.school.edu.common.Labels;
import com.school.edu.dto.AuthDto;
import com.school.edu.entity.Clazz;
import com.school.edu.entity.Major;
import com.school.edu.entity.SysUser;
import com.school.edu.entity.Teacher;
import com.school.edu.entity.Student;
import com.school.edu.entity.Grade;
import com.school.edu.mapper.SysUserMapper;
import com.school.edu.security.JwtUtil;
import com.school.edu.security.LoginUser;
import com.school.edu.vo.ProfileVO;
import com.school.edu.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务：登录、当前用户档案、修改密码。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final SysUserMapper sysUserMapper;
    private final LookupService lookupService;

    @Transactional
    public AuthDto.LoginResponse login(AuthDto.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        SysUser user = loginUser.getUser();

        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(patch);

        String token = jwtUtil.generate(user);
        return new AuthDto.LoginResponse(token, "Bearer", jwtUtil.getExpireSeconds(), toUserVO(user));
    }

    public ProfileVO profile(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        ProfileVO base = new ProfileVO(user.getId(), user.getUsername(), user.getRealName(), user.getRole(),
                Labels.role(user.getRole()), user.getPhone(), user.getEmail(), null,
                null, null, null, null, null, null,
                null, null, null, null);

        if ("STUDENT".equals(user.getRole())) {
            Student student = lookupService.studentsByUserIds(java.util.List.of(userId)).get(userId);
            if (student == null) {
                return base;
            }
            Clazz clazz = student.getClazzId() == null ? null : lookupService.clazzMap().get(student.getClazzId());
            String majorName = null;
            String gradeName = null;
            if (clazz != null) {
                Major major = lookupService.allMajors().stream()
                        .filter(m -> m.getId().equals(clazz.getMajorId())).findFirst().orElse(null);
                Grade grade = lookupService.allGrades().stream()
                        .filter(g -> g.getId().equals(clazz.getGradeId())).findFirst().orElse(null);
                majorName = major == null ? null : major.getName();
                gradeName = grade == null ? null : grade.getName();
            }
            return new ProfileVO(user.getId(), user.getUsername(), user.getRealName(), user.getRole(),
                    Labels.role(user.getRole()), user.getPhone(), user.getEmail(), student.getGender(),
                    student.getId(), student.getStudentNo(), student.getClazzId(),
                    clazz == null ? null : clazz.getName(), majorName, gradeName,
                    null, null, null, null);
        }

        if ("TEACHER".equals(user.getRole())) {
            Teacher teacher = lookupService.teachersByUserIds(java.util.List.of(userId)).get(userId);
            if (teacher == null) {
                return base;
            }
            return new ProfileVO(user.getId(), user.getUsername(), user.getRealName(), user.getRole(),
                    Labels.role(user.getRole()), user.getPhone(), user.getEmail(), teacher.getGender(),
                    null, null, null, null, null, null,
                    teacher.getId(), teacher.getTeacherNo(), teacher.getTitle(), teacher.getDepartment());
        }
        return base;
    }

    @Transactional
    public void changePassword(Long userId, AuthDto.ChangePasswordRequest request) {        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setPassword(passwordEncoder.encode(request.newPassword()));
        patch.setMustChangePassword(0);
        sysUserMapper.updateById(patch);
    }

    /**
     * 修改本人邮箱。邮箱是学业预警通知的收件地址，因此必须保证唯一：
     * 若允许重复，学生只要填写同学的邮箱，就能收到本应发给同学的预警邮件（其中含成绩）。
     * 修改成功后返回最新的档案，便于前端直接刷新。
     */
    @Transactional
    public ProfileVO updateEmail(Long userId, AuthDto.UpdateEmailRequest request) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        String email = request.email().trim();
        if (email.equalsIgnoreCase(user.getEmail() == null ? "" : user.getEmail().trim())) {
            return profile(userId);
        }
        assertEmailFree(email, userId);

        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setEmail(email);
        sysUserMapper.updateById(patch);
        return profile(userId);
    }

    /** 邮箱唯一性校验（排除自身）。 */
    private void assertEmailFree(String email, Long excludeUserId) {
        Long count = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmail, email)
                .ne(excludeUserId != null, SysUser::getId, excludeUserId));
        if (count > 0) {
            throw new BusinessException("该邮箱已被其他账号使用，请更换");
        }
    }

    /** 将实体转换为 UserVO（不查学籍/教职档案，供登录响应使用）。 */    public UserVO toUserVO(SysUser user) {
        return new UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getRole(),
                Labels.role(user.getRole()), user.getPhone(), user.getEmail(), user.getStatus(),
                user.getMustChangePassword(), user.getLastLoginTime(), user.getCreateTime(),
                null, null, null, null, null, null, null, null, null);
    }
}
