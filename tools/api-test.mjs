// End-to-end API smoke test for the 高校教务系统 backend.
// Exercises the frozen contract in docs/接口设计.md: auth, admin user CRUD,
// 课程申报与审核（教师申报 → 管理员批准）、student selection/drop, teacher score
// entry, and the anti-oversell rule.

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
    failures.push(`${name} ${detail}`);
    console.log(`  FAIL  ${name}  ${detail}`);
  }
}

async function api(method, path, { token, body } = {}) {
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
    json = { code: -1, message: `non-JSON response (HTTP ${res.status})` };
  }
  return { status: res.status, ...json };
}

async function login(username, password = '123456') {
  const r = await api('POST', '/api/auth/login', { body: { username, password } });
  return r;
}

function section(title) {
  console.log(`\n=== ${title} ===`);
}

const run = async () => {
  // ---------------------------------------------------------------- 认证
  section('1. 认证与鉴权');
  const adminLogin = await login('admin');
  check('管理员登录成功', adminLogin.code === 200 && !!adminLogin.data?.token,
    JSON.stringify(adminLogin).slice(0, 200));
  const adminToken = adminLogin.data?.token;
  check('登录返回角色为 ADMIN', adminLogin.data?.user?.role === 'ADMIN');
  check('登录响应不含密码字段', !('password' in (adminLogin.data?.user ?? {})));

  const teacherLogin = await login('t001');
  const teacherToken = teacherLogin.data?.token;
  check('教师登录成功', teacherLogin.code === 200 && !!teacherToken);
  check('教师角色为 TEACHER', teacherLogin.data?.user?.role === 'TEACHER');

  const studentLogin = await login('2024001');
  const studentToken = studentLogin.data?.token;
  check('学生登录成功', studentLogin.code === 200 && !!studentToken);
  check('学生角色为 STUDENT', studentLogin.data?.user?.role === 'STUDENT');

  const badPw = await login('admin', 'wrong-password');
  check('错误密码被拒绝', badPw.code === 401, `code=${badPw.code}`);

  const noToken = await api('GET', '/api/auth/me');
  check('无令牌访问受保护接口返回 401', noToken.code === 401, `code=${noToken.code}`);

  const meStudent = await api('GET', '/api/auth/me', { token: studentToken });
  check('学生档案含学号与班级',
    meStudent.data?.studentNo === '2024001' && meStudent.data?.clazzName === '计科2401',
    JSON.stringify(meStudent.data));

  // 越权：学生访问管理员接口
  const crossRole = await api('GET', '/api/admin/users?role=STUDENT', { token: studentToken });
  check('学生越权访问管理员接口被拒(403)', crossRole.code === 403, `code=${crossRole.code}`);

  // ---------------------------------------------------------------- 元数据
  section('2. 公共元数据');
  const meta = await api('GET', '/api/meta/selection-window', { token: studentToken });
  check('选课窗口处于开放状态', meta.data?.open === true, JSON.stringify(meta.data));
  const teachers = await api('GET', '/api/meta/teachers', { token: adminToken });
  check('教师下拉数据非空', Array.isArray(teachers.data) && teachers.data.length >= 5);
  const clazzs = await api('GET', '/api/meta/clazzs', { token: adminToken });
  check('班级下拉带专业/年级名称',
    clazzs.data?.length >= 5 && !!clazzs.data[0].majorName && !!clazzs.data[0].gradeName);

  // ---------------------------------------------------------------- 管理员
  section('3. 管理员功能');
  const users = await api('GET', '/api/admin/users?role=STUDENT&page=1&size=5', { token: adminToken });
  check('用户分页查询返回 26 名学生', users.data?.total === 26, `total=${users.data?.total}`);
  check('分页结构正确(records/page/size/total)',
    Array.isArray(users.data?.records) && users.data.size === 5);

  const kw = await api('GET', '/api/admin/users?role=STUDENT&keyword=李雷', { token: adminToken });
  check('按姓名关键字检索命中', kw.data?.total === 1 && kw.data.records[0].realName === '李雷',
    `total=${kw.data?.total}`);

  // 创建学生
  const newStudentNo = '2024900';
  const created = await api('POST', '/api/admin/users', {
    token: adminToken,
    body: {
      username: newStudentNo, realName: '测试学生', role: 'STUDENT',
      phone: '13700000900', status: 1, studentNo: newStudentNo, gender: '男', clazzId: 1,
    },
  });
  check('创建学生用户成功', created.code === 200 && created.data?.studentNo === newStudentNo,
    JSON.stringify(created).slice(0, 200));
  const createdId = created.data?.id;

  const dupUser = await api('POST', '/api/admin/users', {
    token: adminToken,
    body: { username: newStudentNo, realName: '重复', role: 'STUDENT', studentNo: newStudentNo, clazzId: 1 },
  });
  check('用户名重复被拒', dupUser.code === 400, `code=${dupUser.code}`);

  const dupNo = await api('POST', '/api/admin/users', {
    token: adminToken,
    body: { username: 'other900', realName: '重复学号', role: 'STUDENT', studentNo: newStudentNo, clazzId: 1 },
  });
  check('学号重复被拒', dupNo.code === 400, `code=${dupNo.code}`);

  // 新用户可用默认密码登录
  const newLogin = await login(newStudentNo);
  check('新建用户可用默认密码 123456 登录', newLogin.code === 200, JSON.stringify(newLogin).slice(0, 150));
  check('新建用户被标记为需修改密码', newLogin.data?.user?.mustChangePassword === 1);

  // 参数校验
  const badPhone = await api('POST', '/api/admin/users', {
    token: adminToken,
    body: { username: 'badphone1', realName: '手机号非法', role: 'STUDENT', studentNo: '2024901', phone: 'abc' },
  });
  check('手机号格式校验生效', badPhone.code === 400, `code=${badPhone.code}`);

  // 停用
  const disable = await api('PUT', `/api/admin/users/${createdId}/status`, {
    token: adminToken, body: { status: 0 },
  });
  check('停用用户成功', disable.code === 200);
  const disabledLogin = await login(newStudentNo);
  check('停用账号无法登录', disabledLogin.code === 401, `code=${disabledLogin.code}`);
  await api('PUT', `/api/admin/users/${createdId}/status`, { token: adminToken, body: { status: 1 } });

  // 重置密码
  const changePw = await api('POST', '/api/auth/change-password', {
    token: studentToken, body: { oldPassword: '123456', newPassword: 'abc12345' },
  });
  check('修改本人密码成功', changePw.code === 200, JSON.stringify(changePw));
  const loginNewPw = await login('2024001', 'abc12345');
  check('新密码可登录', loginNewPw.code === 200);
  const wrongOld = await api('POST', '/api/auth/change-password', {
    token: studentToken, body: { oldPassword: 'nope123', newPassword: 'xyz12345' },
  });
  check('原密码错误被拒', wrongOld.code === 400, `code=${wrongOld.code}`);
  // 改回默认密码
  await api('POST', '/api/auth/change-password', {
    token: studentToken, body: { oldPassword: 'abc12345', newPassword: '123456' },
  });

  const reset = await api('PUT', `/api/admin/users/${createdId}/reset-password`, { token: adminToken });
  check('重置密码返回默认密码', reset.code === 200 && reset.data?.password === '123456',
    JSON.stringify(reset.data));

  // 课程管理（重构后：管理员不再直接建课，改为「教师申报 → 管理员审核」）
  const courses = await api('GET', '/api/admin/courses?page=1&size=10', { token: adminToken });
  // 种子数据：当前学期 10 门 + 历史学期 4 门 = 14 门
  check('课程分页查询返回 14 门课（含历史学期）', courses.data?.total === 14, `total=${courses.data?.total}`);
  check('课程含任课教师与学期名称',
    !!courses.data?.records[0]?.teacherName && !!courses.data.records[0].semesterName);
  check('课程含剩余名额字段',
    courses.data?.records.every(c => c.remaining === c.capacity - c.selectedCount));

  // 管理员已无建课接口（POST 不再被支持）
  const adminCreate = await api('POST', '/api/admin/courses', {
    token: adminToken,
    body: {
      courseNo: 'ADMIN901', name: '管理员直建课程', credit: 2.0, hours: 32, courseType: '选修',
      semesterId: 1, capacity: 30,
    },
  });
  check('管理员不再提供直接建课接口（405）', adminCreate.code === 405, JSON.stringify(adminCreate).slice(0, 150));

  // 课程申报改由教师端发起；此处用 t005（陈静）申报，避免影响后续 t001 授课课程数的断言
  const t5Login = await login('t005');
  const t5Token = t5Login.data?.token;

  const newCourse = await api('POST', '/api/teacher/courses', {
    token: t5Token,
    body: {
      courseNo: 'TEST901', name: '测试课程', credit: 2.0, hours: 32, courseType: '选修',
      semesterId: 1, capacity: 30, weekday: 5, startSection: 5, endSection: 6,
      location: '测试楼101', description: '联调测试用', status: 1,
    },
  });
  check('教师申报课程成功（提交后待审核）',
    newCourse.code === 200 && newCourse.data?.auditStatus === 'PENDING',
    JSON.stringify(newCourse).slice(0, 200));
  const newCourseId = newCourse.data?.id;

  const pending = await api('GET', '/api/admin/courses?auditStatus=PENDING&page=1&size=20', { token: adminToken });
  check('管理员可按 auditStatus 过滤待审核课程',
    pending.code === 200 && pending.data?.records?.some(c => c.id === newCourseId),
    JSON.stringify(pending).slice(0, 200));

  const approve = await api('PUT', `/api/admin/courses/${newCourseId}/approve`, {
    token: adminToken, body: { remark: '联调测试通过' },
  });
  check('管理员审核通过课程申报', approve.code === 200, JSON.stringify(approve).slice(0, 150));
  const approved = await api('GET', `/api/admin/courses?semesterId=1&keyword=TEST901`, { token: adminToken });
  check('审核通过后课程状态为 APPROVED',
    approved.data?.records?.[0]?.auditStatus === 'APPROVED',
    JSON.stringify(approved.data?.records?.[0] ?? {}).slice(0, 200));

  const dupCourse = await api('POST', '/api/teacher/courses', {
    token: t5Token,
    body: {
      courseNo: 'TEST901', name: '重复课程号', credit: 2.0, hours: 32, courseType: '选修',
      semesterId: 1, capacity: 30,
    },
  });
  check('同学期课程号重复被拒', dupCourse.code === 400, `code=${dupCourse.code} msg=${dupCourse.message}`);

  const badCredit = await api('POST', '/api/teacher/courses', {
    token: t5Token,
    body: {
      courseNo: 'TEST902', name: '学分非法', credit: 0, hours: 32, courseType: '选修',
      semesterId: 1, capacity: 30,
    },
  });
  check('学分必须大于 0 校验生效', badCredit.code === 400, `code=${badCredit.code} msg=${badCredit.message}`);

  // 组织架构
  const grades = await api('GET', '/api/admin/org/grades', { token: adminToken });
  check('年级列表非空', Array.isArray(grades.data) && grades.data.length >= 3);
  const majors = await api('GET', '/api/admin/org/majors', { token: adminToken });
  check('专业列表非空', Array.isArray(majors.data) && majors.data.length >= 4);

  const delGrade = await api('DELETE', '/api/admin/org/grades/1', { token: adminToken });
  check('删除被班级引用的年级被拒', delGrade.code === 400, `code=${delGrade.code}`);

  // 学期
  const semesters = await api('GET', '/api/admin/semesters', { token: adminToken });
  check('学期列表非空', Array.isArray(semesters.data) && semesters.data.length >= 2);
  const currentCount = semesters.data.filter(s => s.isCurrent === 1).length;
  check('当前学期全局唯一', currentCount === 1, `count=${currentCount}`);

  const badWindow = await api('PUT', '/api/admin/semesters/1', {
    token: adminToken,
    body: {
      name: '2026-2027学年第一学期', startDate: '2026-09-01', endDate: '2027-01-15',
      selectStartTime: '2026-12-01 00:00:00', selectEndTime: '2026-10-01 00:00:00',
    },
  });
  check('选课开始晚于结束被拒', badWindow.code === 400, `code=${badWindow.code}`);

  // ---------------------------------------------------------------- 学生选课
  section('4. 选课与退课（核心业务）');
  const hall = await api('GET', '/api/student/courses?page=1&size=20', { token: studentToken });
  check('选课大厅返回当前学期课程', hall.data?.total === 11, `total=${hall.data?.total}`);
  check('课程含 selected 标记', hall.data?.records.some(c => c.selected === true));
  check('课程含 weekdayLabel', !!hall.data?.records[0]?.weekdayLabel);

  // 退课用例必须用「非必修 + 该生无成绩」的课：course 2（CS102）在种子数据中已改为必修课，
  // 必修课一律不可退课；这里改用 course 10（EL202 信号与系统，选修、该生尚无成绩，
  // 且种子数据中该生对它是 DROPPED 记录，可同时验证「退课后重选复用原记录」）。
  const DROP_COURSE = 10;
  const before = await api('GET', '/api/admin/courses?page=1&size=50', { token: adminToken });
  const cBefore = before.data.records.find(c => c.id === DROP_COURSE);

  const selOk = await api('POST', '/api/student/selections', {
    token: studentToken, body: { courseId: DROP_COURSE },
  });
  check('选课成功并返回"选课成功"', selOk.code === 200 && selOk.message === '选课成功',
    JSON.stringify(selOk));

  const after = await api('GET', '/api/admin/courses?page=1&size=50', { token: adminToken });
  const cAfter = after.data.records.find(c => c.id === DROP_COURSE);
  check('选课后容量 -1', cAfter.selectedCount === cBefore.selectedCount + 1,
    `${cBefore.selectedCount} -> ${cAfter.selectedCount}`);

  const dupSel = await api('POST', '/api/student/selections', {
    token: studentToken, body: { courseId: DROP_COURSE },
  });
  check('重复选课提示"请勿重复选课"',
    dupSel.code === 400 && dupSel.message === '请勿重复选课', JSON.stringify(dupSel));

  const mySel = await api('GET', '/api/student/selections', { token: studentToken });
  check('我的课表包含刚选的课程',
    mySel.data?.some(s => s.courseId === DROP_COURSE && s.status === 'SELECTED'));

  // 退课
  const dropOk = await api('DELETE', `/api/student/selections/${DROP_COURSE}`, { token: studentToken });
  check('退课成功并返回"退课成功"', dropOk.code === 200 && dropOk.message === '退课成功',
    JSON.stringify(dropOk));

  const afterDrop = await api('GET', '/api/admin/courses?page=1&size=50', { token: adminToken });
  const cDrop = afterDrop.data.records.find(c => c.id === DROP_COURSE);
  check('退课后容量还原', cDrop.selectedCount === cBefore.selectedCount,
    `expected ${cBefore.selectedCount}, got ${cDrop.selectedCount}`);

  const dropAgain = await api('DELETE', `/api/student/selections/${DROP_COURSE}`, { token: studentToken });
  check('重复退课提示无有效记录',
    dropAgain.code === 400 && dropAgain.message === '退课失败，未找到有效选课记录', JSON.stringify(dropAgain));

  // 重选（复用 DROPPED 记录）
  const reselect = await api('POST', '/api/student/selections', {
    token: studentToken, body: { courseId: DROP_COURSE },
  });
  check('退课后可重选', reselect.code === 200, JSON.stringify(reselect));

  // 已录入成绩的课程不可退课（BR-04）：由该课任课教师 t005 为这名学生录入成绩
  const rosterDrop = await api('GET', `/api/teacher/courses/${DROP_COURSE}/students`, { token: t5Token });
  const dropStudent = rosterDrop.data?.find(r => r.studentNo === '2024001');
  const prepScore = await api('POST', `/api/teacher/courses/${DROP_COURSE}/scores`, {
    token: t5Token,
    body: { items: [{ studentId: dropStudent?.studentId, usualScore: 80, examScore: 85 }] },
  });
  check('为待退课程录入成绩成功（准备"已评分课程"）', prepScore.code === 200,
    JSON.stringify(prepScore).slice(0, 150));

  const dropScored = await api('DELETE', `/api/student/selections/${DROP_COURSE}`, { token: studentToken });
  check('已录入成绩的课程不可退课',
    dropScored.code === 400 && dropScored.message === '该课程已录入成绩，不可退课',
    JSON.stringify(dropScored));

  // 关闭选课窗口后不能选课
  await api('PUT', '/api/admin/semesters/1', {
    token: adminToken,
    body: {
      name: '2026-2027学年第一学期', startDate: '2026-09-01', endDate: '2027-01-15',
      selectStartTime: '2020-01-01 00:00:00', selectEndTime: '2020-01-02 00:00:00',
    },
  });
  const closedSel = await api('POST', '/api/student/selections', {
    token: studentToken, body: { courseId: 4 },
  });
  check('非选课时段提示"当前非选课时段"',
    closedSel.code === 400 && closedSel.message === '当前非选课时段', JSON.stringify(closedSel));

  // 恢复选课窗口
  const now = new Date();
  const fmt = (d) => d.toISOString().slice(0, 19).replace('T', ' ');
  const start = new Date(now.getTime() - 7 * 86400000);
  const end = new Date(now.getTime() + 120 * 86400000);
  await api('PUT', '/api/admin/semesters/1', {
    token: adminToken,
    body: {
      name: '2026-2027学年第一学期', startDate: '2026-09-01', endDate: '2027-01-15',
      selectStartTime: fmt(start), selectEndTime: fmt(end),
    },
  });
  const reopened = await api('GET', '/api/meta/selection-window', { token: studentToken });
  check('选课窗口已恢复开放', reopened.data?.open === true, JSON.stringify(reopened.data));

  // ---------------------------------------------------------------- 教师
  section('5. 教师功能');
  const tCourses = await api('GET', '/api/teacher/courses', { token: teacherToken });
  // 张伟：当前学期 CS101、CS102 + 历史学期 CS103
  check('教师查看授课课程', tCourses.data?.length === 3, `count=${tCourses.data?.length}`);
  check('教师课程包含学生已选人数',
    tCourses.data?.every(c => typeof c.selectedCount === 'number'));

  const roster = await api('GET', '/api/teacher/courses/1/students', { token: teacherToken });
  check('查看选课学生名单', roster.data?.length === 8, `count=${roster.data?.length}`);
  check('名单含学号/姓名/班级',
    !!roster.data?.[0]?.studentNo && !!roster.data?.[0]?.studentName && !!roster.data?.[0]?.clazzName);
  check('名单标记已有成绩的学生', roster.data?.some(r => r.scored === true));

  // 越权：教师访问他人课程
  const otherCourse = await api('GET', '/api/teacher/courses/3/students', { token: teacherToken });
  check('教师访问非本人课程被拒(403)', otherCourse.code === 403, `code=${otherCourse.code}`);

  // 成绩录入
  const rosterIds = roster.data.map(r => r.studentId);
  const saveScores = await api('POST', '/api/teacher/courses/1/scores', {
    token: teacherToken,
    body: {
      items: rosterIds.map((id, i) => ({ studentId: id, usualScore: 70 + i, examScore: 75 + i })),
    },
  });
  check('批量录入成绩成功', saveScores.code === 200, JSON.stringify(saveScores));

  const rosterAfter = await api('GET', '/api/teacher/courses/1/students', { token: teacherToken });
  check('录入后全部学生均有成绩', rosterAfter.data?.every(r => r.scored === true));
  const first = rosterAfter.data[0];
  check('总评 = 平时×40% + 期末×60%',
    Math.abs(first.totalScore - (first.usualScore * 0.4 + first.examScore * 0.6)) < 0.011,
    `${first.usualScore},${first.examScore} -> ${first.totalScore}`);
  check('绩点按五级制换算', typeof first.gradePoint === 'number' && first.gradePoint >= 0);

  const badScore = await api('POST', '/api/teacher/courses/1/scores', {
    token: teacherToken,
    body: { items: [{ studentId: rosterIds[0], usualScore: 120, examScore: 80 }] },
  });
  check('成绩超出 0~100 被拒', badScore.code === 400, `code=${badScore.code} msg=${badScore.message}`);

  const stats = await api('GET', '/api/teacher/courses/1/statistics', { token: teacherToken });
  check('成绩统计返回平均分/最高分/及格人数',
    stats.data?.avgScore !== undefined && stats.data?.maxScore !== undefined
      && stats.data?.passCount !== undefined, JSON.stringify(stats.data));

  const scoreOther = await api('POST', '/api/teacher/courses/3/scores', {
    token: teacherToken, body: { items: [{ studentId: 9, usualScore: 80, examScore: 80 }] },
  });
  check('教师不能给非本人课程录成绩(403)', scoreOther.code === 403, `code=${scoreOther.code}`);

  // ---------------------------------------------------------------- 学生成绩
  section('6. 学生成绩与工作台');
  const myScores = await api('GET', '/api/student/scores', { token: studentToken });
  check('学生可查询本人成绩', Array.isArray(myScores.data) && myScores.data.length >= 1,
    `count=${myScores.data?.length}`);
  check('成绩含课程名与绩点',
    !!myScores.data?.[0]?.courseName && myScores.data?.[0]?.gradePoint !== undefined);

  const dashStudent = await api('GET', '/api/dashboard/summary', { token: studentToken });
  check('学生工作台统计可用',
    dashStudent.data?.selectedCount !== undefined && dashStudent.data?.totalCredit !== undefined,
    JSON.stringify(dashStudent.data));

  const dashAdmin = await api('GET', '/api/dashboard/summary', { token: adminToken });
  check('管理员工作台统计可用',
    dashAdmin.data?.studentCount === 27 && dashAdmin.data?.courseCount === 15,
    JSON.stringify(dashAdmin.data));
  check('管理员工作台含学业预警统计',
    dashAdmin.data?.warningFlagged !== undefined && dashAdmin.data?.warningRed !== undefined,
    JSON.stringify(dashAdmin.data));

  const dashTeacher = await api('GET', '/api/dashboard/summary', { token: teacherToken });
  check('教师工作台统计可用',
    dashTeacher.data?.courseCount === 3 && dashTeacher.data?.studentCount >= 8,
    JSON.stringify(dashTeacher.data));
  check('教师工作台含关联预警学生数',
    dashTeacher.data?.warningStudentCount !== undefined,
    JSON.stringify(dashTeacher.data));

  // ---------------------------------------------------------------- 并发防超卖
  section('7. 并发选课防超卖');
  const small = await api('POST', '/api/teacher/courses', {
    token: t5Token,
    body: {
      courseNo: 'RACE001', name: '并发测试课', credit: 1.0, hours: 16, courseType: '选修',
      semesterId: 1, capacity: 3, weekday: 6, startSection: 1, endSection: 2,
      location: '测试楼202', status: 1,
    },
  });
  check('教师申报容量为 3 的测试课程', small.code === 200, JSON.stringify(small).slice(0, 150));
  const raceCourseId = small.data?.id;
  const raceApprove = await api('PUT', `/api/admin/courses/${raceCourseId}/approve`, {
    token: adminToken, body: { remark: '并发测试用' },
  });
  check('审核通过并发测试课程（未审核课程不可选）', raceApprove.code === 200,
    JSON.stringify(raceApprove).slice(0, 150));

  // 让 12 名不同的学生并发抢 3 个名额
  const rollNos = ['2024009', '2024010', '2024011', '2024012', '2024013', '2024014',
    '2024015', '2024016', '2024017', '2024018', '2025001', '2025002'];
  const tokens = [];
  for (const no of rollNos) {
    const r = await login(no);
    if (r.code === 200) tokens.push(r.data.token);
  }
  check('准备 12 个学生令牌', tokens.length === 12, `count=${tokens.length}`);

  const results = await Promise.all(
    tokens.map(t => api('POST', '/api/student/selections', { token: t, body: { courseId: raceCourseId } })),
  );
  const okCount = results.filter(r => r.code === 200).length;
  const fullCount = results.filter(r => r.message === '该课程名额已满').length;
  check('并发抢课成功数恰好等于容量 3', okCount === 3, `ok=${okCount}`);
  check('其余请求均返回"该课程名额已满"', fullCount === 9, `full=${fullCount}`);

  const raceAfter = await api('GET', '/api/admin/courses?page=1&size=50', { token: adminToken });
  const raceCourse = raceAfter.data.records.find(c => c.id === raceCourseId);
  check('课程已选人数未超过容量（无超卖）', raceCourse.selectedCount === 3,
    `selectedCount=${raceCourse.selectedCount}`);
  check('剩余名额为 0', raceCourse.remaining === 0);

  // ---------------------------------------------------------------- 清理
  section('8. 清理测试数据');
  // 重构后管理员不再有删课接口：/api/admin/courses/{id} 已无任何映射，故返回 404（资源不存在）
  const adminDelete = await api('DELETE', `/api/admin/courses/${raceCourseId}`, { token: adminToken });
  check('管理员不再提供删课接口（404 资源不存在）', adminDelete.code === 404,
    JSON.stringify(adminDelete).slice(0, 150));
  // 教师只能删除本人「尚未通过审核」的课程：已通过审核（且已有选课记录）的课程一律不可删除
  const cleanupCourse = await api('DELETE', `/api/teacher/courses/${raceCourseId}`, { token: t5Token });
  check('删除有选课记录的课程被拒（已通过审核的课程不可删除）',
    cleanupCourse.code === 400 && String(cleanupCourse.message).includes('不能删除'),
    JSON.stringify(cleanupCourse).slice(0, 200));
  const delUser = await api('DELETE', `/api/admin/users/${createdId}`, { token: adminToken });
  check('删除用户成功', delUser.code === 200, JSON.stringify(delUser));

  // ---------------------------------------------------------------- 汇总
  console.log(`\n${'='.repeat(60)}`);
  console.log(`结果: ${pass} 通过 / ${fail} 失败  (共 ${pass + fail} 项断言)`);
  if (failures.length) {
    console.log('\n失败项:');
    failures.forEach(f => console.log('  - ' + f));
  }
  console.log('='.repeat(60));
  process.exitCode = fail === 0 ? 0 : 1;
};

run().catch(e => {
  console.error('测试脚本异常:', e);
  process.exitCode = 1;
});
