-- =============================================================================
--  高校教务系统 —— 初始化数据（演示数据）
--  执行顺序：先 schema.sql，再 data.sql
--
--  说明：所有种子账号的初始密码均为 123456，密码字段存放的是
--  Spring Security BCryptPasswordEncoder(10) 生成的散列（已验证 matches = true）。
--  选课周期使用 NOW() 相对时间生成，保证导入数据后选课窗口始终处于开放状态，
--  便于演示与验收；生产环境应由教务处按实际校历设置。
-- =============================================================================

SET NAMES utf8mb4;
USE school_ms;

-- BCrypt 散列后的 "123456"
SET @pw = '$2a$10$xLe38yPtBPr673qUI81MCe0WWxKEuMjV4/seLbVl6k4kAkvbq/F5G';

-- -----------------------------------------------------------------------------
-- 1. 用户（1 管理员 + 5 教师 + 26 学生）
--    约定：学生 2024xxx 的 user_id = student_id + 6
-- -----------------------------------------------------------------------------
INSERT INTO sys_user (id, username, password, real_name, role, phone, email, status, must_change_password) VALUES
(1,  'admin', @pw, '系统管理员', 'ADMIN',   '13800000000', 'admin@school.edu.cn', 1, 0),
(2,  't001',  @pw, '张伟',      'TEACHER', '13900000001', 't001@school.edu.cn',  1, 0),
(3,  't002',  @pw, '李娜',      'TEACHER', '13900000002', 't002@school.edu.cn',  1, 0),
(4,  't003',  @pw, '王强',      'TEACHER', '13900000003', 't003@school.edu.cn',  1, 0),
(5,  't004',  @pw, '刘敏',      'TEACHER', '13900000004', 't004@school.edu.cn',  1, 0),
(6,  't005',  @pw, '陈静',      'TEACHER', '13900000005', 't005@school.edu.cn',  1, 0),
(7,  '2024001', @pw, '李雷',   'STUDENT', '13700000001', NULL, 1, 0),
(8,  '2024002', @pw, '韩梅梅', 'STUDENT', '13700000002', NULL, 1, 0),
(9,  '2024003', @pw, '张强',   'STUDENT', '13700000003', NULL, 1, 0),
(10, '2024004', @pw, '刘洋',   'STUDENT', '13700000004', NULL, 1, 0),
(11, '2024005', @pw, '王芳',   'STUDENT', '13700000005', NULL, 1, 0),
(12, '2024006', @pw, '陈晨',   'STUDENT', '13700000006', NULL, 1, 0),
(13, '2024007', @pw, '杨帆',   'STUDENT', '13700000007', NULL, 1, 0),
(14, '2024008', @pw, '赵磊',   'STUDENT', '13700000008', NULL, 1, 0),
(15, '2024009', @pw, '周婷',   'STUDENT', '13700000009', NULL, 1, 0),
(16, '2024010', @pw, '吴迪',   'STUDENT', '13700000010', NULL, 1, 0),
(17, '2024011', @pw, '郑爽',   'STUDENT', '13700000011', NULL, 1, 0),
(18, '2024012', @pw, '孙浩',   'STUDENT', '13700000012', NULL, 1, 0),
(19, '2024013', @pw, '马丽',   'STUDENT', '13700000013', NULL, 1, 0),
(20, '2024014', @pw, '朱涛',   'STUDENT', '13700000014', NULL, 1, 0),
(21, '2024015', @pw, '胡静',   'STUDENT', '13700000015', NULL, 1, 0),
(22, '2024016', @pw, '林峰',   'STUDENT', '13700000016', NULL, 1, 0),
(23, '2024017', @pw, '高鹏',   'STUDENT', '13700000017', NULL, 1, 0),
(24, '2024018', @pw, '何雪',   'STUDENT', '13700000018', NULL, 1, 0),
(25, '2025001', @pw, '罗宇',   'STUDENT', '13700000019', NULL, 1, 0),
(26, '2025002', @pw, '谢婷',   'STUDENT', '13700000020', NULL, 1, 0),
(27, '2025003', @pw, '唐磊',   'STUDENT', '13700000021', NULL, 1, 0),
(28, '2025004', @pw, '曹静',   'STUDENT', '13700000022', NULL, 1, 0),
(29, '2025005', @pw, '邓超',   'STUDENT', '13700000023', NULL, 1, 0),
(30, '2025006', @pw, '冯丽',   'STUDENT', '13700000024', NULL, 1, 0),
(31, '2025007', @pw, '曾勇',   'STUDENT', '13700000025', NULL, 1, 0),
(32, '2025008', @pw, '彭悦',   'STUDENT', '13700000026', NULL, 1, 0);

-- 为学生账号补全邮箱（学业预警邮件通知的收件地址）。
-- 有意保留 2024007（杨帆）没有邮箱，用于演示"无邮箱跳过"的异常分支。
UPDATE sys_user
SET email = CONCAT(username, '@student.school.edu.cn')
WHERE role = 'STUDENT' AND username <> '2024007';

-- -----------------------------------------------------------------------------
-- 2. 学期与选课周期
-- -----------------------------------------------------------------------------
INSERT INTO semester (id, name, start_date, end_date, select_start_time, select_end_time, is_current) VALUES
(1, '2026-2027学年第一学期', '2026-09-01', '2027-01-15',
    DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 120 DAY), 1),
(2, '2025-2026学年第一学期', '2025-09-01', '2026-01-15',
    '2025-09-01 08:00:00', '2025-10-15 23:59:59', 0);

-- -----------------------------------------------------------------------------
-- 3. 年级 / 专业 / 班级
-- -----------------------------------------------------------------------------
INSERT INTO grade (id, name, enroll_year) VALUES
(1, '2024级', 2024),
(2, '2025级', 2025),
(3, '2026级', 2026);

INSERT INTO major (id, name, department) VALUES
(1, '计算机科学与技术',     '计算机学院'),
(2, '软件工程',             '计算机学院'),
(3, '数据科学与大数据技术', '计算机学院'),
(4, '电子信息工程',         '电子信息学院');

INSERT INTO clazz (id, name, major_id, grade_id, counselor) VALUES
(1, '计科2401', 1, 1, '王老师'),
(2, '计科2402', 1, 1, '王老师'),
(3, '软工2401', 2, 1, '李老师'),
(4, '数据2501', 3, 2, '赵老师'),
(5, '电信2501', 4, 2, '孙老师');

-- -----------------------------------------------------------------------------
-- 4. 教师档案
-- -----------------------------------------------------------------------------
INSERT INTO teacher (id, user_id, teacher_no, name, gender, title, department, phone) VALUES
(1, 2, 'T001', '张伟', '男', '教授',   '计算机学院',   '13900000001'),
(2, 3, 'T002', '李娜', '女', '副教授', '计算机学院',   '13900000002'),
(3, 4, 'T003', '王强', '男', '讲师',   '计算机学院',   '13900000003'),
(4, 5, 'T004', '刘敏', '女', '副教授', '数学学院',     '13900000004'),
(5, 6, 'T005', '陈静', '女', '讲师',   '电子信息学院', '13900000005');

-- -----------------------------------------------------------------------------
-- 5. 学生档案（user_id = id + 6）
-- -----------------------------------------------------------------------------
INSERT INTO student (id, user_id, student_no, name, gender, clazz_id, phone) VALUES
(1,  7,  '2024001', '李雷',   '男', 1, '13700000001'),
(2,  8,  '2024002', '韩梅梅', '女', 1, '13700000002'),
(3,  9,  '2024003', '张强',   '男', 1, '13700000003'),
(4,  10, '2024004', '刘洋',   '男', 1, '13700000004'),
(5,  11, '2024005', '王芳',   '女', 1, '13700000005'),
(6,  12, '2024006', '陈晨',   '男', 1, '13700000006'),
(7,  13, '2024007', '杨帆',   '男', 1, '13700000007'),
(8,  14, '2024008', '赵磊',   '男', 1, '13700000008'),
(9,  15, '2024009', '周婷',   '女', 2, '13700000009'),
(10, 16, '2024010', '吴迪',   '男', 2, '13700000010'),
(11, 17, '2024011', '郑爽',   '女', 2, '13700000011'),
(12, 18, '2024012', '孙浩',   '男', 2, '13700000012'),
(13, 19, '2024013', '马丽',   '女', 2, '13700000013'),
(14, 20, '2024014', '朱涛',   '男', 2, '13700000014'),
(15, 21, '2024015', '胡静',   '女', 3, '13700000015'),
(16, 22, '2024016', '林峰',   '男', 3, '13700000016'),
(17, 23, '2024017', '高鹏',   '男', 3, '13700000017'),
(18, 24, '2024018', '何雪',   '女', 3, '13700000018'),
(19, 25, '2025001', '罗宇',   '男', 4, '13700000019'),
(20, 26, '2025002', '谢婷',   '女', 4, '13700000020'),
(21, 27, '2025003', '唐磊',   '男', 4, '13700000021'),
(22, 28, '2025004', '曹静',   '女', 4, '13700000022'),
(23, 29, '2025005', '邓超',   '男', 5, '13700000023'),
(24, 30, '2025006', '冯丽',   '女', 5, '13700000024'),
(25, 31, '2025007', '曾勇',   '男', 5, '13700000025'),
(26, 32, '2025008', '彭悦',   '女', 5, '13700000026');

-- -----------------------------------------------------------------------------
-- 6. 课程（当前学期 2026-2027学年第一学期）
--    selected_count 先置 0，最后统一按选课记录重算，保证数据一致
-- -----------------------------------------------------------------------------
INSERT INTO course (id, course_no, name, credit, hours, course_type, teacher_id, semester_id,
                    capacity, selected_count, weekday, start_section, end_section, location, description, status) VALUES
(1,  'CS101', '数据结构',       4.0, 64, '必修', 1, 1, 60, 0, 1, 1, 2, '教一101', '线性表、树、图及其算法实现', 1),
(2,  'CS102', '计算机组成原理', 3.5, 56, '必修', 1, 1, 60, 0, 2, 3, 4, '教一102', 'CPU、存储层次与指令系统',     1),
(3,  'CS201', '操作系统',       4.0, 64, '必修', 2, 1, 50, 0, 3, 1, 2, '教二201', '进程、内存与文件系统',       1),
(4,  'CS202', '计算机网络',     3.0, 48, '必修', 2, 1, 50, 0, 4, 3, 4, '教二202', 'TCP/IP 协议栈与网络编程',    1),
(5,  'CS301', '数据库原理',     4.0, 64, '必修', 3, 1, 55, 0, 5, 1, 2, '教一201', '关系模型、SQL 与事务',       1),
(6,  'CS302', '软件工程',       3.0, 48, '必修', 3, 1, 55, 0, 1, 3, 4, '教一202', '软件过程、需求与设计方法',   1),
(7,  'MA101', '高等数学',       5.0, 80, '必修', 4, 1, 80, 0, 2, 1, 2, '教三301', '极限、微分与积分',           1),
(8,  'MA102', '线性代数',       3.0, 48, '必修', 4, 1, 80, 0, 3, 3, 4, '教三302', '矩阵、行列式与线性方程组',   1),
(9,  'EL201', '数字电子技术',   3.5, 56, '限选', 5, 1, 45, 0, 4, 1, 2, '教四101', '逻辑门、组合与时序电路',     1),
(10, 'EL202', '信号与系统',     3.0, 48, '选修', 5, 1, 40, 0, 5, 3, 4, '教四102', '连续与离散信号分析',         1);

-- -----------------------------------------------------------------------------
-- 7. 选课记录（模拟学生已完成选课的情况）
-- -----------------------------------------------------------------------------
-- 数据结构：计科2401 全班
INSERT INTO course_selection (student_id, course_id, status, select_time)
SELECT s.id, 1, 'SELECTED', DATE_SUB(NOW(), INTERVAL 5 DAY)
FROM student s WHERE s.clazz_id = 1;

-- 操作系统：计科2401、计科2402
INSERT INTO course_selection (student_id, course_id, status, select_time)
SELECT s.id, 3, 'SELECTED', DATE_SUB(NOW(), INTERVAL 4 DAY)
FROM student s WHERE s.clazz_id IN (1, 2);

-- 数据库原理：计科2401、计科2402、软工2401
INSERT INTO course_selection (student_id, course_id, status, select_time)
SELECT s.id, 5, 'SELECTED', DATE_SUB(NOW(), INTERVAL 3 DAY)
FROM student s WHERE s.clazz_id IN (1, 2, 3);

-- 高等数学：全部 2024 级
INSERT INTO course_selection (student_id, course_id, status, select_time)
SELECT s.id, 7, 'SELECTED', DATE_SUB(NOW(), INTERVAL 3 DAY)
FROM student s WHERE s.clazz_id IN (1, 2, 3);

-- 数字电子技术：软工2401
INSERT INTO course_selection (student_id, course_id, status, select_time)
SELECT s.id, 9, 'SELECTED', DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM student s WHERE s.clazz_id = 3;

-- 一条已退课记录，用于演示退课状态与重选逻辑
INSERT INTO course_selection (student_id, course_id, status, select_time, drop_time)
VALUES (1, 10, 'DROPPED', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- -----------------------------------------------------------------------------
-- 8. 成绩（为「数据结构」的部分学生预置，便于演示成绩查询与统计）
--    总评 = 平时 × 40% + 期末 × 60%
-- -----------------------------------------------------------------------------
INSERT INTO score (student_id, course_id, selection_id, usual_score, exam_score, total_score, teacher_id, submit_time)
SELECT cs.student_id, cs.course_id, cs.id, 88.00, 92.00, 90.40, 1, NOW()
FROM course_selection cs WHERE cs.course_id = 1 AND cs.student_id IN (1, 2, 3);

INSERT INTO score (student_id, course_id, selection_id, usual_score, exam_score, total_score, teacher_id, submit_time)
SELECT cs.student_id, cs.course_id, cs.id, 80.00, 85.00, 83.00, 1, NOW()
FROM course_selection cs WHERE cs.course_id = 1 AND cs.student_id IN (4, 5, 6);

INSERT INTO score (student_id, course_id, selection_id, usual_score, exam_score, total_score, teacher_id, submit_time)
SELECT cs.student_id, cs.course_id, cs.id, 62.00, 55.00, 57.80, 1, NOW()
FROM course_selection cs WHERE cs.course_id = 1 AND cs.student_id = 7;

-- -----------------------------------------------------------------------------
-- 8.5 历史学期（2025-2026学年第一学期）课程与成绩
--     目的：让「累计不及格学分」与「单学期不及格门数」两个预警指标产生差异，
--          并使学生具备多门已评课程，预警指标才有统计意义。
-- -----------------------------------------------------------------------------
INSERT INTO course (id, course_no, name, credit, hours, course_type, teacher_id, semester_id,
                    capacity, selected_count, weekday, start_section, end_section, location, description, status) VALUES
(11, 'CS103', '程序设计基础',     4.0, 64, '必修', 1, 2, 60, 0, 1, 1, 2, '教一103', 'C 语言基础与程序设计方法', 0),
(12, 'CS203', '离散数学',         3.0, 48, '必修', 3, 2, 60, 0, 2, 3, 4, '教一203', '集合、图论与数理逻辑',     0),
(13, 'MA201', '概率论与数理统计', 3.0, 48, '必修', 4, 2, 60, 0, 3, 1, 2, '教三303', '随机变量、分布与统计推断', 0),
(14, 'EL101', '电路分析基础',     2.0, 32, '必修', 5, 2, 60, 0, 4, 3, 4, '教四103', '电路定律与网络分析',       0);

-- 2024 级学生均已修读上述历史学期课程
INSERT INTO course_selection (student_id, course_id, status, select_time)
SELECT s.id, c.id, 'SELECTED', DATE_SUB(NOW(), INTERVAL 320 DAY)
FROM student s
CROSS JOIN course c
WHERE s.clazz_id IN (1, 2, 3) AND c.semester_id = 2;

-- -----------------------------------------------------------------------------
-- 8.6 生成及格成绩（历史学期 4 门 + 当前学期课程 3/5/7）
--     课程 1 的成绩已在第 8 节单独给出，此处不再覆盖。
--     平时 70~95，期末 72~95，总评 71~97，全部及格。
-- -----------------------------------------------------------------------------
INSERT INTO score (student_id, course_id, selection_id, usual_score, exam_score, total_score, grade_point, teacher_id, submit_time)
SELECT t.student_id, t.course_id, t.selection_id, t.usual_score, t.exam_score, t.total_score,
       CASE
           WHEN t.total_score >= 90 THEN 4.00
           WHEN t.total_score >= 85 THEN 3.70
           WHEN t.total_score >= 82 THEN 3.30
           WHEN t.total_score >= 78 THEN 3.00
           WHEN t.total_score >= 75 THEN 2.70
           WHEN t.total_score >= 72 THEN 2.30
           WHEN t.total_score >= 68 THEN 2.00
           WHEN t.total_score >= 64 THEN 1.50
           WHEN t.total_score >= 60 THEN 1.00
           ELSE 0.00
       END AS grade_point,
       t.teacher_id, t.submit_time
FROM (
    SELECT b.student_id, b.course_id, b.selection_id, b.teacher_id, b.submit_time,
           b.usual_score, b.exam_score,
           ROUND(b.usual_score * 0.4 + b.exam_score * 0.6, 2) AS total_score
    FROM (
        SELECT cs.student_id AS student_id,
               cs.course_id  AS course_id,
               cs.id         AS selection_id,
               c.teacher_id  AS teacher_id,
               CASE WHEN c.semester_id = 2 THEN DATE_SUB(NOW(), INTERVAL 200 DAY)
                    ELSE DATE_SUB(NOW(), INTERVAL 10 DAY) END AS submit_time,
               70 + (cs.student_id * 7 + c.id * 5) % 26 AS usual_score,
               72 + (cs.student_id * 11 + c.id * 3) % 24 AS exam_score
        FROM course_selection cs
        JOIN course c ON c.id = cs.course_id
        WHERE c.semester_id = 2 OR (c.semester_id = 1 AND c.id IN (3, 5, 7))
    ) b
) t;

-- -----------------------------------------------------------------------------
-- 8.7 指定若干学生不及格，用于演示三档学业红线预警
--     阈值配置见 backend/src/main/resources/application.yml 的 app.warning
--       学生 1：历史学期 4 门全部不及格  → 累计 12 学分 / 单学期 4 门  → 红色（红线）
--       学生 2：历史学期 2 门不及格      → 累计  7 学分               → 橙色（预警）
--       学生 3：历史学期 2 门不及格      → 累计  6 学分               → 橙色（预警）
--       学生 4：历史学期 1 门不及格      → 累计  3 学分               → 黄色（关注）
--       学生 5：历史学期 1 门不及格      → 累计  4 学分               → 黄色（关注）
--       学生 7：当前学期课程 1 不及格    → 累计  4 学分               → 黄色（关注）
-- -----------------------------------------------------------------------------
UPDATE score s
JOIN course c ON c.id = s.course_id
SET s.usual_score = 48.00, s.exam_score = 52.00, s.total_score = 50.40, s.grade_point = 0.00
WHERE c.semester_id = 2
  AND (
        s.student_id = 1
     OR (s.student_id = 2 AND s.course_id IN (11, 12))
     OR (s.student_id = 3 AND s.course_id IN (12, 13))
     OR (s.student_id = 4 AND s.course_id = 13)
     OR (s.student_id = 5 AND s.course_id = 11)
  );

-- -----------------------------------------------------------------------------
-- 9. 重算课程已选人数，保证 selected_count 与选课记录严格一致
-- -----------------------------------------------------------------------------
UPDATE course c
SET c.selected_count = (SELECT COUNT(*) FROM course_selection cs
                        WHERE cs.course_id = c.id AND cs.status = 'SELECTED');

-- -----------------------------------------------------------------------------
-- 10. 校验输出
-- -----------------------------------------------------------------------------
SELECT '用户' AS 表, COUNT(*) AS 记录数 FROM sys_user
UNION ALL SELECT '教师', COUNT(*) FROM teacher
UNION ALL SELECT '学生', COUNT(*) FROM student
UNION ALL SELECT '课程', COUNT(*) FROM course
UNION ALL SELECT '选课记录', COUNT(*) FROM course_selection
UNION ALL SELECT '成绩', COUNT(*) FROM score
UNION ALL SELECT '有邮箱的学生账号', COUNT(*) FROM sys_user WHERE role = 'STUDENT' AND email IS NOT NULL;
