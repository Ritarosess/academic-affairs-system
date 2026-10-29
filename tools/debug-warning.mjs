// 学业红线预警功能调试
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

const adminToken = (await login('admin')).data?.token;
const teacherToken = (await login('t001')).data?.token;

section('1. 预警阈值配置生效');
const summary = await call('GET', '/api/admin/warnings/summary', { token: adminToken });
check('GET /admin/warnings/summary 成功', summary.code === 200, JSON.stringify(summary).slice(0, 160));
const s = summary.data ?? {};
console.log('    ', JSON.stringify({
  totalStudents: s.totalStudents, evaluatedStudents: s.evaluatedStudents,
  flagged: s.flaggedStudents, red: s.redCount, orange: s.orangeCount,
  yellow: s.yellowCount, normal: s.normalCount,
}));
check('总学生数为 26', s.totalStudents === 26, `total=${s.totalStudents}`);
check('已评估学生数 = 有成绩的学生', s.evaluatedStudents === 18, `evaluated=${s.evaluatedStudents}`);
check('各等级人数之和 = 总学生数',
  s.redCount + s.orangeCount + s.yellowCount + s.normalCount === 26,
  `${s.redCount}+${s.orangeCount}+${s.yellowCount}+${s.normalCount}`);
check('预警总人数 = 红+橙+黄', s.flaggedStudents === s.redCount + s.orangeCount + s.yellowCount);
check('阈值列表返回三级', Array.isArray(s.thresholds) && s.thresholds.length === 3, JSON.stringify(s.thresholds?.length));
check('红色阈值 failedCredits = 10',
  String(s.thresholds?.find((t) => t.level === 'RED')?.failedCredits) === '10',
  JSON.stringify(s.thresholds?.find((t) => t.level === 'RED')));
check('总览不返回第三条规则的门槛字段 minScoredCourses', s.minScoredCourses === undefined,
  `minScoredCourses=${s.minScoredCourses}`);

section('2. 预期预警名单（依据种子数据设计）');
// 设计：学生1 红色；学生2、3 橙色；学生4、5、7 黄色；其余正常
const list = await call('GET', '/api/admin/warnings?page=1&size=50', { token: adminToken });
check('GET /admin/warnings 默认只返回已触发预警的学生', list.code === 200, JSON.stringify(list).slice(0, 160));
check('预警名单共 6 人', list.data?.total === 6, `total=${list.data?.total}`);
const byNo = Object.fromEntries((list.data?.records ?? []).map((r) => [r.studentNo, r]));
console.log('    ', (list.data?.records ?? []).map((r) => `${r.studentNo}:${r.level}(${r.failedCredits}学分/${r.maxSemesterFailedCount}门)`).join('  '));
check('2024001 李雷 = RED', byNo['2024001']?.level === 'RED', `level=${byNo['2024001']?.level}`);
check('2024002 韩梅梅 = ORANGE', byNo['2024002']?.level === 'ORANGE', `level=${byNo['2024002']?.level}`);
check('2024003 张强 = ORANGE', byNo['2024003']?.level === 'ORANGE', `level=${byNo['2024003']?.level}`);
check('2024004 刘洋 = YELLOW', byNo['2024004']?.level === 'YELLOW', `level=${byNo['2024004']?.level}`);
check('2024005 王芳 = YELLOW', byNo['2024005']?.level === 'YELLOW', `level=${byNo['2024005']?.level}`);
check('2024007 杨帆 = YELLOW', byNo['2024007']?.level === 'YELLOW', `level=${byNo['2024007']?.level}`);
check('按严重程度倒序（首条为 RED）', list.data?.records?.[0]?.level === 'RED', `first=${list.data?.records?.[0]?.level}`);

section('3. 指标与规则明细');
const red = byNo['2024001'];
check('红色学生累计不及格学分 = 12', Number(red?.failedCredits) === 12, `credits=${red?.failedCredits}`);
check('红色学生单学期最多不及格门数 = 4', red?.maxSemesterFailedCount === 4, `count=${red?.maxSemesterFailedCount}`);
check('红色学生已评门数 = 8', red?.scoredCourseCount === 8, `scored=${red?.scoredCourseCount}`);
check('红色学生规则明细恰好 2 条（两条规则取最严）', red?.rules?.length === 2, `rules=${red?.rules?.length}`);
const creditRule = red?.rules?.find((r) => r.code === 'FAILED_CREDITS');
check('累计不及格学分规则 triggered=true', creditRule?.triggered === true, JSON.stringify(creditRule));
check('该规则等级为 RED', creditRule?.level === 'RED', `level=${creditRule?.level}`);
const countRule = red?.rules?.find((r) => r.code === 'SEMESTER_FAILED_COUNT');
check('单学期不及格门数规则 triggered=true', countRule?.triggered === true, JSON.stringify(countRule));
const ruleCodes = (red?.rules ?? []).map((r) => r.code);
check('规则明细仅含 FAILED_CREDITS 与 SEMESTER_FAILED_COUNT（无加权绩点规则）',
  ruleCodes.length === 2
    && ruleCodes.includes('FAILED_CREDITS') && ruleCodes.includes('SEMESTER_FAILED_COUNT')
    && !ruleCodes.includes('WEIGHTED_GPA'),
  JSON.stringify(ruleCodes));
check('红色学生有处置建议', !!red?.advice && red.advice.length > 10, red?.advice);
check('等级文案为中文', red?.levelLabel === '红色预警（学业红线）', red?.levelLabel);
check('返回前端标签类型', red?.tagType === 'danger', red?.tagType);

section('4. 学生本人查看');
const stuLogin = await login('2024001');
const stuToken = stuLogin.data?.token;
const mine = await call('GET', '/api/student/warning', { token: stuToken });
check('GET /student/warning 成功', mine.code === 200, JSON.stringify(mine).slice(0, 160));
check('学号正确', mine.data?.studentNo === '2024001', mine.data?.studentNo);
check('等级为 RED', mine.data?.level === 'RED', mine.data?.level);
check('含班级与专业', !!mine.data?.clazzName && !!mine.data?.majorName,
  `${mine.data?.clazzName}/${mine.data?.majorName}`);
check('含已通过学分', mine.data?.passedCredits !== undefined, String(mine.data?.passedCredits));

const goodLogin = await login('2024006');
const goodToken = goodLogin.data?.token;
const good = await call('GET', '/api/student/warning', { token: goodToken });
check('正常学生等级为 NORMAL', good.data?.level === 'NORMAL', good.data?.level);
check('正常学生 summary 说明学业正常', String(good.data?.summary).includes('正常'), good.data?.summary);
check('正常学生 tagType=success', good.data?.tagType === 'success', good.data?.tagType);

const noDataLogin = await login('2025001');
const noData = await call('GET', '/api/student/warning', { token: noDataLogin.data?.token });
check('无成绩学生等级为 NORMAL 且提示无法评估',
  noData.data?.level === 'NORMAL' && String(noData.data?.summary).includes('暂无成绩'),
  `${noData.data?.level} / ${noData.data?.summary}`);
check('无成绩学生不触发预警，且不返回加权绩点字段',
  noData.data?.level === 'NORMAL' && noData.data?.triggered === false
    && (noData.data?.rules ?? []).every((r) => r.triggered === false)
    && noData.data?.weightedGpa === undefined,
  `level=${noData.data?.level} triggered=${noData.data?.triggered} gpa=${noData.data?.weightedGpa}`);
check('无成绩学生已评门数为 0', noData.data?.scoredCourseCount === 0, String(noData.data?.scoredCourseCount));

section('5. 名单筛选与分页');
const onlyRed = await call('GET', '/api/admin/warnings?level=RED', { token: adminToken });
check('level=RED 过滤 → 1 人', onlyRed.data?.total === 1, `total=${onlyRed.data?.total}`);
const onlyOrange = await call('GET', '/api/admin/warnings?level=ORANGE', { token: adminToken });
check('level=ORANGE 过滤 → 2 人', onlyOrange.data?.total === 2, `total=${onlyOrange.data?.total}`);
const onlyYellow = await call('GET', '/api/admin/warnings?level=YELLOW', { token: adminToken });
check('level=YELLOW 过滤 → 3 人', onlyYellow.data?.total === 3, `total=${onlyYellow.data?.total}`);
const allStudents = await call('GET', '/api/admin/warnings?level=ALL&page=1&size=10', { token: adminToken });
check('level=ALL → 全部 26 人', allStudents.data?.total === 26, `total=${allStudents.data?.total}`);
check('level=ALL 分页 size=10 返回 10 条', allStudents.data?.records?.length === 10,
  `len=${allStudents.data?.records?.length}`);
const normals = await call('GET', '/api/admin/warnings?level=NORMAL', { token: adminToken });
check('level=NORMAL 过滤 → 20 人', normals.data?.total === 20, `total=${normals.data?.total}`);

const byClazz = await call('GET', '/api/admin/warnings?clazzId=1&page=1&size=50', { token: adminToken });
check('按班级 1 过滤只含该班学生', (byClazz.data?.records ?? []).every((r) => r.clazzId === 1),
  JSON.stringify((byClazz.data?.records ?? []).map((r) => r.clazzId)));
check('  计科2401（学号 2024001~2024008）中 6 人预警', byClazz.data?.total === 6, `total=${byClazz.data?.total}`);

const byMajor = await call('GET', '/api/admin/warnings?majorId=1&page=1&size=50', { token: adminToken });
check('按专业 1（计算机科学与技术）过滤生效', byMajor.code === 200 && byMajor.data.total >= 1,
  `total=${byMajor.data?.total}`);

const byKw = await call('GET', '/api/admin/warnings?level=ALL&keyword=李雷', { token: adminToken });
check('按姓名关键字过滤', byKw.data?.total === 1 && byKw.data.records[0].studentName === '李雷',
  `total=${byKw.data?.total}`);

const page2 = await call('GET', '/api/admin/warnings?level=ALL&page=2&size=10', { token: adminToken });
check('第 2 页返回 10 条', page2.data?.records?.length === 10, `len=${page2.data?.records?.length}`);
const page9 = await call('GET', '/api/admin/warnings?level=ALL&page=9&size=10', { token: adminToken });
check('越界页码返回空列表', page9.data?.records?.length === 0, `len=${page9.data?.records?.length}`);

section('6. 教师关联查看');
const roster = await call('GET', '/api/teacher/courses/1/students', { token: teacherToken });
check('教师名单含 warningLevel 字段',
  roster.data?.[0]?.warningLevel !== undefined, JSON.stringify(roster.data?.[0] ?? {}).slice(0, 200));
const warnedInRoster = (roster.data ?? []).filter((r) => r.warningLevel !== 'NORMAL');
console.log('    ', (roster.data ?? []).map((r) => `${r.studentNo}:${r.warningLevel}`).join('  '));
check('课程 1 名单中有预警学生', warnedInRoster.length >= 1, `count=${warnedInRoster.length}`);
check('预警学生带中文等级文案', warnedInRoster.every((r) => !!r.warningLevelLabel),
  JSON.stringify(warnedInRoster.map((r) => r.warningLevelLabel)));

const tDash = await call('GET', '/api/dashboard/summary', { token: teacherToken });
check('教师工作台含 warningStudentCount', tDash.data?.warningStudentCount !== undefined,
  JSON.stringify(tDash.data));

section('7. 管理员工作台与权限');
const aDash = await call('GET', '/api/dashboard/summary', { token: adminToken });
check('管理员工作台含预警统计',
  aDash.data?.warningFlagged !== undefined && aDash.data?.warningRed !== undefined,
  JSON.stringify(aDash.data));
check('  工作台预警人数与 summary 一致', aDash.data?.warningFlagged === s.flaggedStudents,
  `${aDash.data?.warningFlagged} vs ${s.flaggedStudents}`);

const stuTryAdmin = await call('GET', '/api/admin/warnings', { token: stuToken });
check('学生访问管理端预警名单 → 403', stuTryAdmin.code === 403, `code=${stuTryAdmin.code}`);
const teacherTryAdmin = await call('GET', '/api/admin/warnings/summary', { token: teacherToken });
check('教师访问管理端预警总览 → 403', teacherTryAdmin.code === 403, `code=${teacherTryAdmin.code}`);
const noToken = await call('GET', '/api/student/warning');
check('无令牌访问学生预警 → 401', noToken.code === 401, `code=${noToken.code}`);

section('8. 成绩变更后预警实时更新');
// 给一名正常学生录入不及格成绩，预警等级应立即变化
const t2 = await login('t003'); // 王强，任教课程 5（数据库原理）
const roster5 = await call('GET', '/api/teacher/courses/5/students', { token: t2.data?.token });
const victim = (roster5.data ?? []).find((r) => r.warningLevel === 'NORMAL');
if (victim) {
  const before = await call('GET', `/api/admin/warnings?level=ALL&keyword=${victim.studentNo}`, { token: adminToken });
  const beforeLevel = before.data?.records?.[0]?.level;
  await call('POST', '/api/teacher/courses/5/scores', {
    token: t2.data?.token,
    body: { items: [{ studentId: victim.studentId, usualScore: 30, examScore: 35 }] },
  });
  const after = await call('GET', `/api/admin/warnings?level=ALL&keyword=${victim.studentNo}`, { token: adminToken });
  const afterLevel = after.data?.records?.[0]?.level;
  console.log(`    ${victim.studentNo}: ${beforeLevel} -> ${afterLevel} (课程5 记 0 分，4 学分)`);
  check('录入不及格成绩后等级上升', beforeLevel === 'NORMAL' && afterLevel !== 'NORMAL',
    `${beforeLevel} -> ${afterLevel}`);
  check('  学分累计正确（4 学分 → 黄色）', afterLevel === 'YELLOW', `level=${afterLevel}`);
  // 还原为及格
  await call('POST', '/api/teacher/courses/5/scores', {
    token: t2.data?.token,
    body: { items: [{ studentId: victim.studentId, usualScore: 80, examScore: 82 }] },
  });
  const restored = await call('GET', `/api/admin/warnings?level=ALL&keyword=${victim.studentNo}`, { token: adminToken });
  check('  还原成绩后等级回落',
    restored.data?.records?.[0]?.level === beforeLevel,
    `${restored.data?.records?.[0]?.level}`);
}

console.log(`\n${'='.repeat(60)}`);
console.log(`学业红线预警调试: ${pass} 通过 / ${fail} 失败  (共 ${pass + fail} 项)`);
if (failures.length) {
  console.log('\n失败项:');
  failures.forEach((f) => console.log('  - ' + f));
}
console.log('='.repeat(60));
process.exitCode = fail === 0 ? 0 : 1;
