package com.school.edu.controller;

import com.school.edu.common.Result;
import com.school.edu.dto.CourseDto;
import com.school.edu.dto.ScoreDto;
import com.school.edu.security.SecurityUtils;
import com.school.edu.service.AuthService;
import com.school.edu.service.CourseService;
import com.school.edu.service.ScoreService;
import com.school.edu.vo.CourseVO;
import com.school.edu.vo.ProfileVO;
import com.school.edu.vo.RosterVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 教师接口：授课课程、选课学生名单、成绩录入与统计。
 */
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherController {

    private final CourseService courseService;
    private final ScoreService scoreService;
    private final AuthService authService;

    /** 我的课程（已通过审核、实际任教的课程，UC-07）。 */
    @GetMapping("/courses")
    public Result<List<CourseVO>> courses() {
        return Result.ok(courseService.listByTeacherUser(SecurityUtils.currentUserId()));
    }

    /** 课程申报：本人申报的全部课程及审核状态。 */
    @GetMapping("/course-applies")
    public Result<List<CourseVO>> courseApplies() {
        return Result.ok(courseService.listAppliesByTeacherUser(SecurityUtils.currentUserId()));
    }

    /** 申报新课程（提交后待管理员审核）。 */
    @PostMapping("/courses")
    public Result<CourseVO> createCourse(@Valid @RequestBody CourseDto.TeacherCourseRequest request) {
        return Result.ok("课程申报已提交，请等待管理员审核",
                courseService.createForTeacher(SecurityUtils.currentUserId(), request));
    }

    /** 修改本人尚未通过审核的课程（修改后重新进入待审核）。 */
    @PutMapping("/courses/{id}")
    public Result<CourseVO> updateCourse(@PathVariable Long id,
                                         @Valid @RequestBody CourseDto.TeacherCourseRequest request) {
        return Result.ok("已重新提交审核",
                courseService.updateForTeacher(id, SecurityUtils.currentUserId(), request));
    }

    /** 删除本人尚未通过审核的课程。 */
    @DeleteMapping("/courses/{id}")
    public Result<Void> deleteCourse(@PathVariable Long id) {
        courseService.deleteForTeacher(id, SecurityUtils.currentUserId());
        return Result.ok("已删除", null);
    }

    /** 选择该课程的学生名单（UC-08）。 */
    @GetMapping("/courses/{courseId}/students")
    public Result<List<RosterVO>> students(@PathVariable Long courseId) {
        return Result.ok(scoreService.roster(courseId, SecurityUtils.currentUserId()));
    }

    /** 批量录入/修改成绩（UC-09）。 */
    @PostMapping("/courses/{courseId}/scores")
    public Result<Void> saveScores(@PathVariable Long courseId,
                                   @Valid @RequestBody ScoreDto.ScoreBatchRequest request) {
        int affected = scoreService.saveScores(courseId, SecurityUtils.currentUserId(), request);
        return Result.ok("保存成功，共更新 " + affected + " 条成绩", null);
    }

    /** 成绩统计。 */
    @GetMapping("/courses/{courseId}/statistics")
    public Result<Map<String, Object>> statistics(@PathVariable Long courseId) {
        return Result.ok(scoreService.statistics(courseId, SecurityUtils.currentUserId()));
    }

    /** 我的教职信息。 */
    @GetMapping("/profile")
    public Result<ProfileVO> profile() {
        return Result.ok(authService.profile(SecurityUtils.currentUserId()));
    }
}
