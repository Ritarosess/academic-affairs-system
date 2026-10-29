package com.school.edu.vo;

import java.time.LocalDateTime;

/**
 * 用户视图对象。永远不包含密码字段。
 */
public record UserVO(
        Long id,
        String username,
        String realName,
        String role,
        String roleLabel,
        String phone,
        String email,
        Integer status,
        Integer mustChangePassword,
        LocalDateTime lastLoginTime,
        LocalDateTime createTime,
        /* 学生专属 */
        String studentNo,
        Long clazzId,
        String clazzName,
        String majorName,
        String gradeName,
        /* 教师专属 */
        String teacherNo,
        String title,
        String department,
        /* 公共 */
        String gender) {
}
