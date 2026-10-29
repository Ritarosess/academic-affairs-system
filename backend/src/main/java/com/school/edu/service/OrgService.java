package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.school.edu.common.BusinessException;
import com.school.edu.dto.OrgDto;
import com.school.edu.entity.Clazz;
import com.school.edu.entity.Grade;
import com.school.edu.entity.Major;
import com.school.edu.entity.Student;
import com.school.edu.mapper.ClazzMapper;
import com.school.edu.mapper.GradeMapper;
import com.school.edu.mapper.MajorMapper;
import com.school.edu.mapper.StudentMapper;
import com.school.edu.vo.ClazzVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 组织架构管理（年级 / 专业 / 班级）。删除前做引用校验，避免产生孤儿数据。
 */
@Service
@RequiredArgsConstructor
public class OrgService {

    private final GradeMapper gradeMapper;
    private final MajorMapper majorMapper;
    private final ClazzMapper clazzMapper;
    private final StudentMapper studentMapper;
    private final LookupService lookupService;

    public List<Grade> listGrades() {
        return lookupService.allGrades();
    }

    @Transactional
    public Grade saveGrade(Long id, OrgDto.GradeRequest request) {
        Long dup = gradeMapper.selectCount(Wrappers.<Grade>lambdaQuery()
                .eq(Grade::getName, request.name()).ne(id != null, Grade::getId, id));
        if (dup > 0) {
            throw new BusinessException("年级名称已存在：" + request.name());
        }
        Grade entity = new Grade();
        entity.setId(id);
        entity.setName(request.name());
        entity.setEnrollYear(request.enrollYear());
        if (id == null) {
            gradeMapper.insert(entity);
        } else {
            gradeMapper.updateById(entity);
        }
        return gradeMapper.selectById(entity.getId());
    }

    @Transactional
    public void deleteGrade(Long id) {
        if (clazzMapper.selectCount(Wrappers.<Clazz>lambdaQuery().eq(Clazz::getGradeId, id)) > 0) {
            throw new BusinessException("该年级下仍有班级，无法删除");
        }
        gradeMapper.deleteById(id);
    }

    public List<Major> listMajors() {
        return lookupService.allMajors();
    }

    @Transactional
    public Major saveMajor(Long id, OrgDto.MajorRequest request) {
        Long dup = majorMapper.selectCount(Wrappers.<Major>lambdaQuery()
                .eq(Major::getName, request.name()).ne(id != null, Major::getId, id));
        if (dup > 0) {
            throw new BusinessException("专业名称已存在：" + request.name());
        }
        Major entity = new Major();
        entity.setId(id);
        entity.setName(request.name());
        entity.setDepartment(request.department());
        if (id == null) {
            majorMapper.insert(entity);
        } else {
            majorMapper.updateById(entity);
        }
        return majorMapper.selectById(entity.getId());
    }

    @Transactional
    public void deleteMajor(Long id) {
        if (clazzMapper.selectCount(Wrappers.<Clazz>lambdaQuery().eq(Clazz::getMajorId, id)) > 0) {
            throw new BusinessException("该专业下仍有班级，无法删除");
        }
        majorMapper.deleteById(id);
    }

    public List<ClazzVO> listClazzes() {
        Map<Long, String> majorNames = lookupService.majorNameMap();
        Map<Long, String> gradeNames = lookupService.gradeNameMap();
        return lookupService.allClazzes().stream()
                .map(c -> new ClazzVO(c.getId(), c.getName(), c.getMajorId(), majorNames.get(c.getMajorId()),
                        c.getGradeId(), gradeNames.get(c.getGradeId()), c.getCounselor()))
                .toList();
    }

    @Transactional
    public ClazzVO saveClazz(Long id, OrgDto.ClazzRequest request) {
        if (majorMapper.selectById(request.majorId()) == null) {
            throw new BusinessException("所选专业不存在");
        }
        if (gradeMapper.selectById(request.gradeId()) == null) {
            throw new BusinessException("所选年级不存在");
        }
        Long dup = clazzMapper.selectCount(Wrappers.<Clazz>lambdaQuery()
                .eq(Clazz::getName, request.name()).ne(id != null, Clazz::getId, id));
        if (dup > 0) {
            throw new BusinessException("班级名称已存在：" + request.name());
        }
        Clazz entity = new Clazz();
        entity.setId(id);
        entity.setName(request.name());
        entity.setMajorId(request.majorId());
        entity.setGradeId(request.gradeId());
        entity.setCounselor(request.counselor());
        if (id == null) {
            clazzMapper.insert(entity);
        } else {
            clazzMapper.updateById(entity);
        }
        return listClazzes().stream()
                .filter(c -> c.id().equals(entity.getId()))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("班级不存在"));
    }

    @Transactional
    public void deleteClazz(Long id) {
        if (studentMapper.selectCount(Wrappers.<Student>lambdaQuery().eq(Student::getClazzId, id)) > 0) {
            throw new BusinessException("该班级下仍有学生，无法删除");
        }
        clazzMapper.deleteById(id);
    }
}
