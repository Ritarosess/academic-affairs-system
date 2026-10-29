package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.edu.common.BusinessException;
import com.school.edu.common.Labels;
import com.school.edu.common.PageResult;
import com.school.edu.dto.CourseDto;
import com.school.edu.entity.Course;
import com.school.edu.entity.CourseSelection;
import com.school.edu.entity.Semester;
import com.school.edu.entity.Student;
import com.school.edu.entity.Teacher;
import com.school.edu.mapper.CourseMapper;
import com.school.edu.mapper.CourseSelectionMapper;
import com.school.edu.mapper.StudentMapper;
import com.school.edu.mapper.TeacherMapper;
import com.school.edu.vo.CourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程（教学班）服务。
 *
 * <p>职责分工：<b>教师申报课程</b>（提交后为待审核），<b>管理员审核</b>（通过 / 拒绝）；
 * 只有「已通过」的课程才会出现在学生选课大厅并允许选课。教师仅能修改 / 删除本人
 * 尚未通过审核的课程；已通过的课程不可再由教师改动。</p>
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    /** 审核状态 */
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    /** 待审核优先、其后按主键倒序的排序片段（常量，无注入风险） */
    private static final String ORDER_AUDIT =
            "ORDER BY CASE audit_status WHEN 'PENDING' THEN 0 WHEN 'REJECTED' THEN 1 ELSE 2 END, id DESC";

    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final CourseSelectionMapper courseSelectionMapper;
    private final LookupService lookupService;

    /* ---------------------------------------------------------------- 查询 */

    /** 管理员：课程审核列表，可按审核状态 / 学期 / 关键字过滤，待审核优先。 */
    public PageResult<CourseVO> pageForAdmin(Long semesterId, String auditStatus, String keyword,
                                             long page, long size) {
        Page<Course> result = courseMapper.selectPage(new Page<>(page, size),
                Wrappers.<Course>lambdaQuery()
                        .eq(semesterId != null, Course::getSemesterId, semesterId)
                        .eq(StringUtils.hasText(auditStatus), Course::getAuditStatus, auditStatus)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(Course::getCourseNo, keyword).or()
                                .like(Course::getName, keyword))
                        .last(ORDER_AUDIT));
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                toVOList(result.getRecords(), null));
    }

    /** 教师「我的课程」：仅展示已通过审核、实际任教的课程。 */
    public List<CourseVO> listByTeacherUser(Long teacherUserId) {
        Teacher teacher = requireTeacher(teacherUserId);
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getTeacherId, teacher.getId())
                .eq(Course::getAuditStatus, APPROVED)
                .orderByDesc(Course::getSemesterId)
                .orderByAsc(Course::getCourseNo));
        return toVOList(courses, null);
    }

    /** 教师「课程申报」：本人申报的全部课程（含待审核 / 已拒绝），待审核优先。 */
    public List<CourseVO> listAppliesByTeacherUser(Long teacherUserId) {
        Teacher teacher = requireTeacher(teacherUserId);
        List<Course> courses = courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .eq(Course::getTeacherId, teacher.getId())
                .last(ORDER_AUDIT));
        return toVOList(courses, null);
    }

    /** 选课大厅：当前学期且<b>已通过审核</b>的课程，并标记当前学生是否已选。 */
    public PageResult<CourseVO> pageForStudent(Long studentUserId, String keyword, String courseType,
                                               long page, long size) {
        Semester current = lookupService.currentSemester();
        if (current == null) {
            return PageResult.of(0, page, size, List.of());
        }
        Student student = requireStudent(studentUserId);
        Page<Course> result = courseMapper.selectPage(new Page<>(page, size),
                Wrappers.<Course>lambdaQuery()
                        .eq(Course::getSemesterId, current.getId())
                        .eq(Course::getAuditStatus, APPROVED)
                        .eq(StringUtils.hasText(courseType), Course::getCourseType, courseType)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(Course::getCourseNo, keyword).or()
                                .like(Course::getName, keyword))
                        .orderByAsc(Course::getCourseNo));
        Set<Long> selectedIds = courseSelectionMapper.selectList(Wrappers.<CourseSelection>lambdaQuery()
                        .eq(CourseSelection::getStudentId, student.getId())
                        .eq(CourseSelection::getStatus, "SELECTED"))
                .stream().map(CourseSelection::getCourseId).collect(Collectors.toSet());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(),
                toVOList(result.getRecords(), selectedIds));
    }

    public Course requireCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw BusinessException.notFound("课程不存在");
        }
        return course;
    }

    /** 校验课程归属：教师只能操作本人任教的课程（BR-06）。 */
    public Course requireOwnedByTeacher(Long courseId, Long teacherUserId) {
        Course course = requireCourse(courseId);
        Teacher teacher = requireTeacher(teacherUserId);
        if (course.getTeacherId() == null || !course.getTeacherId().equals(teacher.getId())) {
            throw BusinessException.forbidden("只能操作本人申报 / 任教的课程");
        }
        return course;
    }

    public Student requireStudent(Long studentUserId) {
        Student student = studentMapper.selectOne(
                Wrappers.<Student>lambdaQuery().eq(Student::getUserId, studentUserId));
        if (student == null) {
            throw BusinessException.notFound("未找到当前登录用户的学籍信息");
        }
        return student;
    }

    public Teacher requireTeacher(Long teacherUserId) {
        Teacher teacher = teacherMapper.selectOne(
                Wrappers.<Teacher>lambdaQuery().eq(Teacher::getUserId, teacherUserId));
        if (teacher == null) {
            throw BusinessException.notFound("未找到当前登录用户的教职信息");
        }
        return teacher;
    }

    /* ------------------------------------------------- 教师：申报 / 修改 / 删除 */

    /** 教师申报新课程，提交后进入待审核。 */
    @Transactional
    public CourseVO createForTeacher(Long teacherUserId, CourseDto.TeacherCourseRequest request) {
        Teacher teacher = requireTeacher(teacherUserId);
        assertSemesterExists(request.semesterId());
        assertCourseNoFree(request.courseNo(), request.semesterId(), null);

        Course course = new Course();
        applyTeacherRequest(course, request);
        course.setTeacherId(teacher.getId());
        course.setSelectedCount(0);
        course.setAuditStatus(PENDING);
        course.setReviewRemark(null);
        course.setReviewerId(null);
        course.setReviewTime(null);
        courseMapper.insert(course);
        return toVOList(List.of(courseMapper.selectById(course.getId())), null).get(0);
    }

    /**
     * 教师修改课程。仅本人且<b>尚未通过审核</b>（待审核 / 已拒绝）可改；
     * 修改后重新进入待审核，并清空上次审核意见。
     */
    @Transactional
    public CourseVO updateForTeacher(Long id, Long teacherUserId, CourseDto.TeacherCourseRequest request) {
        Course existing = requireOwnedByTeacher(id, teacherUserId);
        if (APPROVED.equals(existing.getAuditStatus())) {
            throw new BusinessException("该课程已通过审核，不能修改；如需调整请联系管理员");
        }
        assertSemesterExists(request.semesterId());
        assertCourseNoFree(request.courseNo(), request.semesterId(), id);
        if (request.capacity() < existing.getSelectedCount()) {
            throw new BusinessException("容量不得小于当前已选人数 " + existing.getSelectedCount());
        }

        Course course = new Course();
        course.setId(id);
        applyTeacherRequest(course, request);
        course.setTeacherId(existing.getTeacherId());
        course.setAuditStatus(PENDING);
        course.setReviewRemark(null);
        course.setReviewerId(null);
        course.setReviewTime(null);
        courseMapper.updateById(course);
        return toVOList(List.of(courseMapper.selectById(id)), null).get(0);
    }

    /** 教师删除课程：仅本人、尚未通过审核、且无选课记录时可删。 */
    @Transactional
    public void deleteForTeacher(Long id, Long teacherUserId) {
        Course existing = requireOwnedByTeacher(id, teacherUserId);
        if (APPROVED.equals(existing.getAuditStatus())) {
            throw new BusinessException("该课程已通过审核，不能删除；如需下线请联系管理员");
        }
        if (courseSelectionMapper.selectCount(Wrappers.<CourseSelection>lambdaQuery()
                .eq(CourseSelection::getCourseId, id)) > 0) {
            throw new BusinessException("该课程已有选课记录，无法删除");
        }
        courseMapper.deleteById(id);
    }

    /* -------------------------------------------------------- 管理员：审核 */

    /** 审核通过：待审核 → 已通过。 */
    @Transactional
    public void approve(Long id, Long reviewerId, String remark) {
        Course course = requirePending(id);
        Course patch = new Course();
        patch.setId(id);
        patch.setAuditStatus(APPROVED);
        patch.setReviewRemark(trimToNull(remark));
        patch.setReviewerId(reviewerId);
        patch.setReviewTime(LocalDateTime.now());
        courseMapper.updateById(patch);
    }

    /** 审核拒绝：待审核 → 已拒绝。 */
    @Transactional
    public void reject(Long id, Long reviewerId, String remark) {
        Course course = requirePending(id);
        Course patch = new Course();
        patch.setId(id);
        patch.setAuditStatus(REJECTED);
        patch.setReviewRemark(trimToNull(remark));
        patch.setReviewerId(reviewerId);
        patch.setReviewTime(LocalDateTime.now());
        courseMapper.updateById(patch);
    }

    private Course requirePending(Long id) {
        Course course = requireCourse(id);
        if (!PENDING.equals(course.getAuditStatus())) {
            throw new BusinessException("该课程已审核，不能重复操作");
        }
        return course;
    }

    /* ---------------------------------------------------------------- 组装 */

    private void applyTeacherRequest(Course course, CourseDto.TeacherCourseRequest request) {
        course.setCourseNo(request.courseNo().trim());
        course.setName(request.name().trim());
        course.setCredit(request.credit());
        course.setHours(request.hours());
        course.setCourseType(request.courseType());
        course.setSemesterId(request.semesterId());
        course.setCapacity(request.capacity());
        course.setWeekday(request.weekday());
        course.setStartSection(request.startSection());
        course.setEndSection(request.endSection());
        course.setLocation(trimToNull(request.location()));
        course.setDescription(trimToNull(request.description()));
        course.setStatus(request.status() == null ? 1 : request.status());
    }

    private void assertCourseNoFree(String courseNo, Long semesterId, Long excludeId) {
        Long count = courseMapper.selectCount(Wrappers.<Course>lambdaQuery()
                .eq(Course::getCourseNo, courseNo.trim())
                .eq(Course::getSemesterId, semesterId)
                .ne(excludeId != null, Course::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("该学期课程号已存在：" + courseNo);
        }
    }

    private void assertSemesterExists(Long semesterId) {
        if (semesterId == null || !lookupService.semesterNameMap().containsKey(semesterId)) {
            throw new BusinessException("所选开课学期不存在");
        }
    }

    /** 批量组装 CourseVO。selectedIds 为 null 时不计算 selected 标记。 */
    public List<CourseVO> toVOList(List<Course> courses, Set<Long> selectedIds) {
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }
        Map<Long, String> teacherNames = lookupService.teacherNameMap();
        Map<Long, String> semesterNames = lookupService.semesterNameMap();
        return courses.stream().map(c -> toVO(c, teacherNames, semesterNames, selectedIds)).toList();
    }

    private CourseVO toVO(Course c, Map<Long, String> teacherNames, Map<Long, String> semesterNames,
                          Set<Long> selectedIds) {
        int selected = c.getSelectedCount() == null ? 0 : c.getSelectedCount();
        int capacity = c.getCapacity() == null ? 0 : c.getCapacity();
        Boolean selectedFlag = selectedIds == null ? null : selectedIds.contains(c.getId());
        String audit = StringUtils.hasText(c.getAuditStatus()) ? c.getAuditStatus() : APPROVED;
        return new CourseVO(c.getId(), c.getCourseNo(), c.getName(), c.getCredit(), c.getHours(),
                c.getCourseType(), c.getTeacherId(), teacherNames.get(c.getTeacherId()),
                c.getSemesterId(), semesterNames.get(c.getSemesterId()),
                capacity, selected, Math.max(capacity - selected, 0),
                c.getWeekday(), Labels.weekday(c.getWeekday()), c.getStartSection(), c.getEndSection(),
                c.getLocation(), c.getDescription(), c.getStatus(), selectedFlag,
                audit, Labels.courseAudit(audit), c.getReviewRemark(), c.getReviewTime());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
