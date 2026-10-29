package com.school.edu.service;

import com.school.edu.entity.Grade;
import com.school.edu.entity.Major;
import com.school.edu.entity.Semester;
import com.school.edu.vo.ClazzVO;
import com.school.edu.vo.TeacherOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公共元数据服务：为前端下拉框与选课周期提示提供数据，并集中维护「选课周期是否开放」这一核心业务规则。
 */
@Service
@RequiredArgsConstructor
public class MetaService {

    private final LookupService lookupService;

    public List<TeacherOptionVO> teachers() {
        return lookupService.allTeachers().stream()
                .map(t -> new TeacherOptionVO(t.getId(), t.getName(), t.getTeacherNo(), t.getDepartment()))
                .toList();
    }

    public List<Semester> semesters() {
        return lookupService.allSemesters();
    }

    public List<Grade> grades() {
        return lookupService.allGrades();
    }

    public List<Major> majors() {
        return lookupService.allMajors();
    }

    public List<ClazzVO> clazzes() {
        Map<Long, String> majorNames = lookupService.majorNameMap();
        Map<Long, String> gradeNames = lookupService.gradeNameMap();
        return lookupService.allClazzes().stream()
                .map(c -> new ClazzVO(c.getId(), c.getName(), c.getMajorId(), majorNames.get(c.getMajorId()),
                        c.getGradeId(), gradeNames.get(c.getGradeId()), c.getCounselor()))
                .toList();
    }

    /** 选课周期判定：必须在当前学期且当前时间落在选课窗口内（BR-01）。 */
    public boolean isSelectionOpen() {
        Semester current = lookupService.currentSemester();
        return isOpen(current);
    }

    private boolean isOpen(Semester semester) {
        if (semester == null || semester.getSelectStartTime() == null || semester.getSelectEndTime() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(semester.getSelectStartTime()) && !now.isAfter(semester.getSelectEndTime());
    }

    /**
     * 退课窗口判定：选课开始后 ~ 退课截止时间前。
     * 未单独配置退课截止时间时，退课窗口与选课窗口一致。
     */
    public boolean isDropOpen() {
        Semester current = lookupService.currentSemester();
        if (current == null || current.getSelectStartTime() == null) {
            return false;
        }
        LocalDateTime deadline = current.getDropEndTime() != null
                ? current.getDropEndTime() : current.getSelectEndTime();
        if (deadline == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(current.getSelectStartTime()) && !now.isAfter(deadline);
    }

    public Map<String, Object> selectionWindow() {
        Semester current = lookupService.currentSemester();
        Map<String, Object> result = new LinkedHashMap<>();
        if (current == null) {
            result.put("open", false);
            result.put("semesterName", null);
            result.put("selectStartTime", null);
            result.put("selectEndTime", null);
            result.put("dropOpen", false);
            result.put("dropEndTime", null);
            result.put("message", "尚未设置当前学期，请联系教务处");
            return result;
        }
        boolean open = isOpen(current);
        LocalDateTime dropDeadline = current.getDropEndTime() != null
                ? current.getDropEndTime() : current.getSelectEndTime();
        result.put("open", open);
        result.put("dropOpen", isDropOpen());
        result.put("semesterName", current.getName());
        result.put("selectStartTime", current.getSelectStartTime());
        result.put("selectEndTime", current.getSelectEndTime());
        result.put("dropEndTime", dropDeadline);
        result.put("message", open ? "当前处于选课时段" : "当前非选课时段");
        return result;
    }
}
