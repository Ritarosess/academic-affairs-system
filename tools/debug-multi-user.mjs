// 多用户并发登录/并发请求验证
// 目的：确认后端为无状态 JWT，多个不同用户的令牌可以同时存在、交错请求互不干扰。
// 若此脚本全绿，则"不能同时多个用户登录"只可能出在前端令牌存储（localStorage 跨标签共享）。
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

async function call(method, path, { token, body } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(BASE + path, {
    method, headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let json = null;
  try { json = await res.json(); } catch { json = { code: -1, message: 'non-JSON' }; }
  return { status: res.status, ...json };
}
const login = (u, p = '123456') => call('POST', '/api/auth/login', { body: { username: u, password: p } });
const section = (t) => console.log(`\n=== ${t} ===`);

section('1. 三种角色同时登录，各自拿到独立令牌');
const [admin, teacher, student] = await Promise.all([login('admin'), login('t001'), login('2024001')]);
const aTok = admin.data?.token;
const tTok = teacher.data?.token;
const sTok = student.data?.token;
check('管理员登录成功', !!aTok, JSON.stringify(admin).slice(0, 100));
check('教师登录成功', !!tTok, JSON.stringify(teacher).slice(0, 100));
check('学生登录成功', !!sTok, JSON.stringify(student).slice(0, 100));
check('三个令牌互不相同', new Set([aTok, tTok, sTok]).size === 3);

section('2. 同一账号二次登录不失效旧令牌（无状态，不做单会话踢出）');
const admin2 = await login('admin');
check('管理员二次登录得到新令牌', admin2.code === 200 && !!admin2.data?.token);
const oldStillOk = await call('GET', '/api/auth/me', { token: aTok });
check('第一次登录的旧令牌仍然可用', oldStillOk.code === 200, `code=${oldStillOk.code}`);
check('  旧令牌身份未被新登录影响', oldStillOk.data?.role === 'ADMIN', oldStillOk.data?.role);

section('3. 交错并发请求，各自返回本角色数据');
const rounds = 12;
const results = await Promise.all(
  Array.from({ length: rounds }, (_, i) => {
    if (i % 3 === 0) return call('GET', '/api/auth/me', { token: aTok }).then((r) => ({ who: 'ADMIN', r }));
    if (i % 3 === 1) return call('GET', '/api/auth/me', { token: tTok }).then((r) => ({ who: 'TEACHER', r }));
    return call('GET', '/api/auth/me', { token: sTok }).then((r) => ({ who: 'STUDENT', r }));
  }),
);
const mismatch = results.filter((x) => x.r.code !== 200 || x.r.data?.role !== x.who);
check(`${rounds} 次交错请求全部返回各自角色`, mismatch.length === 0,
  JSON.stringify(mismatch.slice(0, 3).map((m) => `${m.who}->${m.r.data?.role}`)));

section('4. 并发访问各自权限范围（越权仍被正确拒绝）');
const [adminUsers, teacherCourses, studentCourses] = await Promise.all([
  call('GET', '/api/admin/users?page=1&size=5', { token: aTok }),
  call('GET', '/api/teacher/courses', { token: tTok }),
  call('GET', '/api/student/selections', { token: sTok }),
]);
// 不带 role 过滤时返回全部账号：1 管理员 + 5 教师 + 26 学生 = 32
check('管理员并发查用户成功', adminUsers.code === 200 && adminUsers.data?.total === 32,
  `total=${adminUsers.data?.total}`);
check('教师并发查课程成功', teacherCourses.code === 200 && Array.isArray(teacherCourses.data),
  `code=${teacherCourses.code}`);
check('学生并发查课表成功', studentCourses.code === 200 && Array.isArray(studentCourses.data),
  `code=${studentCourses.code}`);

const cross = await Promise.all([
  call('GET', '/api/admin/users', { token: sTok }),
  call('GET', '/api/teacher/courses', { token: aTok }),
  call('GET', '/api/student/selections', { token: tTok }),
]);
check('三种越权并发请求均被拒（403）', cross.every((c) => c.code === 403),
  JSON.stringify(cross.map((c) => c.code)));

section('5. 大量不同用户并发（模拟多人同时在线）');
const rollNos = ['2024001', '2024002', '2024003', '2024004', '2024005', '2024006',
  '2024007', '2024008', '2024009', '2024010', '2024011', '2024012'];
const logins = await Promise.all(rollNos.map((n) => login(n)));
const tokens = logins.map((l) => l.data?.token).filter(Boolean);
check(`${rollNos.length} 名学生同时登录成功`, tokens.length === rollNos.length,
  `成功 ${tokens.length}`);
const meAll = await Promise.all(tokens.map((t) => call('GET', '/api/auth/me', { token: t })));
check('每人的档案都指向自己',
  meAll.every((r, i) => r.code === 200 && r.data?.studentNo === rollNos[i]),
  JSON.stringify(meAll.map((r, i) => `${rollNos[i]}->${r.data?.studentNo}`).filter((s) => s.split('->')[0] !== s.split('->')[1])));

section('6. 并发登录本身（同时发起 N 个登录请求）');
const concurrent = await Promise.all(rollNos.map((n) => login(n)));
check('并发登录全部成功', concurrent.every((r) => r.code === 200),
  JSON.stringify(concurrent.filter((r) => r.code !== 200).map((r) => r.message)));
check('并发登录得到互不相同的令牌',
  new Set(concurrent.map((r) => r.data?.token)).size === rollNos.length);

section('7. 无效令牌不影响有效令牌的请求');
const mixed = await Promise.all([
  call('GET', '/api/auth/me', { token: aTok }),
  call('GET', '/api/auth/me', { token: 'invalid-token' }),
  call('GET', '/api/auth/me', { token: tTok }),
  call('GET', '/api/auth/me'),
]);
check('有效令牌在无效令牌混入时仍成功',
  mixed[0].code === 200 && mixed[2].code === 200,
  `admin=${mixed[0].code} teacher=${mixed[2].code}`);
check('无效令牌返回 401', mixed[1].code === 401, `code=${mixed[1].code}`);
check('无令牌返回 401', mixed[3].code === 401, `code=${mixed[3].code}`);

section('8. 前端令牌存储隔离（多用户的真正瓶颈）');
const DEV = process.env.DEV_ORIGIN ?? 'http://localhost:5173';
try {
  const storageSrc = await (await fetch(`${DEV}/src/utils/authStorage.js`)).text();
  check('authStorage 使用 sessionStorage（按标签页隔离）',
    storageSrc.includes('sessionStorage'), '未使用 sessionStorage');
  // localStorage 只应出现在清理历史数据的函数里
  const lsUses = (storageSrc.match(/localStorage/g) ?? []).length;
  check('localStorage 仅用于清理历史遗留令牌（不再作为读写来源）',
    lsUses > 0 && storageSrc.includes('purgeLegacyLocalStorage'),
    `localStorage 出现 ${lsUses} 次`);

  const mainSrc = await (await fetch(`${DEV}/src/main.js`)).text();
  check('应用启动时清理 localStorage 中的历史令牌',
    mainSrc.includes('purgeLegacyLocalStorage'), '未调用');

  const reqSrc = await (await fetch(`${DEV}/src/api/request.js`)).text();
  check('请求拦截器经由 getAuthToken 取令牌（随标签页变化）',
    reqSrc.includes('getAuthToken') && !reqSrc.includes('localStorage'),
    '拦截器直接读取了 localStorage');
} catch (e) {
  check('前端模块可访问（开发服务器 5173）', false, e.message);
}

console.log(`\n${'='.repeat(62)}`);
console.log(`多用户并发验证: ${pass} 通过 / ${fail} 失败  (共 ${pass + fail} 项)`);
if (failures.length) {
  console.log('\n失败项:');
  failures.forEach((f) => console.log('  - ' + f));
}
console.log('='.repeat(62));
process.exitCode = fail === 0 ? 0 : 1;
