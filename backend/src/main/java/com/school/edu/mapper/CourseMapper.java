package com.school.edu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.edu.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 课程 Mapper。座位占用/释放使用「条件原子更新」，以受影响行数判定成败，
 * 这是本系统防超卖的核心手段（见《软件设计说明书》选课模块设计）。
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /**
     * 尝试占用一个座位：仅当已选人数小于容量时才会更新成功。
     *
     * @return 1 表示占座成功，0 表示名额已满
     */
    @Update("UPDATE course SET selected_count = selected_count + 1 " +
            "WHERE id = #{courseId} AND selected_count < capacity")
    int tryOccupySeat(@Param("courseId") Long courseId);

    /**
     * 释放一个座位（退课时调用）。
     *
     * @return 1 表示释放成功
     */
    @Update("UPDATE course SET selected_count = selected_count - 1 " +
            "WHERE id = #{courseId} AND selected_count > 0")
    int releaseSeat(@Param("courseId") Long courseId);

    /** 统计某学生当前已选课程的学分合计。 */
    @Select("SELECT IFNULL(SUM(c.credit), 0) FROM course c " +
            "JOIN course_selection cs ON cs.course_id = c.id " +
            "WHERE cs.student_id = #{studentId} AND cs.status = 'SELECTED'")
    BigDecimal sumSelectedCredit(@Param("studentId") Long studentId);
}
