// 校验学业预警相关的前端改动：逐个模块确认 SFC 能被 Vite 编译（模板/脚本无语法错误），
// 并确认构建产物已随 jar 一起由 8080 提供。
const DEV = process.env.DEV_ORIGIN ?? 'http://localhost:5173';
const APP = process.env.APP_ORIGIN ?? 'http://127.0.0.1:8080';

let pass = 0;
let fail = 0;
const failures = [];
const check = (name, cond, detail = '') => {
  if (cond) {
    pass++;
    console.log(`  PASS  ${name}`);
  } else {
    fail++;
    failures.push(`${name} :: ${detail}`);
    console.log(`  FAIL  ${name}  ${detail}`);
  }
};

/** 新增或修改过的模块 */
const MODULES = [
  '/src/api/warning.js',
  '/src/utils/dict.js',
  '/src/components/WarningAlert.vue',
  '/src/views/admin/WarningList.vue',
  '/src/views/student/MyScores.vue',
  '/src/views/teacher/CourseStudents.vue',
  '/src/views/common/DashboardView.vue',
  '/src/layouts/MainLayout.vue',
  '/src/router/index.js'
];

console.log(`=== 1. Vite 开发服务器编译每个改动模块 (${DEV}) ===`);
for (const path of MODULES) {
  let res;
  let text;
  try {
    res = await fetch(DEV + path);
    text = await res.text();
  } catch (e) {
    check(`GET ${path}`, false, e.message);
    continue;
  }
  const type = res.headers.get('content-type') ?? '';
  const isJs = type.includes('javascript');
  const hasError =
    text.includes('Internal server error') ||
    text.includes('Pre-transform error') ||
    text.includes('[plugin:vite:vue]') ||
    /^\s*<!DOCTYPE html>/i.test(text);
  check(`GET ${path}`, res.status === 200 && isJs && !hasError,
    `status=${res.status} type=${type}${hasError ? ' 编译报错' : ''}`);
}

console.log('\n=== 2. 关键导出确实存在于编译结果中 ===');
const dict = await (await fetch(DEV + '/src/utils/dict.js')).text();
for (const name of ['WARNING_LEVEL_OPTIONS', 'warningColor', 'warningShortLabel', 'WARNING_LEVEL_COLORS']) {
  check(`dict.js 导出 ${name}`, dict.includes(name), '未找到');
}
const api = await (await fetch(DEV + '/src/api/warning.js')).text();
for (const name of ['fetchMyWarning', 'fetchWarningList', 'fetchWarningSummary',
  'fetchWarningPreview', 'notifyWarnings', 'fetchNotifications',
  'sendTestMail', 'fetchMailConfig']) {
  check(`warning.js 导出 ${name}`, api.includes(name), '未找到');
}
const alertComp = await (await fetch(DEV + '/src/components/WarningAlert.vue')).text();
check('WarningAlert 渲染了等级标签', alertComp.includes('levelLabel'), '未找到 levelLabel');
check('WarningAlert 渲染了规则明细', alertComp.includes('data.rules'), '未找到 rules');

const listPage = await (await fetch(DEV + '/src/views/admin/WarningList.vue')).text();
check('WarningList 使用 PageTable 服务端分页', listPage.includes('PageTable') && listPage.includes('server'), '未找到');
check('WarningList 含筛选条件', ['level', 'clazzId', 'majorId', 'keyword'].every((k) => listPage.includes(k)), '');
// 注意：Vite 开发服务器返回的是编译后的渲染函数，
// 模板属性会变成对象字段（type="selection" -> type: "selection"），
// 因此这里同时接受源码写法与编译后写法。
const hasSelectionCol = /type:\s*"selection"|type="selection"/.test(listPage);
const hasSelectionHandler = listPage.includes('onSelectionChange') || listPage.includes('selection-change');
check('WarningList 含表格勾选列（批量发送）', hasSelectionCol && hasSelectionHandler,
  `col=${hasSelectionCol} handler=${hasSelectionHandler}`);
check('WarningList 调用发送接口', listPage.includes('notifyWarnings'), '未找到');
check('WarningList 含邮件预览弹窗', listPage.includes('fetchWarningPreview') && listPage.includes('previewVisible'), '未找到');
check('WarningList 含发送结果弹窗', listPage.includes('resultVisible') && listPage.includes('statusLabel'), '未找到');
check('WarningList 含发送历史弹窗', listPage.includes('fetchNotifications') && listPage.includes('historyVisible'), '未找到');
check('WarningList 含"一键通知当前筛选"', listPage.includes('handleNotifyFiltered'), '未找到');
check('WarningList 提示无邮箱学生', listPage.includes('未登记邮箱'), '未找到');
check('WarningList 提示模拟/真实发送模式', listPage.includes('mailEnabled'), '未找到');
check('WarningList 含邮件自检入口', listPage.includes('handleTestMail') && listPage.includes('邮件自检'), '未找到');
check('WarningList 自检结果展示 SMTP 配置', listPage.includes('mailTest') && listPage.includes('SMTP 服务器'), '未找到');
check('WarningList 发送前二次确认', listPage.includes('ElMessageBox.confirm'), '未找到');

const pageTable = await (await fetch(DEV + '/src/components/PageTable.vue')).text();
check('PageTable 透传原生表格属性/事件（$attrs）',
  pageTable.includes('$attrs') && pageTable.includes('inheritAttrs'), '未找到');

const roster = await (await fetch(DEV + '/src/views/teacher/CourseStudents.vue')).text();
check('CourseStudents 含预警列', roster.includes('warningLevel') && roster.includes('warningShortLabel'), '未找到');

const dash = await (await fetch(DEV + '/src/views/common/DashboardView.vue')).text();
check('DashboardView 含预警卡片', dash.includes('warningFlagged') && dash.includes('warningStudentCount'), '未找到');

const router = await (await fetch(DEV + '/src/router/index.js')).text();
check('路由已注册 /admin/warnings', router.includes('admin/warnings') && router.includes('WarningList'), '未找到');

console.log(`\n=== 3. 构建产物由 8080 提供 (${APP}) ===`);
const home = await fetch(APP + '/');
const html = await home.text();
check('GET / 返回 SPA 首页', home.status === 200 && html.includes('id="app"'), `status=${home.status}`);

const route = await fetch(APP + '/admin/warnings');
const routeText = await route.text();
check('前端路由 /admin/warnings 回退到 index.html',
  route.status === 200 && routeText.includes('id="app"'), `status=${route.status}`);

// 确认打包后的资源清单里包含预警页面的分包
const mainJs = (html.match(/src="(\/assets\/index-[^"]+\.js)"/) ?? [])[1];
let manifestText = '';
if (mainJs) {
  manifestText = await (await fetch(APP + mainJs)).text();
}
check('主包中引用了 WarningList 分包', /WarningList-[A-Za-z0-9_-]+\.js/.test(manifestText), mainJs ?? '(未找到主包)');
check('主包中引用了 WarningAlert 分包', /WarningAlert-[A-Za-z0-9_-]+\.js/.test(manifestText), '');

console.log(`\n${'='.repeat(60)}`);
console.log(`前端预警改动校验: ${pass} 通过 / ${fail} 失败  (共 ${pass + fail} 项)`);
if (failures.length) {
  console.log('\n失败项:');
  failures.forEach((f) => console.log('  - ' + f));
}
console.log('='.repeat(60));
process.exitCode = fail === 0 ? 0 : 1;
