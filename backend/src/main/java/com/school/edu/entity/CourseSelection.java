package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 选课记录表：退课不删行，改为置 DROPPED，重选时复用（唯一键防重复选课）。 */
@Data
@TableName("course_selection")
public class CourseSelection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;
    private Long courseId;
    /** SELECTED 已选 / DROPPED 已退 */
    private String status;
    private LocalDateTime selectTime;
    private LocalDateTime dropTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
