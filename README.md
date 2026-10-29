# 高校教务系统 School Management System

一套面向高校教务管理的前后端分离 Web 系统，覆盖 **学生选课 / 退课、教师成绩录入、管理员用户与课程维护、教务处学业红线预警与邮件通知** 四条业务主线。

- **后端**：Spring Boot 3.2 + Spring Security + JWT + MyBatis-Plus + Spring Mail + MySQL 8.0（JDK 21）
- **前端**：Vue 3 + Vite + Element Plus + Vue Router + Pinia + Axios
- **架构**：Controller → Service → Mapper 分层，RESTful 接口，前后端分离
- **规模**：11 张数据表、19 个用例、43 个接口、约 90 个后端 Java 文件、约 35 个前端源文件

---

## 1. 快速开始

### 1.1 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+（本项目使用 21） | Spring Boot 3.x 要求 |
| Maven | 3.8+ | 构建后端；本项目已内置 `tools/apache-maven-3.9.9` |
| MySQL | 8.0+ | 字符集 `utf8mb4` |
| Node.js | 18+ | 构建前端（本项目使用 v24） |

### 1.2 初始化数据库

```bash
cd db
mysql -u root -p --default-character-set=utf8mb4 -e "source schema.sql"
mysql -u root -p --default-character-set=utf8mb4 -e "source data.sql"
```

`schema.sql` 会创建数据库 `school_ms` 及全部 10 张表；`data.sql` 写入演示数据（1 个管理员、5 名教师、26 名学生、10 门课程、63 条选课记录、7 条成绩）。

> 数据库账号密码在 `backend/src/main/resources/application.yml` 中配置，默认 `root` / `123456`。生产环境请改用环境变量注入。

### 1.3 启动后端

```bash
cd backend
# 使用项目内置 Maven（推荐，仓库已本地化到项目内）
..\tools\apache-maven-3.9.9\bin\mvn.cmd -s ..\tools\maven-settings.xml package -DskipTests
java -jar target/edu-system.jar
```

服务启动在 <http://localhost:8080>。

### 1.4 启动前端（开发模式）

```bash
cd frontend
npm install
npm run dev
```

访问 <http://localhost:5173>，Vite 会将 `/api` 请求代理到 `http://localhost:8080`。

> **请用 `localhost` 而不是 `127.0.0.1` 访问开发服务器。** 本机 `localhost` 解析为 IPv6 的
> `::1`，Vite 默认只监听该地址，因此 `http://127.0.0.1:5173` 会被拒绝连接；而后端 8080
> 监听的是 IPv4 `0.0.0.0`，两者绑定行为不同。
>
> 若在**禁止 Node 派生子进程**的受限沙箱中运行，需改用
> `npm install --ignore-scripts` 配合 `npm run dev:sandbox`（开发）或 `npm run build:sandbox`（构建），
> 详见 `frontend/README.md` 末尾的「受限沙箱环境的构建适配」。

### 1.5 单端口运行（演示 / 部署模式）

一键构建（前端构建 → 产物并入后端静态目录 → 后端打包）：

```powershell
# 在项目根目录的 PowerShell 窗口中执行
& scripts\build.ps1
```

> 脚本会**自动查找 Java 17+ 的 JDK**（PATH 上的 `java` 可能是 JDK 8，直接用会报
> `UnsupportedClassVersionError`）。找不到时会给出明确的设置指引。
> 脚本以 UTF-8 BOM 保存，Windows PowerShell 5.1 与 PowerShell 7 均可正常执行。

或手动执行等价的四步：

```bash
cd frontend && npm run build
# 将 frontend/dist/* 复制到 backend/src/main/resources/static/
cd ../backend && ..\tools\apache-maven-3.9.9\bin\mvn.cmd -s ..\tools\maven-settings.xml package -DskipTests
java -jar target/edu-system.jar
```

浏览器只需访问 <http://localhost:8080>。前端 SPA 由 `SpaWebFilter` 处理路由回退，
刷新 `/student/courses`、`/admin/users` 等任意前端路由均能正确加载。

---

## 2. 演示账号

所有种子账号初始密码均为 **`123456`**（BCrypt 散列存储）。

| 角色 | 用户名 | 密码 | 登录后进入 |
| --- | --- | --- | --- |
| 管理员 | `admin` | `123456` | 用户管理 |
| 教师 | `t001` ~ `t005` | `123456` | 我的课程 |
| 学生 | `2024001` ~ `2024018`、`2025001` ~ `2025008` | `123456` | 选课大厅 |

### 2.1 同时以多个角色登录

登录令牌存放在 **`sessionStorage`**（按标签页隔离），因此**同一个浏览器可以同时开多个标签页、分别以不同角色登录**，互不干扰：

```
标签页 1 → admin      （管理员）
标签页 2 → t001       （教师）
标签页 3 → 2024001    （学生）
```

三个标签页可以同时操作，各自的菜单、数据与权限完全独立。

> **为什么不是 `localStorage`**：`localStorage` 在同一浏览器的所有标签页间共享，而请求拦截器每次请求都会重新读取令牌。
> 若用它存令牌，标签页 2 登录后会把标签页 1 的令牌覆盖掉，回到标签页 1 就会发现自己"变成了"标签页 2 的用户 ——
> 表现为"系统不能同时多个用户登录"。后端的 JWT 是无状态的，本身完全支持多用户并存
> （并发验证见 `tools/debug-multi-user.mjs`），问题只出在令牌的存放位置。
>
> **代价**：关闭标签页后登录状态不再保留（**刷新不受影响**）。
> 若更希望"关闭浏览器后仍保持登录"，把 `frontend/src/utils/authStorage.js` 中 `storage()` 的返回值
> 由 `window.sessionStorage` 改为 `window.localStorage` 即可，但需接受"同一浏览器只能有一个用户"的限制。

---

## 3. 目录结构

```
高校教务系统/
├── 1 开发计划.docx              ← 原始文档（未改动）
├── 2 需求规格说明书.docx         ← 原始文档（未改动）
├── 3 软件设计说明书(1).docx      ← 原始文档（未改动）
├── docs/
│   ├── 需求分析与可行性分析报告.md    ← 需求分析与五维可行性论证
│   ├── 接口设计.md                  ← 前后端并行开发的冻结契约
│   ├── 测试报告.md                  ← 端到端验证结果（81 项断言）
│   ├── diagrams/                    ← 13 张 PlantUML 图（.puml 源文件 + .png）
│   └── exported/                    ← 三份填写完整的交付文档
│       ├── 1 项目开发计划.docx
│       ├── 2 需求规格说明书.docx
│       └── 3 软件设计说明书.docx
├── backend/                     ← Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/school/edu/
│       ├── common/    统一响应体、分页、业务异常、全局异常处理、枚举文案
│       ├── config/    Security / MyBatis-Plus / Jackson / SPA 路由回退
│       ├── security/  JWT 工具、登录主体、认证过滤器、当前用户工具
│       ├── entity/    10 个实体（对应 10 张表）
│       ├── mapper/    MyBatis-Plus Mapper + 自定义 SQL（原子占座等）
│       ├── dto/ vo/   请求体与视图对象
│       ├── service/   业务服务层（含 WarningService / WarningMailService / WarningNotifyService）
│       └── controller/ REST 控制器（含 AdminWarningController）
├── frontend/                    ← Vue 3 前端
├── db/
│   ├── schema.sql               ← 建库建表脚本
│   └── data.sql                 ← 演示数据
└── tools/                       ← 构建与验证工具（非交付代码）
    ├── apache-maven-3.9.9/      ← 项目内置 Maven
    ├── maven-settings.xml       ← 本地仓库指向项目内
    ├── api-test.mjs             ← 端到端接口测试（81 项断言）
    ├── docs-builder/            ← 三份 docx 的生成脚本
    └── HashGen.java             ← BCrypt 散列生成工具
```

---

## 4. 核心设计要点

### 4.1 选课防超卖（本系统最关键的技术设计）

朴素做法「先查余量、再更新」在并发下会超卖。本系统将判断与扣减合并为**一条带条件的原子 UPDATE**：

```sql
UPDATE course
   SET selected_count = selected_count + 1
 WHERE id = #{courseId}
   AND selected_count < capacity
```

以返回的受影响行数判定成败：`1` 表示抢到座位，`0` 表示名额已满。判断与扣减在同一语句内由数据库行锁保证原子性，不存在竞态窗口。配合三重保障：

1. **事务回滚兜底** —— 整个选课流程在 `@Transactional` 中，落表失败则座位自动还原；
2. **唯一索引防重** —— `course_selection(student_id, course_id)` 唯一索引，数据库层杜绝重复选课；
3. **退课复用记录** —— 退课只把状态置为 `DROPPED`，重选时复用原行，既保留唯一约束又支持重选。

> 实测：12 名学生并发抢选容量为 3 的课程，恰好 3 人成功、9 人收到「该课程名额已满」，课程已选人数精确等于 3，**零超卖**。

### 4.2 选课周期控制

选课与退课是否可用，由 `semester` 表中当前学期（`is_current = 1`）的 `select_start_time` / `select_end_time` 与服务器时间比较决定。校验在**服务端**执行，不信任前端传参。

### 4.3 权限与数据隔离

- JWT 无状态认证，每次请求回查账号状态，**停用账号后已签发的令牌立即失效**；
- 路径级（`/api/admin/**`、`/api/teacher/**`、`/api/student/**`）+ 方法级（`@PreAuthorize`）双重鉴权；
- 所有「本人」接口从 JWT 解析身份，**不接受前端传入的用户标识**；教师操作课程前二次校验课程归属。

### 4.4 成绩计算

```
总评成绩 = 平时成绩 × 40% + 期末成绩 × 60%     （权重可由 app.score.usual-weight 配置）
绩点按五级制换算：≥90 → 4.0，85~89 → 3.7，……，<60 → 0.0
```

成绩一旦录入，该课程即不可退课（业务规则 BR-04）。

### 4.5 学业红线预警

教务处依据成绩自动判定每名学生的学业风险等级，三档提示：**黄色（学业关注）→ 橙色（学业预警）→ 红色（学业红线）**。

两条规则，**取最严的一条**作为最终等级：

| 规则 | 指标 | 触发方向 | 红色 / 橙色 / 黄色阈值 |
| --- | --- | --- | --- |
| ① | 累计不及格学分 | ≥ 阈值 | 10 / 6 / 3 |
| ② | 单学期最多不及格门数 | ≥ 阈值 | 4 / 3 / 2 |

阈值全部外置于 `application.yml` 的 `app.warning`，可按学校规定调整，无需改代码。

**关键设计：预警实时计算、不落库。** 成绩一经修改，下一次请求即反映新的预警等级，无需定时任务或数据同步，也不存在预警与成绩不一致的可能。指标由一条「派生表 LEFT JOIN」聚合 SQL 一次性算出全部学生，避免 N+1 查询。

**规则取舍**：预警只采用上述两条「不及格」指标，未引入加权平均绩点规则 —— 绩点受选课门数与学分权重影响较大，选课数量少的学生容易因个别低分被放大而误报，用「累计不及格学分 + 单学期不及格门数」更能稳定反映学业风险。若学校另有规定，可在 `app.warning` 下新增阈值并扩展一条规则（`WarningLevel.max` 取最严的机制无需改动）。

**展示位置**：学生端工作台与「我的成绩」顶部醒目告警条（含两条规则逐条判定与处置建议）；管理端「学业预警」菜单（统计卡片 + 阈值表 + 可筛选名单）；教师端成绩录入名单中的预警标记。

**预警仅作提示，不阻断任何已有功能** —— 红色预警学生仍可正常选课、退课。

### 4.6 学业预警邮件通知

管理员可在预警名单中勾选学生（或「一键通知当前筛选」），系统向学生**账号中登记的邮箱**发送预警通知邮件，并逐条记录发送结果。

**邮件内容**（管理员发送前可预览）：主题 `【学业预警】学业情况通知 —— 姓名（学号）`，正文含五部分 —— 预警等级、学业指标、两条规则逐条判定（当前值/阈值/是否触发）、处置建议、说明与查询入口。同时提供**纯文本**与 **HTML** 两个版本（`multipart/alternative`），由邮件客户端选择显示。

**两种发送模式**，由 `app.mail.enabled` 控制：

| 模式 | 行为 |
| --- | --- |
| `true` | 真实投递到 SMTP 服务器 |
| `false`（默认） | **模拟发送**：照常生成邮件内容并写入发送记录，但不连网络，正文输出到服务端日志 |

> 邮件发送依赖真实 SMTP 授权码。模拟模式让这个功能在**没有邮箱凭据的环境下也能完整演示与自动化测试**；配置好邮箱后只改一个开关即可真实投递，业务代码零改动。

### 4.6.1 改为真实发送（三步）

**第 1 步：拿到 SMTP 授权码**（不是邮箱登录密码）

| 邮箱 | 开启位置 | 说明 |
| --- | --- | --- |
| QQ 邮箱 | 设置 → 账户 → POP3/IMAP/SMTP 服务 → 开启 **SMTP 服务** | 会生成一串 16 位授权码 |
| 163 邮箱 | 设置 → POP3/SMTP/IMAP → 开启 **SMTP 服务** | 需先用手机发短信验证，生成授权码 |
| 校内邮箱 | 咨询学校信息中心 | 部分校内邮箱无需认证 |

**第 2 步：用脚本启动**（推荐，无需手改配置文件）

```powershell
# QQ 邮箱（端口 465 / SSL）
& scripts\start-backend.ps1 -MailHost smtp.qq.com -MailPort 465 -MailSsl `
    -MailUser 你的QQ号@qq.com -MailPassword 你的16位授权码

# 163 邮箱（端口 465 / SSL）
& scripts\start-backend.ps1 -MailHost smtp.163.com -MailPort 465 -MailSsl `
    -MailUser 你的账号@163.com -MailPassword 你的授权码

# Gmail（端口 587 / STARTTLS）
& scripts\start-backend.ps1 -MailHost smtp.gmail.com -MailPort 587 -MailStartTls `
    -MailUser 你的账号@gmail.com -MailPassword 应用专用密码
```

端口 465 自动用 SSL、587 自动用 STARTTLS、其余（如 25）不加密，**不需要你手工指定加密方式**。

也可以不用脚本，直接改 `backend/src/main/resources/application.yml` 里的默认值，
或自行设置 `APP_MAIL_ENABLED=true`、`MAIL_HOST`、`MAIL_PORT`、`MAIL_USERNAME`、
`MAIL_PASSWORD`、`MAIL_SMTP_SSL` 这些环境变量。

**第 3 步：点「邮件自检」验证**

用 `admin` 登录 → 「学业预警」→ 右上角 **邮件自检**。系统会向管理员本人邮箱发一封测试邮件，
并弹窗显示：

- 当前是真实发送还是模拟发送
- 实际使用的 SMTP 服务器 / 账号 / 发件人
- 发送成功或**失败的具体原因 + 针对性提示**

失败时常见的三种情况及提示：

| 原始错误 | 系统给出的提示 | 真实原因 |
| --- | --- | --- |
| `535 Authentication failed` | 授权码问题 | 填了登录密码而不是 SMTP 授权码；或没开启 SMTP 服务 |
| `550 From header is invalid` | 发件人被拒 | `app.mail.from` 与登录账号不一致 —— **本项目已自动处理**：`from` 留空时会自动取登录账号 |
| `Connection refused` / `timed out` | 连接失败 | 端口与加密方式不匹配（465 用 SSL、587 用 STARTTLS），或防火墙拦截 |

自检通过后，回到预警名单勾选学生点「发送邮件通知」即可真实投递。

> **关于发件人（重要）**：QQ、163 等服务商会拒绝「发件人与登录账号不一致」的邮件。
> 因此 `app.mail.from` 默认留空，系统会自动使用 `spring.mail.username` 作为发件人。
> 只有当学校提供了独立发信地址（如校内自建 SMTP）时才需要显式指定 `-MailFrom`。

### 4.6.1.1 凭据放在哪里（不要写进 application.yml）

SMTP 授权码**不要**写进 `backend/src/main/resources/application.yml` —— 那个文件受版本控制，
会随代码一起提交、随文档一起分发。正确做法是使用被 git 忽略的本地凭据文件：

```powershell
Copy-Item config\mail.local.ps1.example config\mail.local.ps1
# 然后编辑 config\mail.local.ps1 填入真实值，再直接运行脚本即可（无需任何参数）
& scripts\start-backend.ps1
```

`config/mail.local.ps1` 已在 `.gitignore` 中，不会进入版本库。

### 4.6.1.2 演示时的注意：种子数据的邮箱是假的

`db/data.sql` 里的学生邮箱是 `2024xxxx@student.school.edu.cn`，而**`school.edu.cn` 这个域名并不存在**。
后果是：

- 向这些地址发送时，**QQ 的 SMTP 服务器会接受邮件并返回成功**，系统显示「已发送」；
- 但邮件随后会被**异步退回**（退信会回到发件人邮箱），学生**收不到**。

这不是系统缺陷 —— SMTP 协议本身不提供同步的投递保证，服务器「接受」不等于「已送达」。
只是演示时容易误以为发送成功了。

**要真正演示学生收到预警邮件**，需要给学生填真实邮箱，三种办法：

1. 用 `admin` 登录 → 用户管理 → 编辑学生 → 填写真实邮箱（推荐）
2. 学生本人登录 → 个人中心 → 邮箱设置
3. 直接改 `db/data.sql` 里的邮箱后重置数据库

> **唯一性限制**：邮箱全局唯一（出于防止学生收到他人成绩的考虑，见 §4.7）。
> 因此**不能把多个学生都指向同一个邮箱**。若只是想让演示时所有通知都进自己的一个邮箱，
> 可临时注释掉 `AuthService.assertEmailFree` 与 `UserService.assertEmailFree` 中的校验
> （**仅供演示，不要用于实际部署**）。

### 4.6.2 不用真实邮箱也想验证发送链路

项目自带一个零依赖的本地 SMTP 接收器，可以在没有邮箱授权码的情况下验证**真实发送**链路：

```bash
# 终端 1：启动本地 SMTP 接收器（监听 127.0.0.1:2525）
node tools/smtp-sink.mjs

# 终端 2：以真实发送模式启动（指向本地接收器，无需加密）
& scripts\start-backend.ps1 -MailHost 127.0.0.1 -MailPort 2525 `
    -MailUser test@example.com -MailPassword dummy
```

收到的邮件会写入 `tools/smtp-inbox/*.eml`（原始报文）与 `*.json`（信封信息）。

**发送结果**四种状态：`SENT` 已发送 / `SIMULATED` 模拟发送 / `FAILED` 发送失败（记录失败原因）/ `SKIPPED` 已跳过。其中「账号未登记邮箱」会**写入历史**（教务处可据此补录邮箱），而「未触发预警」「学生不存在」属管理员误选，只在响应中反馈。

**设计要点**：发送服务**不加 `@Transactional`** —— 邮件是外部网络 I/O，不应长时间占用数据库事务；每条记录独立落库，保证「已发出的邮件一定有记录，失败的邮件也留有原因」。单人失败不影响批次内其他学生。

### 4.7 学生自助维护邮箱

学生（以及教师、管理员）可在「个人中心 → 邮箱设置」自行修改邮箱，无需联系管理员。未登记邮箱时会显示醒目提示。

**邮箱为什么必须唯一**：预警邮件正文包含成绩与不及格明细。若允许重复，学生只要把邮箱填成同学地址，就能收到本应发给同学的预警邮件。因此自服务修改与管理端编辑都校验唯一性（排除自身），失败时提示「该邮箱已被其他账号使用，请更换」且**不产生半修改状态**。

> 这是**部分缓解**：它挡不住使用另一个未被占用的地址冒领。彻底解决需要邮箱验证流程（发送确认链接、点击后生效），本期未实现，已列为扩展点。

---

## 5. 验证

### 5.1 端到端接口测试

覆盖认证、鉴权、管理员 CRUD、选课退课、成绩录入、并发防超卖：

```powershell
# 需先启动后端，并使用干净的数据基线
& scripts\reset-db.ps1
node tools/api-test.mjs
```

结果：**81 项断言全部通过**。

### 5.2 单端口集成验证

确认 SPA 页面、前端路由回退、静态资源与 REST 接口由同一个端口统一提供：

```bash
node tools/verify-integration.mjs
```

结果：**17 项断言全部通过**（SPA 首页 6 项、路由回退 6 项、静态资源 2 项、同端口接口 3 项）。

### 5.3 全部测试脚本

| 脚本 | 覆盖内容 | 断言数 |
| --- | --- | ---: |
| `tools/api-test.mjs` | 认证、鉴权、管理员 CRUD、选课退课、成绩录入、并发防超卖 | 83 |
| `tools/verify-integration.mjs` | 单端口集成：SPA、路由回退、静态资源、同端口 API | 17 |
| `tools/debug-frontend-api.mjs` | 前端 48 个 API 调用逐个真跑 + 字段形状校验 | 81 |
| `tools/debug-edge-cases.mjs` | 分页越界、非法参数、注入样本、特殊字符、令牌篡改、越权、周期边界 | 36 |
| `tools/debug-devserver.mjs` | 开发服务器：SPA 加载、SFC 编译、依赖解析、`/api` 代理 | 22 |
| `tools/debug-warning.mjs` | 学业红线预警：阈值、名单、规则明细、筛选分页、成绩联动 | 63 |
| `tools/debug-warning-frontend.mjs` | 预警前端改动：模块编译、导出、路由、邮件 UI、构建产物 | 40 |
| `tools/debug-warning-mail.mjs` | 邮件通知：真实发送 / 模拟发送（双模式，自动识别） | 67 / 47 |
| `tools/debug-profile-email.mjs` | 本人邮箱维护：唯一性、格式校验、幂等、权限、与邮件联动 | 31 |
| `tools/debug-multi-user.mjs` | 多用户并发：多角色同时登录、令牌隔离、并发越权拦截、前端存储隔离 | 23 |
| `tools/debug-mail-config.mjs` | 邮件自检：配置摘要、发件人回退、自检接口、失败提示（双模式） | 16 / 19 |
| — | **合计** | **484 / 467（0 失败）** |

> 各脚本会修改数据库状态，**每次执行前请先重置数据库**：`& scripts\reset-db.ps1`。

详见 `docs/测试报告.md`。

---

## 6. 文档索引

| 文档 | 位置 | 内容 |
| --- | --- | --- |
| 需求分析与可行性分析报告 | `docs/需求分析与可行性分析报告.md` | 需求梳理、用例规约、非功能需求、技术/经济/操作/法律/进度可行性、风险 |
| 项目开发计划 | `docs/exported/1 项目开发计划.docx` | 工作内容、产品、运行环境、验收标准、任务分解、进度甘特图、关键问题、交付期限 |
| 需求规格说明书 | `docs/exported/2 需求规格说明书.docx` | 三端用例图与用例规约（含 UC-17 学业预警）、用例清单、业务规则、非功能需求、数据需求 |
| 软件设计说明书 | `docs/exported/3 软件设计说明书.docx` | 系统架构、总体功能、UML 类图与时序图（含 3.4 学业红线预警与邮件通知模块设计）、E-R 图、11 张物理表结构、接口设计、出错处理 |
| 接口设计说明书 | `docs/接口设计.md` | 全部 43 个接口的字段级契约（含 §7 学业红线预警与邮件通知） |
| 测试报告 | `docs/测试报告.md` | 测试范围、用例与 444 / 464 项断言结果 |

---

## 7. 安全提示

本项目为教学/演示性质，交付时包含以下需在生产环境调整的配置：

- `app.jwt.secret` 为演示密钥，生产环境必须替换为随机 256 位以上密钥并通过环境变量注入；
- 数据库账号密码以明文写在 `application.yml`，生产环境应改为环境变量或配置中心；
- CORS 白名单放行了 `localhost`，生产环境应改为实际前端域名；
- 演示数据中所有账号密码相同，生产环境应强制首次登录修改密码（字段 `must_change_password` 已预留）。
