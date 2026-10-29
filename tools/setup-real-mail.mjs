// 将管理员邮箱改为真实地址并执行邮件自检（会真实投递到该邮箱）
const BASE = 'http://127.0.0.1:8080';

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

const REAL_EMAIL = process.env.REAL_EMAIL;
if (!REAL_EMAIL) {
  console.error('请先指定收件邮箱，例如：REAL_EMAIL=you@example.com node tools/setup-real-mail.mjs');
  process.exit(1);
}

console.log('=== 1. 登录管理员 ===');
const login = await call('POST', '/api/auth/login', { body: { username: 'admin', password: '123456' } });
const token = login.data?.token;
console.log(`  code=${login.code} role=${login.data?.user?.role}`);
if (!token) { console.error('登录失败'); process.exit(1); }

console.log('\n=== 2. 当前档案 ===');
const me0 = await call('GET', '/api/auth/me', { token });
console.log(`  当前邮箱: ${me0.data?.email}`);

console.log('\n=== 3. 将管理员邮箱改为真实地址（走"维护本人邮箱"接口）===');
const upd = await call('PUT', '/api/auth/email', { token, body: { email: REAL_EMAIL } });
console.log(`  code=${upd.code} message=${upd.message}`);
console.log(`  新邮箱: ${upd.data?.email}`);
if (upd.code !== 200) { console.error('邮箱修改失败'); process.exit(1); }

const me1 = await call('GET', '/api/auth/me', { token });
console.log(`  重新查询确认: ${me1.data?.email}`);

console.log('\n=== 4. 查看当前 SMTP 配置 ===');
const cfg = await call('GET', '/api/admin/warnings/mail-config', { token });
console.log(`  mailEnabled : ${cfg.data?.mailEnabled}`);
console.log(`  host        : ${cfg.data?.host}:${cfg.data?.port}`);
console.log(`  username    : ${cfg.data?.username}`);
console.log(`  from        : ${cfg.data?.from}`);

console.log('\n=== 5. 邮件自检（真实投递）===');
console.log('  正在连接 smtp.qq.com:465 ...');
const t0 = Date.now();
const test = await call('POST', '/api/admin/warnings/test-mail', { token });
const cost = Date.now() - t0;
console.log(`  耗时: ${cost} ms`);
console.log(`  success : ${test.data?.success}`);
console.log(`  to      : ${test.data?.to}`);
console.log(`  message : ${test.data?.message}`);
if (test.data?.hint) console.log(`  hint    : ${test.data?.hint}`);

console.log('\n' + '='.repeat(64));
if (test.data?.success) {
  console.log('结果：测试邮件已成功投递到 ' + test.data.to);
  console.log('请到该邮箱（含垃圾邮件箱）查收，主题为「【学业预警】邮件发送测试」。');
} else {
  console.log('结果：发送失败，需按 hint 排查。');
}
console.log('='.repeat(64));
process.exitCode = test.data?.success ? 0 : 1;
