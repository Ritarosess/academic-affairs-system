-- =============================================================================
--  高校教务系统 School Management System —— 物理结构设计（数据表 DDL）
--  数据库：MySQL 8.0    字符集：utf8mb4 / utf8mb4_general_ci    引擎：InnoDB
--  共 13 张表：用户、学期、年级、专业、班级、教师、学生、课程、选课记录、成绩、
--              学业预警邮件通知记录、注册申请、邮件（SMTP）配置
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP DATABASE IF EXISTS school_ms;
CREATE DATABASE school_ms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE school_ms;

-- -----------------------------------------------------------------------------
-- 1. sys_user  用户表（统一账号，三种角色共用一张表）
-- -----------------------------------------------------------------------------
CREATE TABLE sys_user (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username             VARCHAR(50)  NOT NULL                COMMENT '登录用户名，全局唯一',
    password             VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 散列，禁止明文）',
    real_name            VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
    role                 VARCHAR(20)  NOT NULL                COMMENT '角色：ADMIN / TEACHER / STUDENT',
    phone                VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
    email                VARCHAR(100)          DEFAULT NULL   COMMENT '电子邮箱',
    status               TINYINT      NOT NULL DEFAULT 1      COMMENT '账号状态：1 启用，0 停用',
    must_change_password TINYINT      NOT NULL DEFAULT 0      COMMENT '是否需强制修改密码：1 是，0 否',
    last_login_time      DATETIME              DEFAULT NULL   COMMENT '最后登录时间',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_role (role),
    KEY idx_user_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- -----------------------------------------------------------------------------
-- 2. semester  学期表（含教务处设定的选课周期）
-- -----------------------------------------------------------------------------
CREATE TABLE semester (
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name              VARCHAR(50) NOT NULL                COMMENT '学期名称，如 2025-2026学年第一学期',
    start_date        DATE                 DEFAULT NULL   COMMENT '学期开始日期',
    end_date          DATE                 DEFAULT NULL   COMMENT '学期结束日期',
    select_start_time  DATETIME               DEFAULT NULL   COMMENT '选课开始时间',
    select_end_time    DATETIME               DEFAULT NULL   COMMENT '选课结束时间',
    drop_end_time      DATETIME               DEFAULT NULL   COMMENT '退课截止时间（为空则与选课结束时间一致）',
    is_current        TINYINT     NOT NULL DEFAULT 0      COMMENT '是否当前学期：1 是，0 否',
    create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_semester_name (name),
    KEY idx_semester_current (is_current)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学期表';

-- -----------------------------------------------------------------------------
-- 3. grade  年级表
-- -----------------------------------------------------------------------------
CREATE TABLE grade (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name        VARCHAR(50) NOT NULL                COMMENT '年级名称，如 2023级',
    enroll_year INT         NOT NULL                COMMENT '入学年份',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_grade_name (name),
    UNIQUE KEY uk_grade_year (enroll_year)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '年级表';

-- -----------------------------------------------------------------------------
-- 4. major  专业表
-- -----------------------------------------------------------------------------
CREATE TABLE major (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name        VARCHAR(100) NOT NULL                COMMENT '专业名称',
    department  VARCHAR(100) NOT NULL                COMMENT '所属院系',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_major_name (name),
    KEY idx_major_dept (department)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '专业表';

-- -----------------------------------------------------------------------------
-- 5. clazz  班级表（隶属专业 + 年级）
-- -----------------------------------------------------------------------------
CREATE TABLE clazz (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    name        VARCHAR(50) NOT NULL                COMMENT '班级名称，如 计科2301',
    major_id    BIGINT      NOT NULL                COMMENT '所属专业ID',
    grade_id    BIGINT      NOT NULL                COMMENT '所属年级ID',
    counselor   VARCHAR(50)          DEFAULT NULL   COMMENT '辅导员',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_clazz_name (name),
    KEY idx_clazz_major (major_id),
    KEY idx_clazz_grade (grade_id),
    CONSTRAINT fk_clazz_major FOREIGN KEY (major_id) REFERENCES major (id),
    CONSTRAINT fk_clazz_grade FOREIGN KEY (grade_id) REFERENCES grade (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '班级表';

-- -----------------------------------------------------------------------------
-- 6. teacher  教师表
-- -----------------------------------------------------------------------------
CREATE TABLE teacher (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT      NOT NULL                COMMENT '关联用户ID',
    teacher_no  VARCHAR(30) NOT NULL                COMMENT '工号',
    name        VARCHAR(50) NOT NULL                COMMENT '姓名',
    gender      VARCHAR(4)           DEFAULT '男'   COMMENT '性别',
    title       VARCHAR(30)          DEFAULT NULL   COMMENT '职称，如 教授/副教授/讲师',
    department  VARCHAR(100)         DEFAULT NULL   COMMENT '所属院系',
    phone       VARCHAR(20)          DEFAULT NULL   COMMENT '联系电话',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_teacher_no (teacher_no),
    UNIQUE KEY uk_teacher_user (user_id),
    CONSTRAINT fk_teacher_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '教师表';

-- -----------------------------------------------------------------------------
-- 7. student  学生表
-- -----------------------------------------------------------------------------
CREATE TABLE student (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT      NOT NULL                COMMENT '关联用户ID',
    student_no  VARCHAR(30) NOT NULL                COMMENT '学号',
    name        VARCHAR(50) NOT NULL                COMMENT '姓名',
    gender      VARCHAR(4)           DEFAULT '男'   COMMENT '性别',
    clazz_id    BIGINT               DEFAULT NULL   COMMENT '所属班级ID',
    phone       VARCHAR(20)          DEFAULT NULL   COMMENT '联系电话',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_no (student_no),
    UNIQUE KEY uk_student_user (user_id),
    KEY idx_student_clazz (clazz_id),
    CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_student_clazz FOREIGN KEY (clazz_id) REFERENCES clazz (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生表';

-- -----------------------------------------------------------------------------
-- 8. course  课程表（即“教学班”：某学期由某教师开设的一次授课单元）
--    selected_count 为冗余计数字段，选课/退课时以原子 UPDATE 方式维护，用于防超卖
-- -----------------------------------------------------------------------------
CREATE TABLE course (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    course_no      VARCHAR(30)   NOT NULL                COMMENT '课程号',
    name           VARCHAR(100)  NOT NULL                COMMENT '课程名称',
    credit         DECIMAL(3, 1) NOT NULL                COMMENT '学分',
    hours          INT           NOT NULL DEFAULT 32     COMMENT '学时',
    course_type    VARCHAR(20)   NOT NULL DEFAULT '必修' COMMENT '课程性质：必修/选修/限选',
    teacher_id     BIGINT                 DEFAULT NULL   COMMENT '任课教师ID',
    semester_id    BIGINT        NOT NULL                COMMENT '开课学期ID',
    capacity       INT           NOT NULL DEFAULT 50     COMMENT '课程容量',
    selected_count INT           NOT NULL DEFAULT 0      COMMENT '已选人数',
    weekday        TINYINT                DEFAULT NULL   COMMENT '上课星期：1~7',
    start_section  TINYINT                DEFAULT NULL   COMMENT '开始节次',
    end_section    TINYINT                DEFAULT NULL   COMMENT '结束节次',
    location       VARCHAR(50)            DEFAULT NULL   COMMENT '上课地点',
    description    VARCHAR(255)           DEFAULT NULL   COMMENT '课程简介',
    status         TINYINT       NOT NULL DEFAULT 1      COMMENT '选课开关：1 开放选课，0 关闭',
    audit_status   VARCHAR(20)   NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已拒绝',
    review_remark  VARCHAR(255)           DEFAULT NULL   COMMENT '审核意见',
    reviewer_id    BIGINT                 DEFAULT NULL   COMMENT '审核管理员ID',
    review_time    DATETIME               DEFAULT NULL   COMMENT '审核时间',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_no_semester (course_no, semester_id),
    KEY idx_course_teacher (teacher_id),
    KEY idx_course_semester (semester_id),
    KEY idx_course_audit (audit_status),
    CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teacher (id),
    CONSTRAINT fk_course_semester FOREIGN KEY (semester_id) REFERENCES semester (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课程表';

-- -----------------------------------------------------------------------------
-- 9. course_selection  选课记录表
--    唯一键 (student_id, course_id) 从数据库层面杜绝重复选课；
--    退课不删除记录，而是将 status 置为 DROPPED，重选时复用该行
-- -----------------------------------------------------------------------------
CREATE TABLE course_selection (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    student_id  BIGINT      NOT NULL                COMMENT '学生ID',
    course_id   BIGINT      NOT NULL                COMMENT '课程ID',
    status      VARCHAR(20) NOT NULL DEFAULT 'SELECTED' COMMENT '选课状态：SELECTED 已选 / DROPPED 已退',
    select_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '选课时间',
    drop_time   DATETIME             DEFAULT NULL   COMMENT '退课时间',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_selection_student_course (student_id, course_id),
    KEY idx_selection_course (course_id),
    KEY idx_selection_status (status),
    CONSTRAINT fk_selection_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_selection_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '选课记录表';

-- -----------------------------------------------------------------------------
-- 10. score  成绩表
-- -----------------------------------------------------------------------------
CREATE TABLE score (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    student_id   BIGINT        NOT NULL                COMMENT '学生ID',
    course_id    BIGINT        NOT NULL                COMMENT '课程ID',
    selection_id BIGINT                 DEFAULT NULL   COMMENT '关联选课记录ID',
    usual_score  DECIMAL(5, 2)          DEFAULT NULL   COMMENT '平时成绩',
    exam_score   DECIMAL(5, 2)          DEFAULT NULL   COMMENT '期末成绩',
    total_score  DECIMAL(5, 2)          DEFAULT NULL   COMMENT '总评成绩',
    grade_point  DECIMAL(3, 2)          DEFAULT NULL   COMMENT '绩点',
    teacher_id   BIGINT                 DEFAULT NULL   COMMENT '录入教师ID',
    submit_time  DATETIME               DEFAULT NULL   COMMENT '提交时间',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_score_student_course (student_id, course_id),
    KEY idx_score_course (course_id),
    CONSTRAINT fk_score_student FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_score_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '成绩表';

-- -----------------------------------------------------------------------------
-- 11. warning_notification  学业预警邮件通知记录表
--     记录每一次向学生发送学业预警通知邮件的结果，供教务处追溯与避免重复打扰。
--     表中冗余保存发送时的等级与关键指标快照，便于事后核对（当时为何判定为预警）。
-- -----------------------------------------------------------------------------
CREATE TABLE warning_notification (
    id                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    student_id                BIGINT       NOT NULL                COMMENT '学生ID',
    user_id                   BIGINT       NOT NULL                COMMENT '账号ID',
    student_no                VARCHAR(30)  NOT NULL                COMMENT '学号（快照）',
    student_name              VARCHAR(50)  NOT NULL                COMMENT '姓名（快照）',
    email                     VARCHAR(100)          DEFAULT NULL   COMMENT '收件邮箱（发送时快照）',
    level                     VARCHAR(20)  NOT NULL                COMMENT '发送时的预警等级：YELLOW/ORANGE/RED',
    level_label               VARCHAR(50)           DEFAULT NULL   COMMENT '等级中文名（快照）',
    failed_credits            DECIMAL(5, 1)         DEFAULT NULL   COMMENT '发送时累计不及格学分（快照）',
    max_semester_failed_count INT                   DEFAULT NULL   COMMENT '发送时单学期最多不及格门数（快照）',
    subject                   VARCHAR(200) NOT NULL                COMMENT '邮件主题',
    content                   TEXT                                COMMENT '邮件正文（纯文本版本）',
    status                    VARCHAR(20)  NOT NULL                COMMENT '结果：SENT 已发送 / SIMULATED 模拟发送 / FAILED 发送失败 / SKIPPED 无邮箱跳过',
    error_message             VARCHAR(500)          DEFAULT NULL   COMMENT '失败原因',
    operator_id               BIGINT                DEFAULT NULL   COMMENT '操作管理员ID',
    send_time                 DATETIME     NOT NULL                COMMENT '发送时间',
    create_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_notify_student (student_id),
    KEY idx_notify_status (status),
    KEY idx_notify_send_time (send_time),
    CONSTRAINT fk_notify_student FOREIGN KEY (student_id) REFERENCES student (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学业预警邮件通知记录表';

-- -----------------------------------------------------------------------------
-- 12. registration_apply  注册申请表
--     学生 / 教师自助注册后先写入本表（密码已 BCrypt 散列），状态为 PENDING，
--     由管理员审核；通过后才在 sys_user 与 student / teacher 中生成正式账号。
--     独立成表可避免未审核账号污染 sys_user，保证数据完整性。
-- -----------------------------------------------------------------------------
CREATE TABLE registration_apply (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username      VARCHAR(50)  NOT NULL                COMMENT '申请用户名',
    password      VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt 散列）',
    real_name     VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
    role          VARCHAR(20)  NOT NULL                COMMENT '申请角色：STUDENT / TEACHER',
    phone         VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
    email         VARCHAR(100)          DEFAULT NULL   COMMENT '电子邮箱',
    gender        VARCHAR(4)            DEFAULT '男'   COMMENT '性别',
    student_no    VARCHAR(30)           DEFAULT NULL   COMMENT '学号（学生申请）',
    clazz_id      BIGINT                DEFAULT NULL   COMMENT '班级ID（学生申请）',
    teacher_no    VARCHAR(30)           DEFAULT NULL   COMMENT '工号（教师申请）',
    title         VARCHAR(30)           DEFAULT NULL   COMMENT '职称（教师申请）',
    department    VARCHAR(100)          DEFAULT NULL   COMMENT '所属院系（教师申请）',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已拒绝',
    review_remark VARCHAR(255)          DEFAULT NULL   COMMENT '审核意见',
    reviewer_id   BIGINT                DEFAULT NULL   COMMENT '审核管理员ID',
    review_time   DATETIME              DEFAULT NULL   COMMENT '审核时间',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_reg_status (status),
    KEY idx_reg_username (username),
    KEY idx_reg_student_no (student_no),
    KEY idx_reg_teacher_no (teacher_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '注册申请表';

-- -----------------------------------------------------------------------------
-- 13. mail_config  邮件（SMTP）配置表（单行，固定 id = 1）
--     管理员在「邮件配置」页维护；应用启动时若为空，用 application.yml 的
--     spring.mail.* / app.mail.* 作为初始种子写入。密码字段存放 SMTP 授权码。
-- -----------------------------------------------------------------------------
CREATE TABLE mail_config (
    id             BIGINT       NOT NULL DEFAULT 1      COMMENT '固定主键=1',
    enabled        TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '是否真实发送：0 模拟 / 1 真实',
    host           VARCHAR(120)          DEFAULT NULL   COMMENT 'SMTP 服务器地址',
    port           INT                   DEFAULT 465    COMMENT 'SMTP 端口',
    username       VARCHAR(120)          DEFAULT NULL   COMMENT 'SMTP 登录账号（发件邮箱）',
    password       VARCHAR(200)          DEFAULT NULL   COMMENT 'SMTP 授权码（非邮箱登录密码）',
    use_auth       TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否需要认证',
    use_ssl        TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否启用 SSL（端口 465）',
    use_starttls   TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '是否启用 STARTTLS（端口 587）',
    from_addr      VARCHAR(120)          DEFAULT NULL   COMMENT '发件人地址，留空则用登录账号',
    from_name      VARCHAR(80)           DEFAULT '高校教务系统' COMMENT '发件人显示名',
    subject_prefix VARCHAR(80)           DEFAULT '【学业预警】' COMMENT '邮件主题前缀',
    portal_url     VARCHAR(200)          DEFAULT 'http://localhost:5173' COMMENT '学生查询入口',
    sender         VARCHAR(80)           DEFAULT '教务处' COMMENT '邮件落款单位',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '邮件 SMTP 配置表（单行）';

SET FOREIGN_KEY_CHECKS = 1;