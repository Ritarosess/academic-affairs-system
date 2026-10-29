package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.school.edu.common.BusinessException;
import com.school.edu.dto.SemesterDto;
import com.school.edu.entity.Course;
import com.school.edu.entity.Semester;
import com.school.edu.mapper.CourseMapper;
import com.school.edu.mapper.SemesterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 学期与选课周期管理。选课周期是选课/退课的开关，由教务处在此配置（BR-01）。
 */
@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterMapper semesterMapper;
    private final CourseMapper courseMapper;
    private final LookupService lookupService;

    public List<Semester> list() {
        return lookupService.allSemesters();
    }

    @Transactional
    public Semester save(Long id, SemesterDto.SemesterRequest request) {
        if (request.selectStartTime() != null && request.selectEndTime() != null
                && !request.selectStartTime().isBefore(request.selectEndTime())) {
            throw new BusinessException("选课开始时间必须早于结束时间");
        }
        if (request.dropEndTime() != null && request.selectStartTime() != null
                && !request.selectStartTime().isBefore(request.dropEndTime())) {
            throw new BusinessException("退课截止时间必须晚于选课开始时间");
        }
        if (request.startDate() != null && request.endDate() != null
                && request.startDate().isAfter(request.endDate())) {
            throw new BusinessException("学期开始日期不能晚于结束日期");
        }
        Long dup = semesterMapper.selectCount(Wrappers.<Semester>lambdaQuery()
                .eq(Semester::getName, request.name()).ne(id != null, Semester::getId, id));
        if (dup > 0) {
            throw new BusinessException("学期名称已存在：" + request.name());
        }

        Semester entity = new Semester();
        entity.setId(id);
        entity.setName(request.name());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setSelectStartTime(request.selectStartTime());
        entity.setSelectEndTime(request.selectEndTime());
        entity.setDropEndTime(request.dropEndTime());
        entity.setIsCurrent(id == null ? 0 : null);
        if (id == null) {
            semesterMapper.insert(entity);
        } else {
            semesterMapper.updateById(entity);
        }
        if (request.isCurrent() != null && request.isCurrent() == 1) {
            setCurrent(entity.getId());
        }
        return semesterMapper.selectById(entity.getId());
    }

    /** 设为当前学期：先清空其他学期的标记，保证全局唯一。 */
    @Transactional
    public void setCurrent(Long id) {
        Semester target = semesterMapper.selectById(id);
        if (target == null) {
            throw BusinessException.notFound("学期不存在");
        }
        Semester clear = new Semester();
        clear.setIsCurrent(0);
        semesterMapper.update(clear, Wrappers.<Semester>lambdaUpdate().eq(Semester::getIsCurrent, 1));

        Semester patch = new Semester();
        patch.setId(id);
        patch.setIsCurrent(1);
        semesterMapper.updateById(patch);
    }

    @Transactional
    public void delete(Long id) {
        if (courseMapper.selectCount(Wrappers.<Course>lambdaQuery().eq(Course::getSemesterId, id)) > 0) {
            throw new BusinessException("该学期下已有课程，无法删除");
        }
        semesterMapper.deleteById(id);
    }
}
