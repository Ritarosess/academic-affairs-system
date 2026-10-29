// 单端口集成验证：确认 SPA 页面、前端路由回退、静态资源与 REST 接口
// 由同一个 Spring Boot 端口（8080）统一提供。
const BASE = process.env.BASE ?? 'http://127.0.0.1:8080';

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

console.log('=== 1. SPA 首页 ===');
const home = await fetch(`${BASE}/`);
const html = await home.text();
check('GET / 返回 200', home.status === 200, `status=${home.status}`);
check('响应类型为 HTML', (home.headers.get('content-type') ?? '').includes('text/html'));
check('包含 #app 挂载点', html.includes('id="app"'));
check('引用了打包后的 JS', /assets\/index-.*\.js/.test(html));
check('引用了打包后的 CSS', /assets\/index-.*\.css/.test(html));
const title = (html.match(/<title>(.*?)<\/title>/) ?? [])[1];
check('页面标题为中文且未乱码', title === '高校教务系统', `title=${title}`);

console.log('\n=== 2. 前端路由回退（SpaWebFilter） ===');
for (const path of ['/login', '/student/courses', '/teacher/courses', '/admin/users', '/profile', '/no-such-page']) {
  const res = await fetch(`${BASE}${path}`);
  const body = await res.text();
  check(`GET ${path} 回退到 index.html`, res.status === 200 && body.includes('id="app"'),
    `status=${res.status}`);
}

console.log('\n=== 3. 静态资源可访问 ===');
const jsPath = (html.match(/src="(\/assets\/index-[^"]+\.js)"/) ?? [])[1];
const cssPath = (html.match(/href="(\/assets\/index-[^"]+\.css)"/) ?? [])[1];
if (jsPath) {
  const r = await fetch(`${BASE}${jsPath}`);
  const len = (await r.arrayBuffer()).byteLength;
  check(`GET ${jsPath}`, r.status === 200 && len > 100000, `status=${r.status} bytes=${len}`);
} else {
  check('找到 JS 资源引用', false);
}
if (cssPath) {
  const r = await fetch(`${BASE}${cssPath}`);
  const len = (await r.arrayBuffer()).byteLength;
  check(`GET ${cssPath}`, r.status === 200 && len > 1000, `status=${r.status} bytes=${len}`);
} else {
  check('找到 CSS 资源引用', false);
}

console.log('\n=== 4. 同端口的 REST 接口 ===');
const login = await fetch(`${BASE}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: '123456' }),
});
const lj = await login.json();
check('POST /api/auth/login 成功', lj.code === 200 && lj.data?.user?.role === 'ADMIN',
  JSON.stringify(lj).slice(0, 160));
const token = lj.data?.token;

const users = await fetch(`${BASE}/api/admin/users?role=STUDENT&page=1&size=5`, {
  headers: { Authorization: `Bearer ${token}` },
});
const uj = await users.json();
check('GET /api/admin/users 带令牌成功', uj.code === 200 && uj.data?.total === 26, `total=${uj.data?.total}`);

const nope = await fetch(`${BASE}/api/admin/users`);
const nj = await nope.json();
check('无令牌访问受保护接口返回 401', nope.status === 401 && nj.code === 401,
  `status=${nope.status} code=${nj.code}`);

console.log(`\n${'='.repeat(56)}`);
console.log(`集成验证: ${pass} 通过 / ${fail} 失败`);
console.log('='.repeat(56));
process.exitCode = fail === 0 ? 0 : 1;
