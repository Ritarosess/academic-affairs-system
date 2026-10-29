package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 学期表：含教务处设定的选课周期。 */
@Data
@TableName("semester")
public class Semester {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime selectStartTime;
    private LocalDateTime selectEndTime;
    /** 退课截止时间；为空时退课窗口与选课窗口一致 */
    private LocalDateTime dropEndTime;
    /** 1 当前学期，0 非当前 */
    private Integer isCurrent;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
