// 调试 Vite 开发服务器：确认 SPA 可加载、依赖可解析、/api 代理可达后端。
const ORIGIN = process.env.ORIGIN ?? 'http://127.0.0.1:5174';

let pass = 0;
let fail = 0;
const check = (name, cond, detail = '') => {
  if (cond) {
    pass++;
    console.log(`  PASS  ${name}`);
  } else {
    fail++;
    console.log(`  FAIL  ${name}  ${detail}`);
  }
};

async function get(path, opts) {
  const res = await fetch(ORIGIN + path, opts);
  const text = await res.text();
  return { status: res.status, text, type: res.headers.get('content-type') ?? '' };
}

console.log(`=== 调试目标: ${ORIGIN} ===\n`);

console.log('=== 1. 入口 HTML ===');
const home = await get('/');
check('GET / 返回 200', home.status === 200, `status=${home.status}`);
check('Content-Type 为 HTML', home.type.includes('text/html'), home.type);
check('注入了 Vite 客户端', home.text.includes('@vite/client'));
const entry = (home.text.match(/src="(\/src\/main\.js[^"]*)"/) ?? [])[1];
check('入口指向 /src/main.js', !!entry, entry ?? '(未找到)');

console.log('\n=== 2. 入口模块与依赖改写 ===');
const main = await get('/src/main.js');
check('GET /src/main.js 返回 200', main.status === 200, `status=${main.status}`);
check('返回 JS 而非 HTML 兜底', main.type.includes('javascript'), main.type);
const specs = [...main.text.matchAll(/from\s+"([^"]+)"/g)].map((m) => m[1]);
console.log('    改写后的导入:', specs.join(', ') || '(无)');
check('裸模块已被改写为可解析路径',
  specs.length > 0 && specs.every((s) => s.startsWith('/') || s.startsWith('.')),
  specs.join(', '));

console.log('\n=== 3. 逐个抓取依赖（noDiscovery 模式下是否可原生加载） ===');
const toFetch = specs.filter((s) => s.startsWith('/'));
for (const spec of toFetch.slice(0, 8)) {
  const r = await get(spec);
  check(`GET ${spec}`, r.status === 200 && r.type.includes('javascript'),
    `status=${r.status} type=${r.type}`);
}

console.log('\n=== 4. Vue 单文件组件编译 ===');
const sfc = await get('/src/views/login/LoginView.vue');
check('GET LoginView.vue 返回 200', sfc.status === 200, `status=${sfc.status}`);
check('SFC 已编译为 JS', sfc.type.includes('javascript') && !sfc.text.includes('<template>'),
  `type=${sfc.type}`);
check('SFC 编译无报错信息', !sfc.text.includes('Internal server error') && !sfc.text.includes('Pre-transform error'));

const layout = await get('/src/layouts/MainLayout.vue');
check('GET MainLayout.vue 返回 200', layout.status === 200, `status=${layout.status}`);

console.log('\n=== 5. /api 代理到后端 8080 ===');
const login = await get('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
});
let lj = null;
try { lj = JSON.parse(login.text); } catch { /* 忽略 */ }
check('经代理 POST /api/auth/login 成功', lj?.code === 200, `status=${login.status} body=${login.text.slice(0, 120)}`);
check('代理返回 JSON 而非 HTML', login.type.includes('application/json'), login.type);
const token = lj?.data?.token;

if (token) {
  const meta = await get('/api/meta/selection-window', { headers: { Authorization: `Bearer ${token}` } });
  let mj = null;
  try { mj = JSON.parse(meta.text); } catch { /* 忽略 */ }
  check('经代理 GET /api/meta/selection-window', mj?.code === 200 && mj.data?.open === true,
    `body=${meta.text.slice(0, 140)}`);
}

const spaRoute = await get('/student/courses');
check('开发服务器直接返回 index.html（由前端路由接管）',
  spaRoute.status === 200 && spaRoute.text.includes('@vite/client'), `status=${spaRoute.status}`);

console.log(`\n${'='.repeat(56)}`);
console.log(`开发服务器调试: ${pass} 通过 / ${fail} 失败`);
console.log('='.repeat(56));
process.exitCode = fail === 0 ? 0 : 1;
