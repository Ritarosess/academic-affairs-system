package com.school.edu.vo;

/** 当前登录用户的完整档案（含学籍/教职信息），用于个人中心展示。 */
public record ProfileVO(
        Long userId,
        String username,
        String realName,
        String role,
        String roleLabel,
        String phone,
        String email,
        String gender,
        /* 学生 */
        Long studentId,
        String studentNo,
        Long clazzId,
        String clazzName,
        String majorName,
        String gradeName,
        /* 教师 */
        Long teacherId,
        String teacherNo,
        String title,
        String department) {
}
