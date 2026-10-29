// 边界与异常调试：覆盖前端交互中可能触发的极端输入与非法状态。
const BASE = process.env.BASE ?? 'http://127.0.0.1:8080';

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

async function call(method, path, { token, body, raw } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = raw ? raw : `Bearer ${token}`;
  const res = await fetch(BASE + path, {
    method, headers,
    body: body === undefined ? undefined : (typeof body === 'string' ? body : JSON.stringify(body)),
  });
  let json = null;
  try { json = await res.json(); } catch { json = { code: -1, message: 'non-JSON' }; }
  return { status: res.status, ...json };
}

const login = (u, p = '123456') => call('POST', '/api/auth/login', { body: { username: u, password: p } });
const section = (t) => console.log(`\n=== ${t} ===`);

const adminToken = (await login('admin')).data?.token;
const studentToken = (await login('2024001')).data?.token;
const teacherToken = (await login('t001')).data?.token;

section('1. 分页边界');
const p999 = await call('GET', '/api/admin/users?role=STUDENT&page=999&size=10', { token: adminToken });
check('超出范围的页码返回空列表而非报错',
  p999.code === 200 && Array.isArray(p999.data?.records) && p999.data.records.length === 0,
  `code=${p999.code} records=${p999.data?.records?.length}`);
check('  同时仍返回正确 total', p999.data?.total === 26, `total=${p999.data?.total}`);

const p1 = await call('GET', '/api/admin/users?role=STUDENT&page=1&size=1000', { token: adminToken });
check('超大 size 不报错（返回全部 26 条）',
  p1.code === 200 && p1.data?.records?.length === 26, `len=${p1.data?.records?.length}`);

const pNeg = await call('GET', '/api/admin/users?role=STUDENT&page=-1&size=10', { token: adminToken });
check('负数页码被安全处理（不抛 500）', pNeg.code === 200 || pNeg.code === 400,
  `code=${pNeg.code} msg=${pNeg.message}`);

const pZero = await call('GET', '/api/admin/users?role=STUDENT&page=1&size=0', { token: adminToken });
check('size=0 被安全处理（不抛 500）', pZero.code === 200 || pZero.code === 400,
  `code=${pZero.code} msg=${pZero.message}`);

section('2. 不存在的资源');
const badCourse = await call('GET', '/api/teacher/courses/99999/students', { token: teacherToken });
check('教师查询不存在课程的名单 → 404', badCourse.code === 404, `code=${badCourse.code} msg=${badCourse.message}`);

const badStat = await call('GET', '/api/teacher/courses/99999/statistics', { token: teacherToken });
check('不存在课程的统计 → 404', badStat.code === 404, `code=${badStat.code}`);

const selBad = await call('POST', '/api/student/selections', { token: studentToken, body: { courseId: 99999 } });
check('选不存在的课程 → 404', selBad.code === 404, `code=${selBad.code} msg=${selBad.message}`);

// 场景 A：课程本身不存在 → 404（REST 语义：资源不存在）
const dropNoCourse = await call('DELETE', '/api/student/selections/99999', { token: studentToken });
check('退完全不存在的课程 → 404 课程不存在',
  dropNoCourse.code === 404 && dropNoCourse.message === '课程不存在',
  `code=${dropNoCourse.code} msg=${dropNoCourse.message}`);

// 场景 B：课程存在但该生没有有效选课记录 → 400（UC-04 异常流 4a）
// 注意：退课接口先校验「必修课不可退课」，故必须挑一门非必修课；
// 课程 9（EL201 数字电子技术，限选）在种子数据中只有软工2401 选课，计科2401 的 2024001 无记录
const dropNotSelected = await call('DELETE', '/api/student/selections/9', { token: studentToken });
check('退存在但未选的课程 → 400 退课失败，未找到有效选课记录',
  dropNotSelected.code === 400 && dropNotSelected.message === '退课失败，未找到有效选课记录',
  `code=${dropNotSelected.code} msg=${dropNotSelected.message}`);

const delBad = await call('DELETE', '/api/admin/users/99999', { token: adminToken });
check('删除不存在用户 → 404', delBad.code === 404, `code=${delBad.code} msg=${delBad.message}`);

const emptySem = await call('GET', '/api/admin/courses?semesterId=99999&page=1&size=10', { token: adminToken });
check('不存在学期下课程为空', emptySem.code === 200 && emptySem.data?.total === 0,
  `total=${emptySem.data?.total}`);

section('3. 非法参数校验');
const noBody = await call('POST', '/api/student/selections', { token: studentToken, body: {} });
check('选课缺少 courseId → 400', noBody.code === 400, `code=${noBody.code} msg=${noBody.message}`);

// 以下四类都是「客户端错误」，必须返回准确的 HTTP 状态码，并且**不能**打 ERROR 堆栈。
// 回归背景：曾因缺少对应 @ExceptionHandler，全部落到兜底分支，日志刷 ERROR 完整堆栈 + 返回 500。
const badJson = await call('POST', '/api/student/selections', { token: studentToken, body: '{bad json' });
check('请求体非法 JSON → HTTP 400 / code 400（不泄露堆栈）',
  badJson.status === 400 && badJson.code === 400 && !String(badJson.message).includes('Exception'),
  `status=${badJson.status} code=${badJson.code} msg=${badJson.message}`);

// DELETE /{id} 命中路由，但 "not-a-number" 无法转成 Long → 类型不匹配（转换发生在删除之前，不会误删）
const badType = await call('DELETE', '/api/admin/users/not-a-number', { token: adminToken });
check('路径变量类型错误 → HTTP 400 / code 400',
  badType.status === 400 && badType.code === 400,
  `status=${badType.status} code=${badType.code} msg=${badType.message}`);

const badMethod = await call('DELETE', '/api/auth/login');
check('请求方法不支持 → HTTP 405 / code 405',
  badMethod.status === 405 && badMethod.code === 405,
  `status=${badMethod.status} code=${badMethod.code} msg=${badMethod.message}`);

const noEndpoint = await call('GET', '/api/no-such-endpoint', { token: adminToken });
check('不存在的接口 → HTTP 404 / code 404',
  noEndpoint.status === 404 && noEndpoint.code === 404,
  `status=${noEndpoint.status} code=${noEndpoint.code} msg=${noEndpoint.message}`);

// 课程申报重构后：建课走教师端（POST /api/teacher/courses），管理员不再有建课接口
const badCap = await call('POST', '/api/teacher/courses', {
  token: teacherToken,
  body: { courseNo: 'EDGE01', name: '容量非法', credit: 2, hours: 32, courseType: '选修', semesterId: 1, capacity: 0 },
});
check('容量为 0 → 400', badCap.code === 400, `code=${badCap.code} msg=${badCap.message}`);

const badHours = await call('POST', '/api/teacher/courses', {
  token: teacherToken,
  body: { courseNo: 'EDGE02', name: '学时非法', credit: 2, hours: 0, courseType: '选修', semesterId: 1, capacity: 10 },
});
check('学时为 0 → 400', badHours.code === 400, `code=${badHours.code} msg=${badHours.message}`);

// 任课教师改由服务端按登录身份绑定：请求体里伪造的 teacherId 必须被忽略
const fakeTeacher = await call('POST', '/api/teacher/courses', {
  token: teacherToken,
  body: { courseNo: 'EDGE03', name: '伪造任课教师', credit: 2, hours: 32, courseType: '选修', teacherId: 99999, semesterId: 1, capacity: 10 },
});
check('伪造的 teacherId 被忽略，课程归属登录教师本人',
  fakeTeacher.code === 200 && fakeTeacher.data?.teacherId === 1,
  `code=${fakeTeacher.code} teacherId=${fakeTeacher.data?.teacherId}`);
if (fakeTeacher.data?.id) {
  const cleanup = await call('DELETE', `/api/teacher/courses/${fakeTeacher.data.id}`, { token: teacherToken });
  check('  待审核课程可由申报教师本人删除（清理）', cleanup.code === 200, `code=${cleanup.code} msg=${cleanup.message}`);
}

const dupUn = await call('POST', '/api/admin/users', {
  token: adminToken,
  body: { username: 'admin', realName: '重复', role: 'ADMIN' },
});
check('重复用户名 → 400', dupUn.code === 400, `code=${dupUn.code} msg=${dupUn.message}`);

const noStudentNo = await call('POST', '/api/admin/users', {
  token: adminToken,
  body: { username: 'nostuno1', realName: '缺学号', role: 'STUDENT' },
});
check('创建学生缺学号 → 400', noStudentNo.code === 400, `code=${noStudentNo.code} msg=${noStudentNo.message}`);

section('4. 超长与特殊字符输入');
const longKw = 'x'.repeat(500);
const longKwRes = await call('GET', `/api/admin/users?role=STUDENT&keyword=${longKw}&page=1&size=10`, { token: adminToken });
check('500 字符关键字不报错', longKwRes.code === 200, `code=${longKwRes.code} msg=${longKwRes.message}`);

const specialKw = encodeURIComponent("' OR 1=1 --");
const injRes = await call('GET', `/api/admin/users?role=STUDENT&keyword=${specialKw}&page=1&size=10`, { token: adminToken });
check('SQL 注入样本被当作普通字符串（参数绑定生效）',
  injRes.code === 200 && injRes.data?.total === 0, `code=${injRes.code} total=${injRes.data?.total}`);

const ts = Date.now().toString().slice(-6);
const xssName = '<script>alert(1)</script>';
const xssUser = await call('POST', '/api/admin/users', {
  token: adminToken,
  body: { username: `xss${ts}`, realName: xssName, role: 'ADMIN', status: 1 },
});
check('特殊字符姓名可正常创建', xssUser.code === 200, JSON.stringify(xssUser).slice(0, 140));
check('  原样返回（转义由前端负责）', xssUser.data?.realName === xssName,
  `realName=${JSON.stringify(xssUser.data?.realName)}`);
if (xssUser.data?.id) await call('DELETE', `/api/admin/users/${xssUser.data.id}`, { token: adminToken });

const uniName = '测试·𠮷𩸽 emoji🎓';
const uniUser = await call('POST', '/api/admin/users', {
  token: adminToken,
  body: { username: `uni${ts}`, realName: uniName, role: 'ADMIN', status: 1 },
});
check('生僻字与 emoji 姓名不丢字符（utf8mb4）',
  uniUser.code === 200 && uniUser.data?.realName === uniName,
  `realName=${JSON.stringify(uniUser.data?.realName)}`);
if (uniUser.data?.id) await call('DELETE', `/api/admin/users/${uniUser.data.id}`, { token: adminToken });

section('5. 令牌异常');
const noTok = await call('GET', '/api/auth/me');
check('无令牌 → 401', noTok.code === 401, `code=${noTok.code}`);

const garbage = await call('GET', '/api/auth/me', { token: 'not-a-jwt' });
check('垃圾令牌 → 401', garbage.code === 401, `code=${garbage.code}`);

const tampered = adminToken ? adminToken.slice(0, -6) + 'AAAAAA' : '';
const tamperRes = await call('GET', '/api/auth/me', { token: tampered });
check('篡改签名的令牌 → 401', tamperRes.code === 401, `code=${tamperRes.code}`);

const wrongScheme = await call('GET', '/api/auth/me', { raw: adminToken });
check('缺少 Bearer 前缀 → 401', wrongScheme.code === 401, `code=${wrongScheme.code}`);

section('6. 越权与账号状态');
const cross1 = await call('GET', '/api/admin/users', { token: studentToken });
check('学生访问管理员接口 → 403', cross1.code === 403, `code=${cross1.code}`);
const cross2 = await call('GET', '/api/teacher/courses', { token: studentToken });
check('学生访问教师接口 → 403', cross2.code === 403, `code=${cross2.code}`);
const cross3 = await call('GET', '/api/student/scores', { token: teacherToken });
check('教师访问学生接口 → 403', cross3.code === 403, `code=${cross3.code}`);

const selfDisable = await call('PUT', `/api/admin/users/${(await call('GET', '/api/auth/me', { token: adminToken })).data.userId}/status`, {
  token: adminToken, body: { status: 0 },
});
check('管理员停用自己 → 400 被拒', selfDisable.code === 400, `code=${selfDisable.code} msg=${selfDisable.message}`);

const selfDelete = await call('DELETE', `/api/admin/users/${(await call('GET', '/api/auth/me', { token: adminToken })).data.userId}`, { token: adminToken });
check('管理员删除自己 → 400 被拒', selfDelete.code === 400, `code=${selfDelete.code} msg=${selfDelete.message}`);

section('7. 选课周期边界');
// 临时把选课窗口设为「刚好已结束」，确认边界判定关闭
const sem = (await call('GET', '/api/admin/semesters', { token: adminToken })).data.find((s) => s.isCurrent === 1);
await call('PUT', `/api/admin/semesters/${sem.id}`, {
  token: adminToken,
  body: {
    name: sem.name, startDate: sem.startDate, endDate: sem.endDate,
    selectStartTime: '2020-01-01 00:00:00', selectEndTime: '2020-01-01 00:00:01', isCurrent: 0,
  },
});
const closedWin = await call('GET', '/api/meta/selection-window', { token: studentToken });
check('窗口过期时 open=false 且提示非选课时段',
  closedWin.data?.open === false && closedWin.data?.message === '当前非选课时段',
  JSON.stringify(closedWin.data));
const closedSel = await call('POST', '/api/student/selections', { token: studentToken, body: { courseId: 2 } });
check('  窗口关闭时选课被拒', closedSel.code === 400 && closedSel.message === '当前非选课时段',
  `msg=${closedSel.message}`);

// 还原窗口
const now = Date.now();
const fmt = (ms) => new Date(ms).toISOString().slice(0, 19).replace('T', ' ');
await call('PUT', `/api/admin/semesters/${sem.id}`, {
  token: adminToken,
  body: {
    name: sem.name, startDate: sem.startDate, endDate: sem.endDate,
    selectStartTime: fmt(now - 7 * 86400000), selectEndTime: fmt(now + 120 * 86400000), isCurrent: 0,
  },
});
const reopened = await call('GET', '/api/meta/selection-window', { token: studentToken });
check('  窗口已还原为开放', reopened.data?.open === true, JSON.stringify(reopened.data));

console.log(`\n${'='.repeat(60)}`);
console.log(`边界与异常调试: ${pass} 通过 / ${fail} 失败  (共 ${pass + fail} 项)`);
if (failures.length) {
  console.log('\n失败项:');
  failures.forEach((f) => console.log('  - ' + f));
}
console.log('='.repeat(60));
process.exitCode = fail === 0 ? 0 : 1;
