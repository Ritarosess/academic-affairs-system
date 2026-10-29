package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 教师表。 */
@Data
@TableName("teacher")
public class Teacher {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String teacherNo;
    private String name;
    private String gender;
    private String title;
    private String department;
    private String phone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
