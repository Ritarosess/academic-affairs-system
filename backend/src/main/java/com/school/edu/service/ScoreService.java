package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.school.edu.common.BusinessException;
import com.school.edu.common.WarningLevel;
import com.school.edu.dto.ScoreDto;
import com.school.edu.entity.Clazz;
import com.school.edu.entity.Course;
import com.school.edu.entity.CourseSelection;
import com.school.edu.entity.Score;
import com.school.edu.entity.Student;
import com.school.edu.entity.Teacher;
import com.school.edu.mapper.CourseMapper;
import com.school.edu.mapper.CourseSelectionMapper;
import com.school.edu.mapper.ScoreMapper;
import com.school.edu.mapper.StudentMapper;
import com.school.edu.vo.RosterVO;
import com.school.edu.vo.ScoreVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成绩服务：教师查看选课名单、批量录入/修改成绩、查看统计；学生查询本人成绩。
 */
@Service
@RequiredArgsConstructor
public class ScoreService {

    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final CourseMapper courseMapper;
    private final CourseSelectionMapper selectionMapper;
    private final CourseService courseService;
    private final LookupService lookupService;
    private final WarningService warningService;

    /** 平时成绩权重，可通过 app.score.usual-weight 配置（风险 R-06 对策）。 */
    @Value("${app.score.usual-weight:0.4}")
    private String usualWeight;

    /** 选课学生名单（含已有成绩），用于教师端成绩录入表格。 */
    public List<RosterVO> roster(Long courseId, Long teacherUserId) {
        courseService.requireOwnedByTeacher(courseId, teacherUserId);

        List<CourseSelection> selections = selectionMapper.selectList(Wrappers.<CourseSelection>lambdaQuery()
                .eq(CourseSelection::getCourseId, courseId)
                .eq(CourseSelection::getStatus, "SELECTED"));
        if (selections.isEmpty()) {
            return List.of();
        }
        Set<Long> studentIds = selections.stream().map(CourseSelection::getStudentId).collect(Collectors.toSet());
        Map<Long, Student> students = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        Map<Long, Score> scores = scoreMapper.selectList(Wrappers.<Score>lambdaQuery()
                        .eq(Score::getCourseId, courseId)).stream()
                .collect(Collectors.toMap(Score::getStudentId, Function.identity(), (a, b) -> a));
        // 一次性取出班级映射，避免在流中反复查询（N+1 问题）
        Map<Long, Clazz> clazzMap = lookupService.clazzMap();
        Map<Long, String> clazzNames = clazzMap.values().stream()
                .collect(Collectors.toMap(Clazz::getId, Clazz::getName, (a, b) -> a));
        // 教师关联查看：在名单中标注每名学生的学业预警等级
        Map<Long, String> warningLevels = warningService.levelNames(studentIds);

        return selections.stream()
                .map(selection -> {
                    Student student = students.get(selection.getStudentId());
                    if (student == null) {
                        return null;
                    }
                    Score score = scores.get(student.getId());
                    Clazz clazz = student.getClazzId() == null ? null : clazzMap.get(student.getClazzId());
                    String warningLevel = warningLevels.getOrDefault(student.getId(), WarningLevel.NORMAL.name());
                    return new RosterVO(selection.getId(), student.getId(), student.getStudentNo(),
                            student.getName(), student.getGender(),
                            clazz == null ? null : clazzNames.get(clazz.getId()),
                            score == null ? null : score.getId(),
                            score == null ? null : score.getUsualScore(),
                            score == null ? null : score.getExamScore(),
                            score == null ? null : score.getTotalScore(),
                            score == null ? null : score.getGradePoint(),
                            score != null,
                            warningLevel,
                            WarningLevel.of(warningLevel).getLabel());
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(RosterVO::studentNo, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /** 批量录入/修改成绩。总评 = 平时 × 权重 + 期末 × (1 − 权重)。 */
    @Transactional
    public int saveScores(Long courseId, Long teacherUserId, ScoreDto.ScoreBatchRequest request) {
        courseService.requireOwnedByTeacher(courseId, teacherUserId);
        Teacher teacher = courseService.requireTeacher(teacherUserId);

        Map<Long, CourseSelection> enrolled = selectionMapper.selectList(
                        Wrappers.<CourseSelection>lambdaQuery()
                                .eq(CourseSelection::getCourseId, courseId)
                                .eq(CourseSelection::getStatus, "SELECTED"))
                .stream().collect(Collectors.toMap(CourseSelection::getStudentId, Function.identity(), (a, b) -> a));

        BigDecimal weight = new BigDecimal(usualWeight);
        BigDecimal examWeight = BigDecimal.ONE.subtract(weight);
        LocalDateTime now = LocalDateTime.now();
        int affected = 0;

        for (ScoreDto.ScoreItem item : request.items()) {
            CourseSelection selection = enrolled.get(item.studentId());
            if (selection == null) {
                throw new BusinessException("学生不在该课程的选课名单中，无法录入成绩");
            }
            BigDecimal total = item.usualScore().multiply(weight)
                    .add(item.examScore().multiply(examWeight))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal gradePoint = gradePointOf(total);

            Score existing = scoreMapper.selectOne(Wrappers.<Score>lambdaQuery()
                    .eq(Score::getStudentId, item.studentId())
                    .eq(Score::getCourseId, courseId));
            if (existing == null) {
                Score score = new Score();
                score.setStudentId(item.studentId());
                score.setCourseId(courseId);
                score.setSelectionId(selection.getId());
                score.setUsualScore(item.usualScore());
                score.setExamScore(item.examScore());
                score.setTotalScore(total);
                score.setGradePoint(gradePoint);
                score.setTeacherId(teacher.getId());
                score.setSubmitTime(now);
                scoreMapper.insert(score);
            } else {
                Score patch = new Score();
                patch.setId(existing.getId());
                patch.setUsualScore(item.usualScore());
                patch.setExamScore(item.examScore());
                patch.setTotalScore(total);
                patch.setGradePoint(gradePoint);
                patch.setTeacherId(teacher.getId());
                patch.setSubmitTime(now);
                scoreMapper.updateById(patch);
            }
            affected++;
        }
        return affected;
    }

    /**
     * 总评成绩 → 绩点（五级制换算）。
     *
     * <p>换算区间与 {@code db/data.sql} 第 223~234 行的 CASE 表达式<b>完全一致</b>，
     * 保证演示数据与运行时录入的成绩口径统一：
     * ≥90→4.00，≥85→3.70，≥82→3.30，≥78→3.00，≥75→2.70，≥72→2.30，
     * ≥68→2.00，≥64→1.50，≥60→1.00，其余（含不及格）→0.00。</p>
     *
     * @param totalScore 总评成绩；为 {@code null}（未出成绩）时返回 {@code null}，不写入绩点
     */
    public static BigDecimal gradePointOf(BigDecimal totalScore) {
        if (totalScore == null) {
            return null;
        }
        if (ge(totalScore, "90")) {
            return new BigDecimal("4.00");
        }
        if (ge(totalScore, "85")) {
            return new BigDecimal("3.70");
        }
        if (ge(totalScore, "82")) {
            return new BigDecimal("3.30");
        }
        if (ge(totalScore, "78")) {
            return new BigDecimal("3.00");
        }
        if (ge(totalScore, "75")) {
            return new BigDecimal("2.70");
        }
        if (ge(totalScore, "72")) {
            return new BigDecimal("2.30");
        }
        if (ge(totalScore, "68")) {
            return new BigDecimal("2.00");
        }
        if (ge(totalScore, "64")) {
            return new BigDecimal("1.50");
        }
        if (ge(totalScore, "60")) {
            return new BigDecimal("1.00");
        }
        return new BigDecimal("0.00");
    }

    private static boolean ge(BigDecimal value, String threshold) {
        return value.compareTo(new BigDecimal(threshold)) >= 0;
    }

    /** 课程成绩统计。 */
    public Map<String, Object> statistics(Long courseId, Long teacherUserId) {
        courseService.requireOwnedByTeacher(courseId, teacherUserId);
        Map<String, Object> result = scoreMapper.statisticsByCourse(courseId);
        return result == null ? Map.of() : result;
    }

    /** 学生本人成绩单。 */
    public List<ScoreVO> myScores(Long studentUserId) {
        Student student = courseService.requireStudent(studentUserId);
        List<Score> scores = scoreMapper.selectList(Wrappers.<Score>lambdaQuery()
                .eq(Score::getStudentId, student.getId())
                .orderByDesc(Score::getSubmitTime));
        if (scores.isEmpty()) {
            return List.of();
        }
        Set<Long> courseIds = scores.stream().map(Score::getCourseId).collect(Collectors.toSet());
        Map<Long, Course> courses = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Function.identity(), (a, b) -> a));
        Map<Long, String> teacherNames = lookupService.teacherNameMap();
        Map<Long, String> semesterNames = lookupService.semesterNameMap();
        return scores.stream()
                .map(score -> {
                    Course course = courses.get(score.getCourseId());
                    if (course == null) {
                        return null;
                    }
                    return new ScoreVO(score.getId(), course.getId(), course.getCourseNo(), course.getName(),
                            course.getCredit(), course.getCourseType(),
                            teacherNames.get(course.getTeacherId()), semesterNames.get(course.getSemesterId()),
                            score.getUsualScore(), score.getExamScore(), score.getTotalScore(),
                            score.getGradePoint(), score.getSubmitTime());
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
