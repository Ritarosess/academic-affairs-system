// 验证「学业预警邮件」的真实投递：临时把一名红色预警学生的邮箱指向真实地址，
// 发送后再还原，最后把管理员邮箱设为真实地址（供「邮件自检」开箱可用）。
const BASE = 'http://127.0.0.1:8080';
const REAL_EMAIL = process.env.REAL_EMAIL;
if (!REAL_EMAIL) {
  console.error('请先指定收件邮箱，例如：REAL_EMAIL=you@example.com node tools/verify-real-warning-mail.mjs');
  process.exit(1);
}
const SEED_ADMIN = 'admin@school.edu.cn';
const SEED_STUDENT = '2024001@student.school.edu.cn';

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

const adminToken = (await call('POST', '/api/auth/login', { body: { username: 'admin', password: '123456' } })).data?.token;
const stuToken = (await call('POST', '/api/auth/login', { body: { username: '2024001', password: '123456' } })).data?.token;
const adminId = (await call('GET', '/api/auth/me', { token: adminToken })).data?.userId;

console.log('=== 准备：腾出真实邮箱地址 ===');
// 学生邮箱唯一性不允许与管理员重复，先把管理员改回种子地址
let r = await call('PUT', '/api/auth/email', { token: adminToken, body: { email: SEED_ADMIN } });
console.log(`  管理员邮箱 -> ${SEED_ADMIN}  (code=${r.code})`);

// 把红色预警学生 2024001（李雷）的邮箱指向真实地址
r = await call('PUT', '/api/auth/email', { token: stuToken, body: { email: REAL_EMAIL } });
console.log(`  学生 2024001 邮箱 -> ${REAL_EMAIL}  (code=${r.code}) ${r.message}`);
if (r.code !== 200) { console.error('  失败，终止'); process.exit(1); }

console.log('\n=== 查看该学生的预警情况 ===');
const list = await call('GET', '/api/admin/warnings?keyword=2024001', { token: adminToken });
const stu = list.data?.records?.[0];
console.log(`  ${stu?.studentNo} ${stu?.studentName}  等级=${stu?.levelLabel}  累计不及格=${stu?.failedCredits} 学分  邮箱=${stu?.email}`);
if (!stu) { console.error('  未找到该学生'); process.exit(1); }

console.log('\n=== 发送真实的学业预警邮件通知 ===');
console.log('  正在连接 smtp.qq.com:465 ...');
const t0 = Date.now();
const send = await call('POST', '/api/admin/warnings/notify', {
  token: adminToken,
  body: { studentIds: [stu.studentId] },
});
const cost = Date.now() - t0;
const item = send.data?.results?.[0];
console.log(`  耗时      : ${cost} ms`);
console.log(`  汇总      : 共 ${send.data?.total} / 已发送 ${send.data?.sent} / 失败 ${send.data?.failed} / 跳过 ${send.data?.skipped}`);
console.log(`  该生结果  : ${item?.statusLabel} - ${item?.message}`);
console.log(`  邮件主题  : ${item?.subject}`);

console.log('\n=== 发送历史（已落库）===');
const hist = await call('GET', '/api/admin/warnings/notifications?page=1&size=5', { token: adminToken });
for (const h of (hist.data?.records ?? [])) {
  console.log(`  ${h.sendTime}  ${h.studentNo}  ${h.email}  ${h.statusLabel}  ${h.errorMessage ?? ''}`);
}

console.log('\n=== 还原：学生邮箱恢复为种子值，管理员设为真实地址 ===');
r = await call('PUT', '/api/auth/email', { token: stuToken, body: { email: SEED_STUDENT } });
console.log(`  学生 2024001 -> ${SEED_STUDENT}  (code=${r.code})`);
r = await call('PUT', '/api/auth/email', { token: adminToken, body: { email: REAL_EMAIL } });
console.log(`  管理员 -> ${REAL_EMAIL}  (code=${r.code})`);

const finalMe = await call('GET', '/api/auth/me', { token: adminToken });
console.log(`\n最终管理员邮箱: ${finalMe.data?.email}`);

console.log('\n' + '='.repeat(64));
const ok = send.data?.sent === 1 && send.data?.failed === 0;
console.log(ok
  ? `结果：真实学业预警邮件已投递到 ${REAL_EMAIL}，请查收（主题含"学业情况通知"）。`
  : '结果：发送未成功，请查看上方的失败原因。');
console.log('='.repeat(64));
process.exitCode = ok ? 0 : 1;
