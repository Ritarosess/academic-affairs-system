package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 学生表。 */
@Data
@TableName("student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String studentNo;
    private String name;
    private String gender;
    private Long clazzId;
    private String phone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
