package com.school.edu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.edu.entity.Score;
import com.school.edu.vo.WarningMetrics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 成绩 Mapper。统计查询与学业预警指标聚合使用 XML 自定义 SQL
 * （见 resources/mapper/ScoreMapper.xml）。
 */
@Mapper
public interface ScoreMapper extends BaseMapper<Score> {

    /** 课程成绩统计：总数、平均分、最高分、最低分、及格人数。 */
    Map<String, Object> statisticsByCourse(@Param("courseId") Long courseId);

    /**
     * 一次性聚合全部学生的学业预警指标，避免按学生逐个查询造成 N+1。
     * 仅统计已出总评成绩的记录。
     */
    List<WarningMetrics> selectWarningMetrics();
}
