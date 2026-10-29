package com.school.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程表（即“教学班”）。selectedCount 为冗余计数，选课/退课时原子维护，用于防超卖。
 */
@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String courseNo;
    private String name;
    private BigDecimal credit;
    private Integer hours;
    /** 必修 / 选修 / 限选 */
    private String courseType;
    private Long teacherId;
    private Long semesterId;
    private Integer capacity;
    private Integer selectedCount;
    /** 1~7 表示周一至周日 */
    private Integer weekday;
    private Integer startSection;
    private Integer endSection;
    private String location;
    private String description;
    /** 1 开放选课，0 关闭 */
    private Integer status;
    /** 审核状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已拒绝 */
    private String auditStatus;
    private String reviewRemark;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
