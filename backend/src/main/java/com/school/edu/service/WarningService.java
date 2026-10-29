package com.school.edu.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.school.edu.common.BusinessException;
import com.school.edu.common.PageResult;
import com.school.edu.common.WarningLevel;
import com.school.edu.config.WarningProperties;
import com.school.edu.entity.Clazz;
import com.school.edu.entity.Student;
import com.school.edu.entity.SysUser;
import com.school.edu.mapper.ScoreMapper;
import com.school.edu.mapper.StudentMapper;
import com.school.edu.mapper.SysUserMapper;
import com.school.edu.vo.WarningMetrics;
import com.school.edu.vo.WarningRuleVO;
import com.school.edu.vo.WarningVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学业红线预警服务。
 *
 * <p>预警指标由成绩数据实时计算得出（不落库），保证与成绩始终一致。两条规则分别为：
 * ① 累计不及格学分 ② 单学期最多不及格门数；最终等级取两者中最严重的一条。</p>
 *
 * <p>阈值全部来自 {@link WarningProperties}（application.yml），可按学校规定调整。</p>
 */
@Service
@RequiredArgsConstructor
public class WarningService {

    /** 规则编码 */
    public static final String RULE_FAILED_CREDITS = "FAILED_CREDITS";
    public static final String RULE_SEMESTER_FAILED_COUNT = "SEMESTER_FAILED_COUNT";

    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final SysUserMapper sysUserMapper;
    private final LookupService lookupService;
    private final WarningProperties properties;

    /* ============================================================ 指标装载 */

    /**
     * 一次性聚合全部学生的预警指标，避免按学生逐个查询造成 N+1。
     */
    public Map<Long, WarningMetrics> allMetrics() {
        return scoreMapper.selectWarningMetrics().stream()
                .filter(m -> m.getStudentId() != null)
                .collect(Collectors.toMap(WarningMetrics::getStudentId, Function.identity(), (a, b) -> a));
    }

    /* ============================================================ 等级判定 */

    /** 综合等级：取两条规则中最严重的一条。 */
    public WarningLevel levelOf(WarningMetrics metrics) {
        if (metrics == null) {
            return WarningLevel.NORMAL;
        }
        WarningLevel byCredits = levelByFailedCredits(metrics.failedCreditsSafe());
        WarningLevel byCount = levelBySemesterFailedCount(metrics.maxSemesterFailedCountSafe());
        return WarningLevel.max(byCredits, byCount);
    }

    private WarningLevel levelByFailedCredits(BigDecimal value) {
        if (ge(value, properties.getRed().getFailedCredits())) {
            return WarningLevel.RED;
        }
        if (ge(value, properties.getOrange().getFailedCredits())) {
            return WarningLevel.ORANGE;
        }
        if (ge(value, properties.getYellow().getFailedCredits())) {
            return WarningLevel.YELLOW;
        }
        return WarningLevel.NORMAL;
    }

    private WarningLevel levelBySemesterFailedCount(int count) {
        if (count >= nz(properties.getRed().getSemesterFailedCount())) {
            return WarningLevel.RED;
        }
        if (count >= nz(properties.getOrange().getSemesterFailedCount())) {
            return WarningLevel.ORANGE;
        }
        if (count >= nz(properties.getYellow().getSemesterFailedCount())) {
            return WarningLevel.YELLOW;
        }
        return WarningLevel.NORMAL;
    }

    private static boolean ge(BigDecimal value, BigDecimal threshold) {
        return value != null && threshold != null && value.compareTo(threshold) >= 0;
    }

    private static int nz(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private static String fmt(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    /* ============================================================ 组装 */

    public WarningVO build(Student student, WarningMetrics metrics) {
        return build(student, metrics, emailOf(student.getUserId()));
    }

    /** 组装预警视图对象。email 由调用方提供，以便批量场景一次取出、避免 N+1 查询。 */
    public WarningVO build(Student student, WarningMetrics metrics, String email) {
        WarningMetrics m = metrics == null ? new WarningMetrics() : metrics;
        Map<Long, Clazz> clazzMap = lookupService.clazzMap();
        Map<Long, String> majorNames = lookupService.majorNameMap();
        Map<Long, String> gradeNames = lookupService.gradeNameMap();
        Clazz clazz = student.getClazzId() == null ? null : clazzMap.get(student.getClazzId());

        WarningLevel level = levelOf(metrics);
        List<WarningRuleVO> rules = buildRules(m);

        return new WarningVO(
                student.getId(), student.getUserId(), student.getStudentNo(), student.getName(), student.getGender(),
                clazz == null ? null : clazz.getId(),
                clazz == null ? null : clazz.getName(),
                clazz == null ? null : majorNames.get(clazz.getMajorId()),
                clazz == null ? null : gradeNames.get(clazz.getGradeId()),
                email,
                level.name(), level.getLabel(), level.getTagType(), level.isTriggered(),
                summaryOf(level, m), adviceOf(level),
                m.failedCreditsSafe(), m.failedCourseCountSafe(), m.scoredCourseCountSafe(),
                m.scoredCreditsSafe(), m.passedCreditsSafe(), m.maxSemesterFailedCountSafe(),
                rules);
    }

    private List<WarningRuleVO> buildRules(WarningMetrics m) {
        List<WarningRuleVO> rules = new ArrayList<>();

        WarningLevel creditsLevel = levelByFailedCredits(m.failedCreditsSafe());
        rules.add(new WarningRuleVO(RULE_FAILED_CREDITS, "累计不及格学分",
                fmt(m.failedCreditsSafe()),
                "≥" + fmt(properties.getYellow().getFailedCredits()) + " 黄 / ≥"
                        + fmt(properties.getOrange().getFailedCredits()) + " 橙 / ≥"
                        + fmt(properties.getRed().getFailedCredits()) + " 红",
                creditsLevel.name(), creditsLevel.getLabel(), creditsLevel.isTriggered(),
                "所有已出成绩课程中总评低于 60 分的学分之和"));

        WarningLevel countLevel = levelBySemesterFailedCount(m.maxSemesterFailedCountSafe());
        rules.add(new WarningRuleVO(RULE_SEMESTER_FAILED_COUNT, "单学期最多不及格门数",
                String.valueOf(m.maxSemesterFailedCountSafe()),
                "≥" + nz(properties.getYellow().getSemesterFailedCount()) + " 黄 / ≥"
                        + nz(properties.getOrange().getSemesterFailedCount()) + " 橙 / ≥"
                        + nz(properties.getRed().getSemesterFailedCount()) + " 红",
                countLevel.name(), countLevel.getLabel(), countLevel.isTriggered(),
                "按学期分组统计不及格门数后取最大值，反映单学期学业压力"));

        return rules;
    }

    private String summaryOf(WarningLevel level, WarningMetrics m) {
        if (m.scoredCourseCountSafe() == 0) {
            return "暂无成绩数据，无法进行学业评估";
        }
        return switch (level) {
            case RED -> "已达到学业红线：累计不及格 " + fmt(m.failedCreditsSafe()) + " 学分，"
                    + "单学期最多不及格 " + m.maxSemesterFailedCountSafe() + " 门";
            case ORANGE -> "已触发学业预警：累计不及格 " + fmt(m.failedCreditsSafe()) + " 学分，需重点关注";
            case YELLOW -> "存在学业关注信号：累计不及格 " + fmt(m.failedCreditsSafe()) + " 学分";
            case NORMAL -> "学业状况正常：已修 " + fmt(m.passedCreditsSafe()) + " 学分，无不及格记录";
        };
    }

    private String adviceOf(WarningLevel level) {
        return switch (level) {
            case RED -> "请立即联系辅导员与院系教务员，制定学业帮扶计划，并优先安排不及格课程重修；"
                    + "若当学期不及格门数继续增加，将影响毕业学分审核。";
            case ORANGE -> "建议尽快与辅导员沟通，梳理不及格课程并安排重修，避免不及格学分继续累积。";
            case YELLOW -> "建议及时调整学习状态，重点关注薄弱课程，防止不及格学分累积到预警线。";
            case NORMAL -> "请继续保持当前学习状态。";
        };
    }

    /* ============================================================ 对外查询 */

    /** 学生本人预警详情。 */
    public WarningVO forUser(Long userId) {
        Student student = studentMapper.selectOne(
                Wrappers.<Student>lambdaQuery().eq(Student::getUserId, userId));
        if (student == null) {
            throw BusinessException.notFound("未找到当前登录用户的学籍信息");
        }
        return build(student, allMetrics().get(student.getId()));
    }

    /** 学生 ID → 等级名，供成绩名单等场景附加预警标记。 */
    public Map<Long, String> levelNames(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, WarningMetrics> metrics = allMetrics();
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long id : studentIds) {
            result.put(id, levelOf(metrics.get(id)).name());
        }
        return result;
    }

    /** 按学生 ID 批量获取预警详情（含邮箱与规则明细），供邮件通知使用。 */
    public List<WarningVO> byStudentIds(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        Map<Long, WarningMetrics> metrics = allMetrics();
        List<Student> students = studentMapper.selectBatchIds(studentIds);
        Map<Long, String> emails = emailsByUserId(students.stream().map(Student::getUserId).toList());
        return students.stream()
                .map(st -> build(st, metrics.get(st.getId()), emails.get(st.getUserId())))
                .toList();
    }

    /** 单个学生的邮箱（账号中登记）。 */
    public String emailOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = sysUserMapper.selectById(userId);
        return user == null ? null : user.getEmail();
    }

    /** 批量取「用户 ID → 邮箱」，一次查询完成。 */
    private Map<Long, String> emailsByUserId(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> u.getEmail() == null ? "" : u.getEmail(),
                        (a, b) -> a));
    }

    /**
     * 管理员预警名单（内存分页）。
     *
     * @param level   等级过滤；留空表示只看已触发预警的学生；传 ALL 表示全部学生
     * @param clazzId 班级过滤
     * @param majorId 专业过滤
     */
    public PageResult<WarningVO> page(String level, Long clazzId, Long majorId, String keyword,
                                      long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = size <= 0 ? 10 : size;

        Map<Long, WarningMetrics> metrics = allMetrics();
        Map<Long, Clazz> clazzMap = lookupService.clazzMap();

        List<Student> students = studentMapper.selectList(Wrappers.<Student>lambdaQuery()
                .eq(clazzId != null, Student::getClazzId, clazzId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Student::getName, keyword).or()
                        .like(Student::getStudentNo, keyword))
                .orderByAsc(Student::getStudentNo));
        // 一次取出名单内全部邮箱，避免逐个学生查询
        Map<Long, String> emails = emailsByUserId(students.stream().map(Student::getUserId).toList());

        List<WarningVO> all = students.stream()
                .filter(st -> {
                    if (majorId == null) {
                        return true;
                    }
                    Clazz clazz = st.getClazzId() == null ? null : clazzMap.get(st.getClazzId());
                    return clazz != null && majorId.equals(clazz.getMajorId());
                })
                .map(st -> build(st, metrics.get(st.getId()), emails.get(st.getUserId())))
                .filter(vo -> matchesLevel(vo, level))
                .sorted(Comparator
                        .comparingInt((WarningVO vo) -> WarningLevel.of(vo.level()).getSeverity()).reversed()
                        .thenComparing(WarningVO::failedCredits, Comparator.reverseOrder())
                        .thenComparing(WarningVO::studentNo, Comparator.nullsLast(String::compareTo)))
                .toList();

        int from = (int) Math.min((current - 1) * pageSize, all.size());
        int to = (int) Math.min(from + pageSize, all.size());
        return PageResult.of(all.size(), current, pageSize, all.subList(from, to));
    }

    private boolean matchesLevel(WarningVO vo, String level) {
        if (!StringUtils.hasText(level)) {
            return vo.triggered();
        }
        if ("ALL".equalsIgnoreCase(level.trim())) {
            return true;
        }
        return vo.level().equalsIgnoreCase(level.trim());
    }

    /** 预警总览：各等级人数与当前生效的阈值，供管理员页面顶部展示。 */
    public Map<String, Object> summary() {
        Map<Long, WarningMetrics> metrics = allMetrics();
        List<Student> students = studentMapper.selectList(null);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (WarningLevel lv : WarningLevel.values()) {
            counts.put(lv.name(), 0);
        }
        int withScores = 0;
        for (Student st : students) {
            WarningMetrics m = metrics.get(st.getId());
            if (m != null && m.scoredCourseCountSafe() > 0) {
                withScores++;
            }
            counts.merge(levelOf(m).name(), 1, Integer::sum);
        }

        int flagged = counts.get(WarningLevel.YELLOW.name())
                + counts.get(WarningLevel.ORANGE.name())
                + counts.get(WarningLevel.RED.name());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalStudents", students.size());
        result.put("evaluatedStudents", withScores);
        result.put("flaggedStudents", flagged);
        result.put("redCount", counts.get(WarningLevel.RED.name()));
        result.put("orangeCount", counts.get(WarningLevel.ORANGE.name()));
        result.put("yellowCount", counts.get(WarningLevel.YELLOW.name()));
        result.put("normalCount", counts.get(WarningLevel.NORMAL.name()));
        result.put("thresholds", List.of(
                Map.of("level", "RED", "levelLabel", WarningLevel.RED.getLabel(),
                        "failedCredits", fmt(properties.getRed().getFailedCredits()),
                        "semesterFailedCount", nz(properties.getRed().getSemesterFailedCount())),
                Map.of("level", "ORANGE", "levelLabel", WarningLevel.ORANGE.getLabel(),
                        "failedCredits", fmt(properties.getOrange().getFailedCredits()),
                        "semesterFailedCount", nz(properties.getOrange().getSemesterFailedCount())),
                Map.of("level", "YELLOW", "levelLabel", WarningLevel.YELLOW.getLabel(),
                        "failedCredits", fmt(properties.getYellow().getFailedCredits()),
                        "semesterFailedCount", nz(properties.getYellow().getSemesterFailedCount()))));
        return result;
    }
}
