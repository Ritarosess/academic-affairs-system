package com.school.edu.vo;

import java.time.LocalDateTime;

/** 注册申请视图对象，附带角色/状态中文名与班级、专业、年级名称。 */
public record RegistrationVO(
        Long id,
        String username,
        String realName,
        String role,
        String roleLabel,
        String phone,
        String email,
        String gender,
        String studentNo,
        Long clazzId,
        String clazzName,
        String majorName,
        String gradeName,
        String teacherNo,
        String title,
        String department,
        String status,
        String statusLabel,
        String reviewRemark,
        Long reviewerId,
        LocalDateTime reviewTime,
        LocalDateTime createTime) {
}
