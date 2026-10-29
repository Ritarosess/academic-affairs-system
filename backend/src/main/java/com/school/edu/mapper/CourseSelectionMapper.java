package com.school.edu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.edu.entity.CourseSelection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CourseSelectionMapper extends BaseMapper<CourseSelection> {

    /**
     * 将一条已退课的记录重新置为已选（重选场景），仅当当前状态为 DROPPED 时才生效。
     *
     * @return 1 表示重选成功，0 表示不可重选（已处于已选状态）
     */
    @Update("UPDATE course_selection SET status = 'SELECTED', select_time = #{selectTime}, drop_time = NULL " +
            "WHERE student_id = #{studentId} AND course_id = #{courseId} AND status = 'DROPPED'")
    int reviveSelection(@Param("studentId") Long studentId,
                        @Param("courseId") Long courseId,
                        @Param("selectTime") LocalDateTime selectTime);

    /**
     * 退课：仅当记录处于已选状态时置为已退。
     *
     * @return 1 表示退课成功，0 表示无有效选课记录
     */
    @Update("UPDATE course_selection SET status = 'DROPPED', drop_time = #{dropTime} " +
            "WHERE student_id = #{studentId} AND course_id = #{courseId} AND status = 'SELECTED'")
    int dropSelection(@Param("studentId") Long studentId,
                      @Param("courseId") Long courseId,
                      @Param("dropTime") LocalDateTime dropTime);
}
