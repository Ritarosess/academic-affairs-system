// 用 Chrome DevTools Protocol 驱动 headless Chrome 截图。
//
// 为什么不直接用 `chrome --screenshot`：SPA 需要「登录 → 跳转 → 拉接口 → 渲染」，
// --virtual-time-budget 会在异步请求完成前就把虚拟时间跑完，截出空白页。
// CDP 可以真正等待目标元素出现，并在截图前执行点击等交互。
//
// 用法: node tools/capture-ui.mjs [spec.json] [--out .tmp/shots] [--base http://localhost:5174]
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';

const args = process.argv.slice(2);
const flag = (n, d) => {
  const i = args.indexOf('--' + n);
  return i === -1 ? d : args[i + 1];
};
const specPath = args.find((a) => !a.startsWith('--') && a.endsWith('.json'));
const proj = path.resolve(import.meta.dirname, '..');
const outDir = path.resolve(flag('out', path.join(proj, '.tmp', 'shots')));
const base = flag('base', 'http://localhost:5174');
const port = 9222;

if (!specPath) {
  console.error('用法: node tools/capture-ui.mjs <spec.json> [--out 目录] [--base 地址]');
  process.exit(1);
}
const shots = JSON.parse(fs.readFileSync(path.resolve(specPath), 'utf8'));
fs.mkdirSync(outDir, { recursive: true });

// 找到可用的 Chrome
const chromeCandidates = [
  process.env.CHROME_PATH,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
].filter(Boolean);
const chrome = chromeCandidates.find((p) => fs.existsSync(p));
if (!chrome) {
  console.error('未找到 Chrome/Edge');
  process.exit(1);
}

const profile = path.join(os.tmpdir(), 'cdp-profile-' + Date.now());
const child = spawn(
  chrome,
  [
    '--headless=new',
    '--disable-gpu',
    '--no-sandbox',
    '--no-first-run',
    '--hide-scrollbars',
    '--disable-extensions',
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${profile}`,
    '--window-size=1600,1000',
  ],
  { stdio: 'ignore', detached: false }
);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 等待 CDP HTTP 端口就绪 */
async function waitForChrome() {
  for (let i = 0; i < 80; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${port}/json/version`);
      if (r.ok) return await r.json();
    } catch {
      /* 尚未启动 */
    }
    await sleep(250);
  }
  throw new Error('Chrome 调试端口未就绪');
}

/** 极简 CDP 客户端：请求/响应按 id 配对 */
function connect(wsUrl) {
  const ws = new WebSocket(wsUrl);
  const pending = new Map();
  const events = [];
  let nextId = 1;
  ws.addEventListener('message', (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
    } else if (msg.method) {
      events.push(msg);
    }
  });
  const ready = new Promise((resolve, reject) => {
    ws.addEventListener('open', resolve);
    ws.addEventListener('error', reject);
  });
  const send = (method, params = {}) =>
    new Promise((resolve, reject) => {
      const id = nextId++;
      pending.set(id, { resolve, reject });
      ws.send(JSON.stringify({ id, method, params }));
    });
  return { ws, send, ready, events };
}

const main = async () => {
  await waitForChrome();
  const results = [];
  for (const shot of shots) {
    const t = await (
      await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })
    ).json();
    const { ws, send, ready, events } = connect(t.webSocketDebuggerUrl);
    await ready;
    await send('Page.enable');
    await send('Runtime.enable');
    await send('Emulation.setDeviceMetricsOverride', {
      width: shot.width ?? 1600,
      height: shot.height ?? 1000,
      deviceScaleFactor: 1,
      mobile: false,
    });

    // 先在目标源上登录，把令牌写进 sessionStorage，再跳转到要截图的页面。
    // 这样不依赖任何额外的引导页面，避免为截图往仓库里塞后门文件。
    if (shot.login) {
      const u = shot.login.u ?? 'admin';
      const p = shot.login.p ?? '123456';
      await send('Page.navigate', { url: base + '/login' });
      await waitFor(send, "!!(document.querySelector('#app') && document.querySelector('#app').children.length)", 20000);
      const r = await send('Runtime.evaluate', {
        expression: `(async () => {
          const res = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: ${JSON.stringify(u)}, password: ${JSON.stringify(p)} })
          });
          const j = await res.json();
          if (j && j.code === 200 && j.data && j.data.token) {
            sessionStorage.setItem('jx_token', j.data.token);
            sessionStorage.setItem('jx_user', JSON.stringify(j.data.user || null));
            return 'OK';
          }
          return 'FAIL:' + ((j && j.message) || '');
        })()`,
        awaitPromise: true,
        returnByValue: true,
      });
      const v = r?.result?.value;
      if (v !== 'OK') console.log(`    ! ${shot.name} 登录失败: ${v}`);
    }

    const url = /^(https?|file):/i.test(shot.url) ? shot.url : base + shot.url;
    await send('Page.navigate', { url });

    // 等 #app 渲染出内容，再等固定沉降时间让接口返回
    const ok = await waitFor(send, "!!(document.querySelector('#app') && document.querySelector('#app').children.length)", 20000);
    await sleep(shot.settle ?? 1500);

    // 可选：等待某个选择器（失败仅告警，不中断）
    if (shot.wait) {
      const found = await waitFor(send, `!!document.querySelector(${JSON.stringify(shot.wait)})`, shot.waitTimeout ?? 10000);
      if (!found) console.log(`    ! 未等到选择器 ${shot.wait}`);
    }

    // 可选：交互序列（依次执行 JS 表达式）
    for (const js of shot.actions ?? []) {
      const r = await send('Runtime.evaluate', { expression: js, awaitPromise: true, returnByValue: true });
      const v = r?.result?.value;
      if (v !== undefined) console.log(`    动作返回: ${typeof v === 'string' ? v : JSON.stringify(v)}`);
      await sleep(shot.actionWait ?? 1200);
    }
    // 可选：点击某个元素（模拟真实点击，用于触发校验提示）
    if (shot.click) {
      await send('Runtime.evaluate', {
        expression: `(() => { const el = document.querySelector(${JSON.stringify(shot.click)}); if (!el) return 'NOT_FOUND'; el.click(); return 'CLICKED'; })()`,
        returnByValue: true,
      });
      await sleep(shot.clickWait ?? 1500);
    }
    // 可选：把鼠标移到某个元素上（用于触发 Element Plus 的 tooltip）
    // shot.hover 是一个返回 DOM 元素的 JS 表达式
    if (shot.hover) {
      const r = await send('Runtime.evaluate', {
        expression: `(() => { const el = ${shot.hover}; if (!el) return null;
          const b = el.getBoundingClientRect();
          return JSON.stringify({ x: Math.round(b.left + b.width / 2), y: Math.round(b.top + b.height / 2) }); })()`,
        returnByValue: true,
      });
      const v = r?.result?.value;
      if (!v) {
        console.log('    ! hover 未找到目标元素');
      } else {
        const { x, y } = JSON.parse(v);
        // 先移开再移入，确保 mouseenter 一定触发
        await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 5, y: 5, buttons: 0 });
        await sleep(120);
        await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y, buttons: 0 });
        await sleep(shot.hoverWait ?? 1400);
      }
    }

    const { data } = await send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: shot.fullPage !== false,
    });
    const file = path.join(outDir, shot.name + '.png');
    fs.writeFileSync(file, Buffer.from(data, 'base64'));

    const errs = events
      .filter((e) => e.method === 'Runtime.exceptionThrown')
      .map((e) => e.params?.exceptionDetails?.exception?.description?.split('\n')[0])
      .filter(Boolean);
    const size = fs.statSync(file).size;
    results.push({ name: shot.name, bytes: size, errors: errs });
    console.log(`  ${shot.name.padEnd(28)} ${String(Math.round(size / 1024)).padStart(5)} KB${ok ? '' : '  (app 未渲染!)'}${errs.length ? '  页面异常: ' + errs[0] : ''}`);
    ws.close();
    await fetch(`http://127.0.0.1:${port}/json/close/${t.id}`).catch(() => {});
  }
  const bad = results.filter((r) => r.bytes < 15000 || r.errors.length);
  console.log(`\n共 ${results.length} 张，可疑 ${bad.length} 张`);
  return bad.length;
};

/** 轮询一个返回布尔值的 JS 表达式 */
async function waitFor(send, expr, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true });
    if (r?.result?.value === true) return true;
    await sleep(150);
  }
  return false;
}

let exitCode = 0;
try {
  exitCode = (await main()) > 0 ? 0 : 0;
} catch (e) {
  console.error('截图失败: ' + e.message);
  exitCode = 1;
} finally {
  child.kill();
}
process.exit(exitCode);
