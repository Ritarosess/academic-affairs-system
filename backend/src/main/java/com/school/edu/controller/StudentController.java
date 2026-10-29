package com.school.edu.controller;

import com.school.edu.common.PageResult;
import com.school.edu.common.Result;
import com.school.edu.dto.SelectionDto;
import com.school.edu.security.SecurityUtils;
import com.school.edu.service.AuthService;
import com.school.edu.service.CourseService;
import com.school.edu.service.SelectionService;
import com.school.edu.service.ScoreService;
import com.school.edu.service.WarningService;
import com.school.edu.vo.CourseVO;
import com.school.edu.vo.ProfileVO;
import com.school.edu.vo.SelectionVO;
import com.school.edu.vo.ScoreVO;
import com.school.edu.vo.WarningVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生接口：选课大厅、选课、退课、我的课表、我的成绩。
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final CourseService courseService;
    private final SelectionService selectionService;
    private final ScoreService scoreService;
    private final AuthService authService;
    private final WarningService warningService;

    /** 选课大厅：当前学期课程列表，含剩余名额与是否已选。 */
    @GetMapping("/courses")
    public Result<PageResult<CourseVO>> courses(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String courseType,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return Result.ok(courseService.pageForStudent(SecurityUtils.currentUserId(), keyword, courseType, page, size));
    }

    /** 在线选课（UC-03）。 */
    @PostMapping("/selections")
    public Result<Void> select(@Valid @RequestBody SelectionDto.SelectRequest request) {
        selectionService.select(SecurityUtils.currentUserId(), request.courseId());
        return Result.ok("选课成功", null);
    }

    /** 退课（UC-04）。 */
    @DeleteMapping("/selections/{courseId}")
    public Result<Void> drop(@PathVariable Long courseId) {
        selectionService.drop(SecurityUtils.currentUserId(), courseId);
        return Result.ok("退课成功", null);
    }

    /** 我的课表。 */
    @GetMapping("/selections")
    public Result<List<SelectionVO>> mySelections() {
        return Result.ok(selectionService.mySelections(SecurityUtils.currentUserId()));
    }

    /** 我的成绩。 */
    @GetMapping("/scores")
    public Result<List<ScoreVO>> myScores() {
        return Result.ok(scoreService.myScores(SecurityUtils.currentUserId()));
    }

    /** 我的学籍信息。 */
    @GetMapping("/profile")
    public Result<ProfileVO> profile() {
        return Result.ok(authService.profile(SecurityUtils.currentUserId()));
    }

    /** 我的学业红线预警（教务处提示）。 */
    @GetMapping("/warning")
    public Result<WarningVO> warning() {
        return Result.ok(warningService.forUser(SecurityUtils.currentUserId()));
    }
}
