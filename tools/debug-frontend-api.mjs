// 前端 API 调用一致性调试：把 frontend/src/api/*.js 中的每一个调用都在真实后端上跑一遍，
// 用对应角色的令牌，确认没有 404/405/参数名不匹配/字段缺失。
// 运行前请确保后端 8080 已启动。测试会创建并清理临时数据，结束后建议重置数据库。

const BASE = process.env.BASE ?? 'http://127.0.0.1:8080';

let pass = 0;
let fail = 0;
const failures = [];

function check(name, cond, detail = '') {
  if (cond) {
    pass++;
    console.log(`  PASS  ${name}`);
  } else {
    fail++;
    failures.push(`${name} :: ${detail}`);
    console.log(`  FAIL  ${name}  ${detail}`);
  }
}

async function call(method, path, { token, body } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let json = null;
  try {
    json = await res.json();
  } catch {
    json = { code: -1, message: 'non-JSON' };
  }
  return { status: res.status, ...json };
}

/** 断言接口可达且业务成功（用于 GET 类） */
async function okGet(label, path, token, extra) {
  const r = await call('GET', path, { token });
  check(label, r.code === 200, `status=${r.status} code=${r.code} msg=${r.message}`);
  if (extra) extra(r);
  return r;
}

const section = (t) => console.log(`\n=== ${t} ===`);

async function login(username, password = '123456') {
  return call('POST', '/api/auth/login', { body: { username, password } });
}

// ---------------------------------------------------------------- 开始
section('0. 准备三个角色的令牌');
const admin = await login('admin');
const teacher = await login('t001');
const student = await login('2024001');
const adminToken = admin.data?.token;
const teacherToken = teacher.data?.token;
const studentToken = student.data?.token;
check('管理员令牌', !!adminToken, JSON.stringify(admin).slice(0, 120));
check('教师令牌', !!teacherToken, JSON.stringify(teacher).slice(0, 120));
check('学生令牌', !!studentToken, JSON.stringify(student).slice(0, 120));

// ---------------------------------------------------------------- auth.js
section('1. src/api/auth.js');
await okGet('GET /auth/me', '/api/auth/me', adminToken, (r) => {
  check('  ProfileVO 含 userId/role/roleLabel',
    r.data?.userId !== undefined && !!r.data?.role && !!r.data?.roleLabel,
    JSON.stringify(r.data).slice(0, 140));
});
const chg = await call('POST', '/api/auth/change-password', {
  token: studentToken, body: { oldPassword: '123456', newPassword: 'dbg12345' },
});
check('POST /auth/change-password', chg.code === 200, JSON.stringify(chg).slice(0, 120));
const chgBack = await call('POST', '/api/auth/change-password', {
  token: studentToken, body: { oldPassword: 'dbg12345', newPassword: '123456' },
});
check('  改回原密码', chgBack.code === 200, JSON.stringify(chgBack).slice(0, 120));

// ---------------------------------------------------------------- meta.js
section('2. src/api/meta.js');
await okGet('GET /meta/teachers', '/api/meta/teachers', adminToken, (r) =>
  check('  返回数组且含 id/name', Array.isArray(r.data) && r.data[0]?.id !== undefined && !!r.data[0]?.name));
await okGet('GET /meta/semesters', '/api/meta/semesters', adminToken, (r) =>
  check('  含 isCurrent 字段', Array.isArray(r.data) && 'isCurrent' in (r.data[0] ?? {})));
await okGet('GET /meta/grades', '/api/meta/grades', adminToken);
await okGet('GET /meta/majors', '/api/meta/majors', adminToken);
await okGet('GET /meta/clazzs', '/api/meta/clazzs', adminToken, (r) =>
  check('  含 majorName/gradeName', !!r.data?.[0]?.majorName && !!r.data?.[0]?.gradeName));
await okGet('GET /meta/selection-window', '/api/meta/selection-window', studentToken, (r) =>
  check('  含 open/message', typeof r.data?.open === 'boolean' && !!r.data?.message));

// ---------------------------------------------------------------- dashboard.js
section('3. src/api/dashboard.js');
for (const [role, token, key] of [
  ['管理员', adminToken, 'studentCount'],
  ['教师', teacherToken, 'courseCount'],
  ['学生', studentToken, 'selectedCount'],
]) {
  await okGet(`GET /dashboard/summary (${role})`, '/api/dashboard/summary', token, (r) =>
    check(`  含 ${key}`, r.data?.[key] !== undefined, JSON.stringify(r.data).slice(0, 140)));
}

// ---------------------------------------------------------------- adminUser.js
section('4. src/api/adminUser.js');
await okGet('GET /admin/users (分页+角色)', '/api/admin/users?role=STUDENT&page=1&size=10', adminToken, (r) =>
  check('  分页结构完整', r.data?.total !== undefined && Array.isArray(r.data?.records)));
await okGet('GET /admin/users (keyword)', '/api/admin/users?role=STUDENT&keyword=李&page=1&size=10', adminToken);
await okGet('GET /admin/users (第 2 页)', '/api/admin/users?role=STUDENT&page=2&size=10', adminToken);

const ts = Date.now().toString().slice(-6);
const newUser = await call('POST', '/api/admin/users', {
  token: adminToken,
  body: {
    username: `dbg${ts}`, realName: '调试学生', role: 'STUDENT',
    phone: '13700009999', email: 'dbg@test.cn', status: 1,
    studentNo: `DBG${ts}`, gender: '男', clazzId: 1,
  },
});
check('POST /admin/users', newUser.code === 200, JSON.stringify(newUser).slice(0, 160));
const uid = newUser.data?.id;

const updUser = await call('PUT', `/api/admin/users/${uid}`, {
  token: adminToken,
  body: {
    username: `dbg${ts}`, realName: '调试学生改', role: 'STUDENT',
    phone: '13700009998', status: 1, studentNo: `DBG${ts}`, gender: '女', clazzId: 2,
  },
});
check('PUT /admin/users/{id}', updUser.code === 200 && updUser.data?.realName === '调试学生改',
  JSON.stringify(updUser).slice(0, 160));

const st1 = await call('PUT', `/api/admin/users/${uid}/status`, { token: adminToken, body: { status: 0 } });
check('PUT /admin/users/{id}/status 停用', st1.code === 200, JSON.stringify(st1).slice(0, 120));
const st2 = await call('PUT', `/api/admin/users/${uid}/status`, { token: adminToken, body: { status: 1 } });
check('PUT /admin/users/{id}/status 启用', st2.code === 200, JSON.stringify(st2).slice(0, 120));

const rp = await call('PUT', `/api/admin/users/${uid}/reset-password`, { token: adminToken });
check('PUT /admin/users/{id}/reset-password', rp.code === 200 && rp.data?.password === '123456',
  JSON.stringify(rp.data));

// ---------------------------------------------------------------- adminCourse.js
section('5. src/api/adminCourse.js');
await okGet('GET /admin/courses', '/api/admin/courses?page=1&size=10', adminToken, (r) =>
  check('  含 teacherName/semesterName/remaining',
    !!r.data?.records?.[0]?.teacherName && !!r.data?.records?.[0]?.semesterName
      && r.data?.records?.[0]?.remaining !== undefined));
await okGet('GET /admin/courses (按学期)', '/api/admin/courses?semesterId=1&page=1&size=10', adminToken);
await okGet('GET /admin/courses (keyword)', '/api/admin/courses?keyword=数据&page=1&size=10', adminToken);

// 课程审核：管理员端已随「教师申报 → 管理员审核」重构，POST/PUT/DELETE /admin/courses 均已下线，
// 现只提供 批准 / 驳回 两个动作（对应 src/api/adminCourse.js 的 approveCourse / rejectCourse）。
// 用 t005 申报，避免影响下文 t001 的 courseCount 断言。
const applyT = await login('t005');
const applyBody = {
  courseNo: `DBG${ts}`, name: '调试课程', credit: 2.0, hours: 32, courseType: '选修',
  semesterId: 1, capacity: 20, weekday: 6, startSection: 1, endSection: 2,
  location: '调试楼101', description: '调试用', status: 1,
};
const apply1 = await call('POST', '/api/teacher/courses', { token: applyT.data?.token, body: applyBody });
check('POST /teacher/courses（教师申报，提交后待审核）',
  apply1.code === 200 && apply1.data?.auditStatus === 'PENDING',
  JSON.stringify(apply1).slice(0, 160));
const cid = apply1.data?.id;

const approve = await call('PUT', `/api/admin/courses/${cid}/approve`, {
  token: adminToken, body: { remark: '调试通过' },
});
check('PUT /admin/courses/{id}/approve', approve.code === 200, JSON.stringify(approve).slice(0, 160));

const apply2 = await call('POST', '/api/teacher/courses', {
  token: applyT.data?.token, body: { ...applyBody, courseNo: `DBG${ts}R`, name: '调试课程驳回' },
});
const cid2 = apply2.data?.id;
const reject = await call('PUT', `/api/admin/courses/${cid2}/reject`, {
  token: adminToken, body: { remark: '调试驳回' },
});
check('PUT /admin/courses/{id}/reject', reject.code === 200, JSON.stringify(reject).slice(0, 160));

// ---------------------------------------------------------------- adminOrg.js
section('6. src/api/adminOrg.js');

// 年级
await okGet('GET /admin/org/grades', '/api/admin/org/grades', adminToken);
const g = await call('POST', '/api/admin/org/grades', {
  token: adminToken, body: { name: `调试${ts}级`, enrollYear: 2999 },
});
check('POST /admin/org/grades', g.code === 200, JSON.stringify(g).slice(0, 140));
const gid = g.data?.id;
const gu = await call('PUT', `/api/admin/org/grades/${gid}`, {
  token: adminToken, body: { name: `调试${ts}级改`, enrollYear: 2998 },
});
check('PUT /admin/org/grades/{id}', gu.code === 200 && gu.data?.enrollYear === 2998,
  JSON.stringify(gu).slice(0, 140));

// 专业
await okGet('GET /admin/org/majors', '/api/admin/org/majors', adminToken);
const m = await call('POST', '/api/admin/org/majors', {
  token: adminToken, body: { name: `调试专业${ts}`, department: '调试学院' },
});
check('POST /admin/org/majors', m.code === 200, JSON.stringify(m).slice(0, 140));
const mid = m.data?.id;
const mu = await call('PUT', `/api/admin/org/majors/${mid}`, {
  token: adminToken, body: { name: `调试专业${ts}改`, department: '调试学院改' },
});
check('PUT /admin/org/majors/{id}', mu.code === 200 && mu.data?.department === '调试学院改',
  JSON.stringify(mu).slice(0, 140));

// 班级
await okGet('GET /admin/org/clazzs', '/api/admin/org/clazzs', adminToken);
const c = await call('POST', '/api/admin/org/clazzs', {
  token: adminToken, body: { name: `调试${ts}班`, majorId: mid, gradeId: gid, counselor: '调试员' },
});
check('POST /admin/org/clazzs', c.code === 200 && !!c.data?.majorName && !!c.data?.gradeName,
  JSON.stringify(c).slice(0, 160));
const ccid = c.data?.id;
const cu = await call('PUT', `/api/admin/org/clazzs/${ccid}`, {
  token: adminToken, body: { name: `调试${ts}班改`, majorId: mid, gradeId: gid, counselor: '调试员改' },
});
check('PUT /admin/org/clazzs/{id}', cu.code === 200 && cu.data?.name === `调试${ts}班改`,
  JSON.stringify(cu).slice(0, 160));

// 逆序清理（班级 → 专业 → 年级）
check('DELETE /admin/org/clazzs/{id}', (await call('DELETE', `/api/admin/org/clazzs/${ccid}`, { token: adminToken })).code === 200);
check('DELETE /admin/org/majors/{id}', (await call('DELETE', `/api/admin/org/majors/${mid}`, { token: adminToken })).code === 200);
check('DELETE /admin/org/grades/{id}', (await call('DELETE', `/api/admin/org/grades/${gid}`, { token: adminToken })).code === 200);

// ---------------------------------------------------------------- adminSemester.js
section('7. src/api/adminSemester.js');
await okGet('GET /admin/semesters', '/api/admin/semesters', adminToken, (r) =>
  check('  返回数组', Array.isArray(r.data)));
const s = await call('POST', '/api/admin/semesters', {
  token: adminToken,
  body: {
    name: `调试学期${ts}`, startDate: '2027-02-01', endDate: '2027-07-01',
    selectStartTime: '2027-02-01 08:00:00', selectEndTime: '2027-03-01 18:00:00', isCurrent: 0,
  },
});
check('POST /admin/semesters', s.code === 200, JSON.stringify(s).slice(0, 160));
const sid = s.data?.id;
const su = await call('PUT', `/api/admin/semesters/${sid}`, {
  token: adminToken,
  body: {
    name: `调试学期${ts}改`, startDate: '2027-02-01', endDate: '2027-07-01',
    selectStartTime: '2027-02-01 08:00:00', selectEndTime: '2027-03-01 18:00:00', isCurrent: 0,
  },
});
check('PUT /admin/semesters/{id}', su.code === 200 && su.data?.name === `调试学期${ts}改`,
  JSON.stringify(su).slice(0, 160));

// setCurrent 会改变选课周期，测完必须还原为学期 1
const sc = await call('PUT', `/api/admin/semesters/${sid}/current`, { token: adminToken });
check('PUT /admin/semesters/{id}/current', sc.code === 200, JSON.stringify(sc).slice(0, 140));
const afterSc = await call('GET', '/api/admin/semesters', { token: adminToken });
const currents = (afterSc.data ?? []).filter((x) => x.isCurrent === 1);
check('  当前学期仍全局唯一', currents.length === 1 && currents[0].id === sid,
  `currents=${JSON.stringify(currents.map((x) => x.id))}`);
const restore = await call('PUT', '/api/admin/semesters/1/current', { token: adminToken });
check('  已还原当前学期为 1', restore.code === 200);
check('DELETE /admin/semesters/{id}', (await call('DELETE', `/api/admin/semesters/${sid}`, { token: adminToken })).code === 200);

// 清理临时用户
check('DELETE /admin/users/{id}', (await call('DELETE', `/api/admin/users/${uid}`, { token: adminToken })).code === 200);

// ---------------------------------------------------------------- teacher.js
section('8. src/api/teacher.js');
let teacherCourseId = null;
await okGet('GET /teacher/courses', '/api/teacher/courses', teacherToken, (r) => {
  check('  返回数组且含 selectedCount', Array.isArray(r.data) && r.data[0]?.selectedCount !== undefined);
  teacherCourseId = r.data?.[0]?.id;
});
await okGet('GET /teacher/profile', '/api/teacher/profile', teacherToken, (r) =>
  check('  含 teacherNo', !!r.data?.teacherNo, JSON.stringify(r.data).slice(0, 140)));

if (teacherCourseId) {
  let roster = null;
  await okGet(`GET /teacher/courses/${teacherCourseId}/students`, `/api/teacher/courses/${teacherCourseId}/students`, teacherToken, (r) => {
    roster = r.data;
    check('  名单含 studentNo/studentName/clazzName',
      Array.isArray(r.data) && (r.data.length === 0 || (!!r.data[0].studentNo && !!r.data[0].studentName)),
      JSON.stringify(r.data?.[0] ?? {}).slice(0, 160));
  });
  await okGet(`GET /teacher/courses/${teacherCourseId}/statistics`, `/api/teacher/courses/${teacherCourseId}/statistics`, teacherToken, (r) =>
    check('  统计含 avgScore/passCount', r.data?.avgScore !== undefined && r.data?.passCount !== undefined,
      JSON.stringify(r.data)));

  if (Array.isArray(roster) && roster.length) {
    const items = roster.map((x, i) => ({
      studentId: x.studentId,
      usualScore: 70 + (i % 20),
      examScore: 75 + (i % 20),
    }));
    const save = await call('POST', `/api/teacher/courses/${teacherCourseId}/scores`, {
      token: teacherToken, body: { items },
    });
    check(`POST /teacher/courses/${teacherCourseId}/scores`, save.code === 200,
      JSON.stringify(save).slice(0, 160));
  }
}

// ---------------------------------------------------------------- student.js
section('9. src/api/student.js');
await okGet('GET /student/courses', '/api/student/courses?page=1&size=10', studentToken, (r) =>
  check('  含 selected/weekdayLabel/remaining',
    r.data?.records?.[0]?.selected !== undefined
      && !!r.data?.records?.[0]?.weekdayLabel
      && r.data?.records?.[0]?.remaining !== undefined));
await okGet('GET /student/courses (性质筛选)', '/api/student/courses?courseType=必修&page=1&size=10', studentToken);
await okGet('GET /student/courses (keyword)', '/api/student/courses?keyword=数据&page=1&size=10', studentToken);
await okGet('GET /student/selections', '/api/student/selections', studentToken, (r) =>
  check('  含 status/statusLabel/scored',
    Array.isArray(r.data) && (r.data.length === 0
      || (!!r.data[0].status && !!r.data[0].statusLabel && r.data[0].scored !== undefined))));
await okGet('GET /student/scores', '/api/student/scores', studentToken, (r) =>
  check('  含 courseName/gradePoint',
    Array.isArray(r.data) && (r.data.length === 0 || (!!r.data[0].courseName && r.data[0].gradePoint !== undefined))));
await okGet('GET /student/profile', '/api/student/profile', studentToken, (r) =>
  check('  含 studentNo/clazzName', !!r.data?.studentNo && !!r.data?.clazzName,
    JSON.stringify(r.data).slice(0, 160)));

// course 2（计算机组成原理）现为必修课、不可退课，改用选修课 10（信号与系统）
const sel = await call('POST', '/api/student/selections', { token: studentToken, body: { courseId: 10 } });
check('POST /student/selections', sel.code === 200, JSON.stringify(sel).slice(0, 140));
const drop = await call('DELETE', '/api/student/selections/10', { token: studentToken });
check('DELETE /student/selections/{courseId}', drop.code === 200, JSON.stringify(drop).slice(0, 140));

// ---------------------------------------------------------------- 汇总
console.log(`\n${'='.repeat(60)}`);
console.log(`前端 API 一致性调试: ${pass} 通过 / ${fail} 失败  (共 ${pass + fail} 项)`);
if (failures.length) {
  console.log('\n失败项:');
  failures.forEach((f) => console.log('  - ' + f));
}
console.log('='.repeat(60));
process.exitCode = fail === 0 ? 0 : 1;
