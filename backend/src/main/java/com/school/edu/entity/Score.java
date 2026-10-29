package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 成绩表。 */
@Data
@TableName("score")
public class Score {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;
    private Long courseId;
    private Long selectionId;
    private BigDecimal usualScore;
    private BigDecimal examScore;
    private BigDecimal totalScore;
    /** 绩点：由总评成绩按五级制换算（见 ScoreService#gradePointOf），未出成绩时为 null */
    private BigDecimal gradePoint;
    private Long teacherId;
    private LocalDateTime submitTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
