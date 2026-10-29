# 高校教务系统 School Management System

一套面向高校教务管理的前后端分离 Web 系统，覆盖 **在线选退课、成绩录入与统计、学业红线预警与邮件通知、用户与组织维护** 四条业务主线，支持学生 / 教师 / 管理员三类角色。

| 维度 | 内容 |
| --- | --- |
| 后端 | Spring Boot 3.2.5、JDK 21、Spring Security + JWT、MyBatis-Plus 3.5.5、Spring Mail |
| 前端 | Vue 3.5、Vite 6、Element Plus 2.9、Vue Router 4、Pinia 2、Axios |
| 数据库 | MySQL 8.0（`utf8mb4` / InnoDB），**13 张表** |
| 架构 | Controller → Service → Mapper 分层，RESTful，前后端分离 |
| 规模 | 97 个 Java 文件（13 控制器 / 15 服务 / 13 Mapper）、22 个 Vue 组件、68 个 REST 接口 |

---

## 1. 功能特性

### 1.1 学生

- **选课大厅**：按学期、院系、课程性质筛选，显示余量与授课教师，一键选课 / 退课
- **我的课表**：周课表视图，连续节次自动合并
- **我的成绩**：总评、绩点、平均绩点；不及格与预警提示条
- **学业预警**：查看本人的预警等级（黄 / 橙 / 红）与触发原因
- **个人中心**：维护本人邮箱（用于接收预警邮件）、修改密码

### 1.2 教师

- **我的课程**：查看本学期授课班级与学生名单
- **成绩录入**：批量录入平时 / 期末成绩，自动计算总评与绩点，超范围或负分被拦截
- **课程申报**：提交新开课程，等待管理员审核
- **选课名单**：查看已选学生及人数

### 1.3 管理员

- **用户管理**：学生 / 教师账号增删改、启用停用、重置密码
- **组织数据**：院系、专业、年级、班级维护
- **学期与选课周期**：设定当前学期、选课起止时间（选退课校验的统一时间基准）
- **课程审核**：批准或驳回教师申报的课程（**管理员不能直接建课**，必须走申报审核）
- **注册审核**：审核学生 / 教师自助注册申请
- **学业预警**：查看全校预警名单与明细，手动触发批量邮件发送
- **邮件配置**：设置 SMTP 参数、发送测试邮件、切换模拟 / 真实发送模式
- **数据总览**：工作台统计卡片

### 1.4 公共

- JWT 无状态登录，未携带令牌返回 401，越权访问返回 403
- 统一响应结构 `{ code, message, data }`，业务异常给出明确提示且不泄露堆栈
- 同一浏览器可用不同标签页**同时以多个角色登录**（令牌存于 `sessionStorage`，按标签页隔离）

---

## 2. 快速开始

### 2.1 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | Spring Boot 3.2 要求 17+，本项目按 21 编译 |
| Maven | 3.8+ | 项目内已本地化 `tools/apache-maven-3.9.9` |
| MySQL | 8.0+ | 字符集 `utf8mb4` |
| Node.js | 18+ | 构建前端 |

### 2.2 初始化数据库

```bash
cd db
mysql -u root -p --default-character-set=utf8mb4 -e "source schema.sql"
mysql -u root -p --default-character-set=utf8mb4 -e "source data.sql"
```

`schema.sql` 创建数据库 `school_ms` 与 **13 张表**；`data.sql` 写入演示数据。灌入后的数据基线：

| 表 | 行数 |
| --- | --- |
| `sys_user`（1 管理员 + 5 教师 + 26 学生） | 32 |
| `teacher` / `student` | 5 / 26 |
| `course` | 14 |
| `course_selection` | 135 |
| `score` | 129 |

其中 25 名学生有邮箱，学业预警分布为 **红 1 人、橙 2 人、黄 3 人**，其余正常。

> 数据库连接默认 `root` / `123456`，在 `backend/src/main/resources/application.yml` 中配置。真实部署请用环境变量注入。

### 2.3 启动后端

```bash
cd backend
# 使用项目内置 Maven + 项目内 settings（推荐）
../tools/apache-maven-3.9.9/bin/mvn -s ../tools/maven-settings.xml package -DskipTests
java -jar target/edu-system.jar
```

服务监听 <http://localhost:8080>。

> 也可以用 `scripts/start-backend.ps1` 一键启动：它会自动探测 JDK 21、加载 `config/mail.local.ps1` 中的邮件凭据，并通过 `-Port` 参数同步邮件正文里的系统入口地址。

### 2.4 启动前端（开发模式）

```bash
cd frontend
npm install
npm run dev
```

访问 <http://localhost:5173>，Vite 会把 `/api` 代理到 `http://localhost:8080`。

> 请用 `localhost` 而非 `127.0.0.1` 访问开发服务器：本机 `localhost` 解析为 IPv6 `::1`，Vite 默认只监听该地址。

### 2.5 单端口运行（演示 / 部署模式）

把前端产物并入后端静态目录，只启动 8080 一个端口：

```powershell
& scripts\build.ps1
& scripts\start-backend.ps1
```

`build.ps1` 依次执行：安装前端依赖 → `vite build` → 复制 `frontend/dist/*` 到 `backend/src/main/resources/static/` → 后端打包。

### 2.6 重置演示数据

```powershell
& scripts\reset-db.ps1
```

---

## 3. 演示账号

密码统一为 `123456`。

| 角色 | 账号 | 说明 |
| --- | --- | --- |
| 管理员 | `admin` | 全部管理功能 |
| 教师 | `t001` ~ `t005` | 授课、成绩录入、课程申报 |
| 学生 | `2024001` ~ `2024018` | 2024 级在校生 |
| 学生 | `2025001` ~ `2025008` | 2025 级新生 |

---

## 4. 目录结构

```
高校教务系统/
├─ backend/                       Spring Boot 后端
│  └─ src/main/
│     ├─ java/com/school/edu/
│     │  ├─ controller/           13 个控制器（按角色划分 URL 前缀）
│     │  ├─ service/              15 个服务类，业务规则与事务边界
│     │  ├─ mapper/               13 个 MyBatis-Plus Mapper
│     │  ├─ entity/ dto/ vo/      数据模型与视图对象
│     │  ├─ security/             JWT 过滤器、认证入口
│     │  ├─ common/               统一响应 Result、业务异常、全局异常处理
│     │  └─ config/               安全、跨域、邮件等配置
│     └─ resources/               application.yml、mapper/*.xml、static/（构建产物）
├─ frontend/                      Vue 3 前端
│  └─ src/
│     ├─ views/                   18 个页面：admin / teacher / student / login / common
│     ├─ components/ layouts/     公共组件与布局
│     └─ api/ router/ stores/     Axios 封装、路由与 Pinia 状态
├─ db/                            schema.sql（13 张表）、data.sql（演示数据）
├─ scripts/                       _jdk.ps1、start-backend.ps1、build.ps1、reset-db.ps1
├─ config/                        mail.local.ps1.example（邮件凭据模板）
└─ tools/                         接口 / 边界 / 并发 / 邮件等测试脚本
```

---

## 5. 核心设计

### 5.1 选课防超卖

选课是本系统唯一的高并发写入场景。**先查询余量再更新**在并发下必然超卖，因此把容量判断下沉到更新语句的条件里，由数据库在行锁保护下完成「比较并更新」，应用只依据受影响行数判断是否抢到座位：

```sql
UPDATE course
   SET selected_count = selected_count + 1
 WHERE id = #{courseId}
   AND selected_count < capacity
```

```java
@Transactional
public void select(Long courseId, Long studentId) {
    // 校验选课周期、课程审核状态、学期归属、是否重复选课
    if (courseMapper.tryOccupySeat(courseId) == 0) {
        throw new BusinessException("该课程名额已满");
    }
    // 写入选课记录；唯一索引兜底
}
```

三重保障：**原子条件更新** + **事务回滚** + `course_selection(student_id, course_id)` **唯一索引**。并发测试用 12 个令牌抢 3 个名额，成功数恒为 3、容量不越界。

### 5.2 退课不删除记录

退课只把状态置为 `DROPPED` 并记录退课时间，容量在同一事务内还原。既保留完整的选退课轨迹便于追溯，也避免了「删除 + 重新插入」在唯一索引下可能出现的并发竞态。规则上另有两条拦截：**必修课不可退**、**已录入成绩不可退**。

### 5.3 选课周期

学期的选课起止时间由管理员设定，是选课 / 退课校验的**统一时间基准**。周期外的请求一律拒绝，避免各接口各自判断导致口径不一致。

### 5.4 认证与权限

- 登录签发 JWT，`JwtAuthenticationFilter` 解析令牌并构造认证对象，会话策略为无状态
- 角色绑定到 **URL 前缀**：`/api/admin/**` → 管理员，`/api/teacher/**` → 教师，`/api/student/**` → 学生。新增接口只要落在对应命名空间内就自动受保护，从机制上避免漏配
- 服务端二次校验资源归属，不信任前端传入的用户标识
- 口令以 BCrypt 散列存储

### 5.5 成绩与绩点

总评 = 平时 × 40% + 期末 × 60%（权重可在 `app.score.usual-weight` 调整），绩点按十档换算：

| 总评 | ≥90 | ≥85 | ≥82 | ≥78 | ≥75 | ≥72 | ≥68 | ≥64 | ≥60 | <60 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 绩点 | 4.00 | 3.70 | 3.30 | 3.00 | 2.70 | 2.30 | 2.00 | 1.50 | 1.00 | 0.00 |

分数取值校验在 0~100 之间，非法输入直接拒绝。

### 5.6 学业红线预警

**只使用两条规则**，各自定级，最终取较严重者（`WarningLevel.max()`）：

| 等级 | 累计不及格学分 | 单学期最多不及格门数 |
| --- | --- | --- |
| 黄 | ≥ 3 | ≥ 2 |
| 橙 | ≥ 6 | ≥ 3 |
| 红 | ≥ 10 | ≥ 4 |

阈值外置于 `app.warning`，可按学校规定调整。等级**实时计算、不落库**，保证补考及格后预警立刻消失，不会出现陈旧数据；仅在真正发送邮件时把当时的等级与指标快照写入通知记录。

预警**只是提示，不影响选课**。

### 5.7 预警邮件

分三层：配置服务读取 SMTP 参数并构造发送器，邮件服务构建纯文本 + HTML 双版本正文，通知服务负责批量编排与结果留痕。

- **模拟 / 真实双模式**：模拟模式只把正文写入日志、不发起任何网络请求，便于无外网环境演示；开关在管理员「邮件配置」页
- **发送结果四态**：`SENT` 已发送、`SIMULATED` 模拟发送、`FAILED` 发送失败、`SKIPPED` 无邮箱跳过，逐条写入 `warning_notification`
- 发送为**同步**执行（未使用 `@Async`），以便接口返回时就能拿到逐条结果

### 5.8 邮件凭据不入库

真实 SMTP 授权码**不要**写进 `application.yml`（该文件受版本控制）。做法是复制模板后填入本地文件：

```powershell
Copy-Item config\mail.local.ps1.example config\mail.local.ps1
# 编辑 config\mail.local.ps1 填入 SMTP 授权码，然后直接 scripts\start-backend.ps1
```

`config/mail.local.ps1` 已被 `.gitignore` 忽略。`application.yml` 中 `MAIL_USERNAME` / `MAIL_PASSWORD` 的默认值为空，只从本地配置或环境变量注入。

---

## 6. 测试与验证

所有脚本位于 `tools/`，需先启动后端并保持干净的数据基线。合计 **430 项断言全部通过**。

| 脚本 | 覆盖范围 | 断言 |
| --- | --- | ---: |
| `api-test.mjs` | 接口功能：三类角色主要流程 | 90 |
| `debug-frontend-api.mjs` | 前端调用与接口契约一致性 | 81 |
| `debug-warning.mjs` | 学业预警两条规则与等级 | 63 |
| `debug-warning-frontend.mjs` | 预警前端组件联动 | 44 |
| `debug-edge-cases.mjs` | 边界值与异常输入 | 40 |
| `debug-profile-email.mjs` | 学生自助维护邮箱 | 31 |
| `debug-multi-user.mjs` | 多用户并发登录 | 23 |
| `debug-devserver.mjs` | 开发服务器与代理 | 23 |
| `debug-mail-config.mjs` | 邮件配置与发送模式 | 18 |
| `verify-integration.mjs` | 单端口集成：静态资源、SPA 回退、真实 HTTP | 17 |

运行示例：

```bash
node tools/api-test.mjs          # 需后端已在 8080 运行
node tools/verify-integration.mjs
```

**性能实测**（本机单实例，`tools/bench-api.mjs`）：核心接口平均延迟 **15.7 ~ 112.2 ms**；100 并发下吞吐约 **297 次/秒**，成功率 **100%**。

---

## 7. 安全说明

- 口令 BCrypt 散列；JWT 密钥与数据库口令均可用环境变量覆盖
- 未认证返回 401、越权返回 403；服务端校验资源归属，不信任前端传入的用户标识
- 业务异常统一为 `{ code, message }`，不向前端泄露堆栈与数据库细节
- 日志按语义分级：业务异常 WARN 单行，参数错误 400、方法不支持 405、资源不存在 404，仅未知异常记 ERROR 堆栈
- 演示账号密码（`123456`）仅用于本地演示，**部署到公网前务必修改**

---

## 8. 关于文档

本仓库只包含**源代码与数据库脚本**。需求、设计、测试等文稿，以及按学校表单填写的课程作业文档（含 `.docx` 成品）**不随仓库分发**，原因见 `.gitignore` 中的说明。

因此本文档不提供指向这些文稿的链接；项目的行为约定（接口契约、预警阈值、绩点规则、防超卖策略）均已在上面第 5 节完整说明。

---

## 9. 已知限制

- 预警邮件为同步发送，批量发送大量学生时会阻塞请求；规模增大后应改为异步队列
- 尚未实现邮箱验证，SMTP 配置错误只能在发送失败时暴露
- 未做 JVM 与连接池调优，性能数据为本机单实例结果，不代表生产容量
- 前端未做单元测试，质量保障依赖接口测试与端到端脚本
