# 高校教务系统 · 前端

Vue 3（`<script setup>` SFC） + Vite + Vue Router 4 + Pinia + Element Plus + Axios。
接口严格按 `../docs/接口设计.md`（API Contract V1.0）实现，未新增或改名任何端点与字段。

## 目录结构

```
frontend/
├── index.html                    # 入口 HTML（lang="zh-CN"，title=高校教务系统）
├── vite.config.mjs               # 标准配置：端口 5173，/api 代理到 http://localhost:8080
├── vite.config.sandbox.mjs       # 受限沙箱专用构建配置（正常环境无需使用）
├── .npmrc                        # 固定 npm 缓存到工作区内
├── tools/prepare-build-env.mjs   # 受限沙箱的构建环境适配脚本（正常环境无需使用）
└── src/
    ├── main.js                   # Pinia / Router / Element Plus(zh-cn) / 全量图标
    ├── App.vue                   # <router-view />
    ├── api/                      # request.js + 各领域接口模块
    ├── components/PageTable.vue  # 统一列表 + 分页容器
    ├── layouts/MainLayout.vue    # 顶栏 + 按角色过滤的侧边菜单
    ├── router/index.js           # 路由 + token/角色守卫
    ├── stores/auth.js            # token / user / 登录登出 / 角色 getter
    ├── styles/index.css          # 全局样式
    ├── utils/                    # dict.js（字典与格式化）、authStorage.js
    └── views/                    # 页面
```

## 安装与构建（正常环境）

```bash
npm install         # 安装依赖
npm run build       # 生产构建，产出 dist/index.html
npm run dev         # 本地开发，http://localhost:5173
```

> 缓存目录已由 `.npmrc` 固定为项目内的 `../.cache/npm`，无需再传 `--cache`
> 或设置 `npm_config_cache`。

## 演示账号

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `123456` |
| 教师 | `t001` ~ `t005` | `123456` |
| 学生 | `2024001` ~ `2024018`、`2025001` ~ `2025008` | `123456` |

## 页面与路由

| 路由 | 页面 | 角色 |
| --- | --- | --- |
| `/login` | 登录 | 匿名 |
| `/dashboard` | 工作台（`/api/dashboard/summary`） | 全部 |
| `/profile` | 个人中心 + 修改密码 | 全部 |
| `/admin/users` | 用户管理（学生/教师/管理员三个页签） | ADMIN |
| `/admin/courses` | 课程管理 | ADMIN |
| `/admin/org` | 组织架构（年级/专业/班级） | ADMIN |
| `/admin/semesters` | 学期与选课周期 | ADMIN |
| `/teacher/courses` | 我的课程 | TEACHER |
| `/teacher/courses/:courseId/students` | 学生名单与成绩录入 | TEACHER |
| `/student/courses` | 选课大厅 | STUDENT |
| `/student/my-courses` | 我的课表（含周课表网格） | STUDENT |
| `/student/scores` | 我的成绩 | STUDENT |

登录后按角色跳转：ADMIN → `/admin/users`，TEACHER → `/teacher/courses`，
STUDENT → `/student/courses`。

---

## 附：受限沙箱环境的构建适配

> 本节仅适用于**禁止 Node 以 piped stdio 派生子进程**的受限沙箱（如 DSH 文件沙箱）。
> 在普通开发机上请忽略本节，直接使用 `npm install` + `npm run build` 即可。

在这样的沙箱中，任何 `child_process.spawn` 都会抛 `EPERM: spawn`（PowerShell 自身
不受影响，被拦截的是 Node 创建子进程管道）。这会影响三条与业务无关的链路：

| 链路 | 现象 | 适配方式 |
| --- | --- | --- |
| npm 安装后 rebuild 阶段执行 `esbuild` / `vue-demi` 的 postinstall | `npm install` 报 `EPERM spawn` | 安装时加 `--ignore-scripts`；两者产物均不影响本项目构建 |
| Vite 的 Windows 专用 realpath 优化执行 `net use` 探测网络驱动器 | 配置解析阶段 `EPERM spawn` | `tools/prepare-build-env.mjs` 将其改为容错调用，被拦截时退回 `fs.realpathSync.native`（即 Vite 探测失败时的默认分支） |
| esbuild 常驻服务进程（`vite:define`、`vite:esbuild*`、压缩） | 构建阶段 `EPERM spawn` | 由 `vite.config.sandbox.mjs` 完全绕开，见下 |

沙箱下构建命令：

```bash
npm install --ignore-scripts
npm run build:sandbox
```

该脚本等价于：

```bash
node tools/prepare-build-env.mjs && vite build --config vite.config.sandbox.mjs --configLoader native
```

其中：

- `--configLoader native` 跳过 esbuild 配置打包；
- `vite.config.sandbox.mjs` 设置 `esbuild: false`（关闭 `vite:esbuild` 与 `vite:esbuild-transpile`），
  JS 压缩改用进程内的 `terser`，`cssMinify` 关闭（不影响功能与语义）；
- `tools/prepare-build-env.mjs` 对 `node_modules` 内的 Vite 打两处**等价**补丁，并额外为
  `replaceDefine` 增加纯 JS 正则回退（esbuild 不可用时替换导入期字面量，语义与
  esbuild `define` 一致）。脚本是**幂等**的，重复执行不会叠加修改。

**代价说明**：沙箱构建产出未做 CSS 压缩，`dist/assets/index-*.css` 因此为 363 KB
（gzip 49 KB）。在正常环境下执行 `npm run build` 会得到压缩后的产物。
