package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.school.edu.common.BusinessException;
import com.school.edu.common.Labels;
import com.school.edu.entity.Course;
import com.school.edu.entity.CourseSelection;
import com.school.edu.entity.Score;
import com.school.edu.entity.Semester;
import com.school.edu.entity.Student;
import com.school.edu.mapper.CourseMapper;
import com.school.edu.mapper.CourseSelectionMapper;
import com.school.edu.mapper.ScoreMapper;
import com.school.edu.vo.SelectionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 选课服务——本系统业务核心。
 *
 * <p>防超卖设计（BR-03）：容量扣减采用「条件原子更新」
 * {@code UPDATE course SET selected_count = selected_count + 1 WHERE id = ? AND selected_count < capacity}，
 * 以数据库受影响行数判定是否抢到座位；配合 course_selection 上 (student_id, course_id) 唯一索引
 * 兜底重复选课。任何一步失败都会让整个事务回滚，容量自动还原。</p>
 */
@Service
@RequiredArgsConstructor
public class SelectionService {

    private final CourseMapper courseMapper;
    private final CourseSelectionMapper selectionMapper;
    private final ScoreMapper scoreMapper;
    private final CourseService courseService;
    private final MetaService metaService;
    private final LookupService lookupService;

    @Transactional
    public void select(Long studentUserId, Long courseId) {
        Student student = courseService.requireStudent(studentUserId);

        // 2. 校验是否处于选课周期（对应用例 UC-03 主事件流第 2 步）
        if (!metaService.isSelectionOpen()) {
            throw new BusinessException("当前非选课时段");
        }
        Course course = courseService.requireCourse(courseId);
        if (!CourseService.APPROVED.equals(course.getAuditStatus())) {
            throw new BusinessException("该课程尚未通过审核，暂不能选课");
        }
        if (course.getStatus() == null || course.getStatus() != 1) {
            throw new BusinessException("该课程已关闭选课");
        }
        Semester current = lookupService.currentSemester();
        if (current == null || !current.getId().equals(course.getSemesterId())) {
            throw new BusinessException("该课程不在当前学期，无法选课");
        }

        // 4. 校验是否已选过该课程（避免无谓占用座位）
        CourseSelection existing = findSelection(student.getId(), courseId);
        if (existing != null && "SELECTED".equals(existing.getStatus())) {
            throw new BusinessException("请勿重复选课");
        }

        // 3. 原子占座，失败即名额已满
        if (courseMapper.tryOccupySeat(courseId) == 0) {
            throw new BusinessException("该课程名额已满");
        }

        // 5. 落表：首次选课插入，退课后重选则复用原记录
        if (existing == null) {
            CourseSelection selection = new CourseSelection();
            selection.setStudentId(student.getId());
            selection.setCourseId(courseId);
            selection.setStatus("SELECTED");
            selection.setSelectTime(LocalDateTime.now());
            try {
                selectionMapper.insert(selection);
            } catch (DuplicateKeyException e) {
                // 并发下另一个请求已建立记录，事务回滚会一并还原座位
                throw new BusinessException("请勿重复选课");
            }
        } else if (selectionMapper.reviveSelection(student.getId(), courseId, LocalDateTime.now()) == 0) {
            throw new BusinessException("请勿重复选课");
        }
    }

    @Transactional
    public void drop(Long studentUserId, Long courseId) {
        Student student = courseService.requireStudent(studentUserId);
        Course course = courseService.requireCourse(courseId);

        // 退课窗口校验（可与选课窗口分别配置截止时间）
        if (!metaService.isDropOpen()) {
            throw new BusinessException("当前不在退课时段，退课通道未开放或已截止");
        }

        // 必修课不可退课
        if ("必修".equals(course.getCourseType())) {
            throw new BusinessException("「" + course.getName() + "」为必修课，不可退课");
        }

        // BR-04：已录入成绩的课程不可退课
        if (scoreMapper.selectCount(Wrappers.<Score>lambdaQuery()
                .eq(Score::getStudentId, student.getId())
                .eq(Score::getCourseId, courseId)) > 0) {
            throw new BusinessException("该课程已录入成绩，不可退课");
        }

        if (selectionMapper.dropSelection(student.getId(), courseId, LocalDateTime.now()) == 0) {
            throw new BusinessException("退课失败，未找到有效选课记录");
        }
        courseMapper.releaseSeat(courseId);
    }

    /** 我的课表：当前学期处于「已选」状态的课程。 */
    public List<SelectionVO> mySelections(Long studentUserId) {
        Student student = courseService.requireStudent(studentUserId);
        List<CourseSelection> selections = selectionMapper.selectList(Wrappers.<CourseSelection>lambdaQuery()
                .eq(CourseSelection::getStudentId, student.getId())
                .eq(CourseSelection::getStatus, "SELECTED")
                .orderByDesc(CourseSelection::getSelectTime));
        return toVOList(selections);
    }

    /** 我的全部选课记录（含已退课），用于成绩页等场景。 */
    public List<SelectionVO> mySelectionHistory(Long studentUserId) {
        Student student = courseService.requireStudent(studentUserId);
        return toVOList(selectionMapper.selectList(Wrappers.<CourseSelection>lambdaQuery()
                .eq(CourseSelection::getStudentId, student.getId())
                .orderByDesc(CourseSelection::getSelectTime)));
    }

    private CourseSelection findSelection(Long studentId, Long courseId) {
        return selectionMapper.selectOne(Wrappers.<CourseSelection>lambdaQuery()
                .eq(CourseSelection::getStudentId, studentId)
                .eq(CourseSelection::getCourseId, courseId));
    }

    private List<SelectionVO> toVOList(List<CourseSelection> selections) {
        if (selections.isEmpty()) {
            return List.of();
        }
        Set<Long> courseIds = selections.stream().map(CourseSelection::getCourseId).collect(Collectors.toSet());
        Map<Long, Course> courseMap = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> teacherNames = lookupService.teacherNameMap();
        Map<Long, String> semesterNames = lookupService.semesterNameMap();
        Semester current = lookupService.currentSemester();

        Set<Long> scoredCourseIds = selections.isEmpty() ? Set.of()
                : scoreMapper.selectList(Wrappers.<Score>lambdaQuery()
                        .eq(Score::getStudentId, selections.get(0).getStudentId()))
                .stream().map(Score::getCourseId).collect(Collectors.toSet());

        return selections.stream()
                .map(selection -> {
                    Course course = courseMap.get(selection.getCourseId());
                    if (course == null) {
                        return null;
                    }
                    // 课表只展示当前学期的课程
                    if (current != null && !current.getId().equals(course.getSemesterId())) {
                        return null;
                    }
                    return new SelectionVO(selection.getId(), course.getId(), course.getCourseNo(),
                            course.getName(), course.getCredit(), course.getCourseType(),
                            teacherNames.get(course.getTeacherId()), semesterNames.get(course.getSemesterId()),
                            course.getWeekday(), Labels.weekday(course.getWeekday()),
                            course.getStartSection(), course.getEndSection(), course.getLocation(),
                            selection.getStatus(), Labels.selectionStatus(selection.getStatus()),
                            scoredCourseIds.contains(course.getId()),
                            selection.getSelectTime(), selection.getDropTime());
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
