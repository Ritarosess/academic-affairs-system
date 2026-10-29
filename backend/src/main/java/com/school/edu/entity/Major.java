package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 专业表。 */
@Data
@TableName("major")
public class Major {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String department;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
