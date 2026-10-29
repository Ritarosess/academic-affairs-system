package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 注册申请表：自助注册申请先落本表，管理员审核通过后才生成正式账号。 */
@Data
@TableName("registration_apply")
public class RegistrationApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    /** BCrypt 散列后的密码，审核通过后直接迁移到 sys_user */
    private String password;
    private String realName;
    /** 申请角色：STUDENT / TEACHER */
    private String role;
    private String phone;
    private String email;
    private String gender;
    private String studentNo;
    private Long clazzId;
    private String teacherNo;
    private String title;
    private String department;
    /** PENDING 待审核 / APPROVED 已通过 / REJECTED 已拒绝 */
    private String status;
    private String reviewRemark;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
