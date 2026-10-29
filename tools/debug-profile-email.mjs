// 「学生修改本人邮箱」功能调试
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

const ORIGIN_1 = '2024001@student.school.edu.cn';
const OTHER_2 = '2024002@student.school.edu.cn';
const NEW_MAIL = 'lilei.new@example.com';

const s1 = await login('2024001');
const s2 = await login('2024002');
const s7 = await login('2024007');
const t1 = await login('t001');
const admin = await login('admin');
const adminToken = admin.data?.token;
const stuToken = s1.data?.token;

section('1. 初始状态');
const me0 = await call('GET', '/api/auth/me', { token: stuToken });
check('学生可读取本人档案', me0.code === 200, JSON.stringify(me0).slice(0, 120));
check('初始邮箱为种子邮箱', me0.data?.email === ORIGIN_1, me0.data?.email);
const s7me = await call('GET', '/api/auth/me', { token: s7.data?.token });
check('2024007 初始无邮箱（种子数据刻意留空）', !s7me.data?.email, String(s7me.data?.email));

section('2. 学生修改本人邮箱');
const upd = await call('PUT', '/api/auth/email', { token: stuToken, body: { email: NEW_MAIL } });
check('PUT /auth/email 成功', upd.code === 200, JSON.stringify(upd).slice(0, 160));
check('返回最新 ProfileVO 且邮箱已更新', upd.data?.email === NEW_MAIL, upd.data?.email);
check('返回体中仍不含密码字段', !('password' in (upd.data ?? {})));

const me1 = await call('GET', '/api/auth/me', { token: stuToken });
check('重新查询确认已持久化', me1.data?.email === NEW_MAIL, me1.data?.email);

section('3. 唯一性校验（防止收到他人成绩）');
const dupUser = await call('PUT', '/api/auth/email', { token: stuToken, body: { email: OTHER_2 } });
check('填写他人邮箱被拒（400）', dupUser.code === 400, `code=${dupUser.code} msg=${dupUser.message}`);
check('提示信息说明邮箱已被占用',
  String(dupUser.message).includes('已被其他账号使用'), dupUser.message);
const me2 = await call('GET', '/api/auth/me', { token: stuToken });
check('被拒后邮箱未被修改', me2.data?.email === NEW_MAIL, me2.data?.email);

const dupTeacher = await call('PUT', '/api/auth/email', { token: stuToken, body: { email: 't001@school.edu.cn' } });
check('填写教师邮箱同样被拒', dupTeacher.code === 400, `code=${dupTeacher.code}`);

section('4. 格式与参数校验');
for (const [label, value] of [
  ['非法格式 abc', 'abc'],
  ['缺少 @', 'abc.com'],
  ['缺少域名', 'abc@'],
  ['超长（>100 字符）', `${'a'.repeat(95)}@example.com`],
]) {
  const r = await call('PUT', '/api/auth/email', { token: stuToken, body: { email: value } });
  check(`${label} → 400`, r.code === 400, `code=${r.code} msg=${r.message}`);
}
const empty = await call('PUT', '/api/auth/email', { token: stuToken, body: { email: '' } });
check('空邮箱 → 400', empty.code === 400, `code=${empty.code} msg=${empty.message}`);
const missing = await call('PUT', '/api/auth/email', { token: stuToken, body: {} });
check('缺少 email 字段 → 400', missing.code === 400, `code=${missing.code} msg=${missing.message}`);
const me3 = await call('GET', '/api/auth/me', { token: stuToken });
check('以上非法请求均未改动邮箱', me3.data?.email === NEW_MAIL, me3.data?.email);

section('5. 重复提交同一邮箱（幂等）');
const same = await call('PUT', '/api/auth/email', { token: stuToken, body: { email: NEW_MAIL } });
check('重复提交相同邮箱返回 200', same.code === 200, `code=${same.code} msg=${same.message}`);

section('6. 权限');
const noToken = await call('PUT', '/api/auth/email', { body: { email: 'x@y.com' } });
check('无令牌 → 401', noToken.code === 401, `code=${noToken.code}`);
const badTok = await call('PUT', '/api/auth/email', { token: 'garbage', body: { email: 'x@y.com' } });
check('非法令牌 → 401', badTok.code === 401, `code=${badTok.code}`);

section('7. 其他角色也可维护本人邮箱');
const tUpd = await call('PUT', '/api/auth/email', { token: t1.data?.token, body: { email: 'zhangwei@school.edu.cn' } });
check('教师修改本人邮箱成功', tUpd.code === 200 && tUpd.data?.email === 'zhangwei@school.edu.cn',
  JSON.stringify(tUpd).slice(0, 140));
await call('PUT', '/api/auth/email', { token: t1.data?.token, body: { email: 't001@school.edu.cn' } });

const aUpd = await call('PUT', '/api/auth/email', { token: adminToken, body: { email: 'admin.new@school.edu.cn' } });
check('管理员修改本人邮箱成功', aUpd.code === 200 && aUpd.data?.email === 'admin.new@school.edu.cn',
  JSON.stringify(aUpd).slice(0, 140));
await call('PUT', '/api/auth/email', { token: adminToken, body: { email: 'admin@school.edu.cn' } });

section('8. 无邮箱学生补录邮箱');
const s7Upd = await call('PUT', '/api/auth/email', { token: s7.data?.token, body: { email: 'yangfan@example.com' } });
check('原本无邮箱的学生可补录', s7Upd.code === 200 && s7Upd.data?.email === 'yangfan@example.com',
  JSON.stringify(s7Upd).slice(0, 140));

section('9. 与预警邮件功能的联动');
const list = await call('GET', '/api/admin/warnings?page=1&size=50', { token: adminToken });
const records = list.data?.records ?? [];
const rec1 = records.find((r) => r.studentNo === '2024001');
const rec7 = records.find((r) => r.studentNo === '2024007');
check('预警名单中学生 2024001 的邮箱为修改后的新邮箱',
  rec1?.email === NEW_MAIL, `email=${rec1?.email}`);
check('预警名单中学生 2024007 已不再是"未登记邮箱"',
  rec7?.email === 'yangfan@example.com', `email=${rec7?.email}`);

const preview = await call('GET', `/api/admin/warnings/preview/${rec1?.studentId}`, { token: adminToken });
check('邮件预览使用新邮箱作为收件地址', preview.data?.email === NEW_MAIL, preview.data?.email);

section('10. 管理端用户编辑的邮箱唯一性');
const uid = rec1?.userId;
const adminDup = await call('PUT', `/api/admin/users/${uid}`, {
  token: adminToken,
  body: {
    username: '2024001', realName: '李雷', role: 'STUDENT', status: 1,
    studentNo: '2024001', gender: '男', clazzId: 1, email: OTHER_2,
  },
});
check('管理员把学生邮箱改成他人邮箱 → 400',
  adminDup.code === 400 && String(adminDup.message).includes('已被其他账号使用'),
  `code=${adminDup.code} msg=${adminDup.message}`);

const adminBad = await call('PUT', `/api/admin/users/${uid}`, {
  token: adminToken,
  body: {
    username: '2024001', realName: '李雷', role: 'STUDENT', status: 1,
    studentNo: '2024001', gender: '男', clazzId: 1, email: 'not-an-email',
  },
});
check('管理员填非法邮箱 → 400', adminBad.code === 400, `code=${adminBad.code} msg=${adminBad.message}`);

const adminOk = await call('PUT', `/api/admin/users/${uid}`, {
  token: adminToken,
  body: {
    username: '2024001', realName: '李雷', role: 'STUDENT', status: 1,
    studentNo: '2024001', gender: '男', clazzId: 1, email: ORIGIN_1,
  },
});
check('管理员改回原邮箱成功', adminOk.code === 200 && adminOk.data?.email === ORIGIN_1,
  JSON.stringify(adminOk).slice(0, 140));

section('11. 还原测试数据');
const restore7 = await call('PUT', '/api/admin/users/' + (rec7?.userId ?? 0), {
  token: adminToken,
  body: {
    username: '2024007', realName: '杨帆', role: 'STUDENT', status: 1,
    studentNo: '2024007', gender: '男', clazzId: 1, email: '',
  },
});
check('已尝试将 2024007 邮箱还原为空', restore7.code === 200, JSON.stringify(restore7).slice(0, 140));

console.log(`\n${'='.repeat(60)}`);
console.log(`本人邮箱维护调试: ${pass} 通过 / ${fail} 失败  (共 ${pass + fail} 项)`);
if (failures.length) {
  console.log('\n失败项:');
  failures.forEach((f) => console.log('  - ' + f));
}
console.log('='.repeat(60));
process.exitCode = fail === 0 ? 0 : 1;
