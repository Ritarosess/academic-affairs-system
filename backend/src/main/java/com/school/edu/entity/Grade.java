package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 年级表。 */
@Data
@TableName("grade")
public class Grade {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Integer enrollYear;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
