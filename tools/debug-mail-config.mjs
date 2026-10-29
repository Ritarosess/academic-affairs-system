// 邮件配置自检功能验证：/api/admin/warnings/test-mail 与 /mail-config
// 可在模拟模式与真实模式下分别运行，脚本会自动识别当前模式。
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

const admin = await login('admin');
const adminToken = admin.data?.token;
const adminMe = await call('GET', '/api/auth/me', { token: adminToken });
const ADMIN_EMAIL = adminMe.data?.email;
const stuToken = (await login('2024001')).data?.token;
const teaToken = (await login('t001')).data?.token;

section('1. SMTP 配置摘要');
const cfg = await call('GET', '/api/admin/warnings/mail-config', { token: adminToken });
check('GET /mail-config 成功', cfg.code === 200, JSON.stringify(cfg).slice(0, 160));
const MAIL_ON = cfg.data?.mailEnabled === true;
console.log(`    当前模式：${MAIL_ON ? '真实发送' : '模拟发送'}`);
console.log(`    配置：host=${cfg.data?.host} port=${cfg.data?.port} user=${cfg.data?.username || '(未配置)'} from=${cfg.data?.from}`);
check('返回 host', !!cfg.data?.host, cfg.data?.host);
check('返回 port', !!cfg.data?.port, cfg.data?.port);
check('返回 from', !!cfg.data?.from, cfg.data?.from);
check('响应中不含密码字段', !('password' in (cfg.data ?? {})) && !JSON.stringify(cfg.data).includes('MAIL_PASSWORD'));
check('mailEnabled 为布尔值', typeof cfg.data?.mailEnabled === 'boolean');

section('2. 发件人回退逻辑（关键：QQ/163 要求发件人=登录账号）');
// 预警总览返回的 mailFrom 必须与实际生效的发件人一致（曾出现返回原始配置导致空值的缺陷）
const warnSummary = await call('GET', '/api/admin/warnings/summary', { token: adminToken });
check('预警总览的 mailFrom 与实际生效发件人一致',
  warnSummary.data?.mailFrom === cfg.data?.from,
  `summary=${warnSummary.data?.mailFrom} config=${cfg.data?.from}`);
check('预警总览的 mailFrom 非空',
  !!warnSummary.data?.mailFrom, String(warnSummary.data?.mailFrom));

if (cfg.data?.username) {
  check('配置了登录账号时，发件人自动取登录账号（from 留空不再用 noreply@school.edu.cn）',
    cfg.data.from === cfg.data.username, `from=${cfg.data.from} user=${cfg.data.username}`);
} else {
  check('未配置登录账号时使用占位发件人',
    cfg.data.from === 'noreply@school.edu.cn', cfg.data.from);
}

section('3. 邮件自检接口');
const test = await call('POST', '/api/admin/warnings/test-mail', { token: adminToken });
check('POST /test-mail 返回 200（无论发送成败）', test.status === 200 && test.code === 200,
  `status=${test.status} code=${test.code}`);
const d = test.data ?? {};
check('返回配置摘要', !!d.host && !!d.port, JSON.stringify(d).slice(0, 160));
check('返回收件人（管理员本人邮箱）', d.to === ADMIN_EMAIL, `${d.to} vs ${ADMIN_EMAIL}`);
check('返回 success 布尔值', typeof d.success === 'boolean', String(d.success));
console.log(`    结果：success=${d.success} message=${d.message}`);
if (d.hint) console.log(`    提示：${d.hint}`);

if (MAIL_ON) {
  check('真实模式下发件人为登录账号本身', d.from === d.username, `from=${d.from}`);
  check('真实模式且 SMTP 可用时发送成功', d.success === true, d.message);
} else {
  check('模拟模式下发件人回退为占位地址', d.from === 'noreply@school.edu.cn', d.from);
  check('模拟模式下明确提示不会真实投递',
    d.success === false && String(d.message).includes('模拟发送模式'), d.message);
  check('模拟模式的提示中给出开启方法',
    String(d.message).includes('app.mail.enabled'), d.message);
}

section('4. 权限');
const stu = await call('POST', '/api/admin/warnings/test-mail', { token: stuToken });
check('学生调用 → 403', stu.code === 403, `code=${stu.code}`);
const tea = await call('GET', '/api/admin/warnings/mail-config', { token: teaToken });
check('教师调用配置接口 → 403', tea.code === 403, `code=${tea.code}`);
const anon = await call('POST', '/api/admin/warnings/test-mail');
check('无令牌 → 401', anon.code === 401, `code=${anon.code}`);

console.log(`\n${'='.repeat(62)}`);
console.log(`邮件自检功能验证: ${pass} 通过 / ${fail} 失败  (共 ${pass + fail} 项)`);
if (failures.length) {
  console.log('\n失败项:');
  failures.forEach((f) => console.log('  - ' + f));
}
console.log('='.repeat(62));
process.exitCode = fail === 0 ? 0 : 1;
