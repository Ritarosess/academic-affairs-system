package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户表：三种角色共用的统一账号。 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String password;
    private String realName;
    /** ADMIN / TEACHER / STUDENT */
    private String role;
    private String phone;
    private String email;
    /** 1 启用，0 停用 */
    private Integer status;
    private Integer mustChangePassword;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
