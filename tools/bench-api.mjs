// 接口性能测试：对核心接口做顺序延迟测量 + 并发吞吐测量，
// 输出 JSON 结果并生成一张自包含的图表页（供截图作为测试报告插图）。
//
// 用法: node tools/bench-api.mjs [--base http://localhost:8081] [--n 30]
import fs from 'node:fs';
import path from 'node:path';

const args = process.argv.slice(2);
const flag = (n, d) => {
  const i = args.indexOf('--' + n);
  return i === -1 ? d : args[i + 1];
};
const BASE = flag('base', process.env.BASE ?? 'http://localhost:8081');
const N = Number(flag('n', 30));
const proj = path.resolve(import.meta.dirname, '..');

async function call(method, p, { token, body } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = 'Bearer ' + token;
  const res = await fetch(BASE + p, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let json = null;
  try {
    json = await res.json();
  } catch {
    json = { code: -1 };
  }
  return json;
}

const login = async (u, p = '123456') => (await call('POST', '/api/auth/login', { body: { username: u, password: p } })).data?.token;

/** 顺序执行 n 次，返回各次耗时（毫秒） */
async function measure(fn, n) {
  const times = [];
  for (let i = 0; i < n; i++) {
    const t0 = performance.now();
    await fn(i);
    times.push(performance.now() - t0);
  }
  return times;
}

const pct = (arr, p) => {
  const s = [...arr].sort((a, b) => a - b);
  const idx = Math.min(s.length - 1, Math.max(0, Math.ceil((p / 100) * s.length) - 1));
  return s[idx];
};
const avg = (arr) => arr.reduce((a, b) => a + b, 0) / arr.length;

const main = async () => {
  const admin = await login('admin');
  const student = await login('2024003');
  if (!admin || !student) throw new Error('登录失败，无法开始性能测试');

  const scenarios = [
    ['登录认证', 'POST /api/auth/login', (i) => call('POST', '/api/auth/login', { body: { username: '2024003', password: '123456' } })],
    ['课程模糊分页检索', 'GET /api/admin/courses?keyword=计算机', () => call('GET', '/api/admin/courses?page=1&size=10&keyword=' + encodeURIComponent('计算机'), { token: admin })],
    ['选课大厅列表', 'GET /api/student/courses', () => call('GET', '/api/student/courses?page=1&size=10', { token: student })],
    ['我的课表', 'GET /api/student/selections', () => call('GET', '/api/student/selections', { token: student })],
    ['我的成绩', 'GET /api/student/scores', () => call('GET', '/api/student/scores', { token: student })],
    ['学业预警计算', 'GET /api/admin/warnings', () => call('GET', '/api/admin/warnings?page=1&size=10', { token: admin })],
    ['预警汇总统计', 'GET /api/admin/warnings/summary', () => call('GET', '/api/admin/warnings/summary', { token: admin })],
  ];

  // 预热，避免首请求的类加载/JIT 影响统计
  for (const [, , fn] of scenarios) await fn(0);

  const rows = [];
  for (const [name, ep, fn] of scenarios) {
    const times = await measure(fn, N);
    rows.push({
      name, ep,
      n: times.length,
      min: Math.min(...times),
      avg: avg(times),
      p50: pct(times, 50),
      p95: pct(times, 95),
      max: Math.max(...times),
    });
    const r = rows[rows.length - 1];
    console.log(`${name.padEnd(18)} 平均 ${r.avg.toFixed(1).padStart(6)} ms   P95 ${r.p95.toFixed(1).padStart(6)} ms   最大 ${r.max.toFixed(1).padStart(6)} ms`);
  }

  // 选课事务（写路径）：选中后立即退选，测一次完整事务往返
  const txTimes = await measure(async () => {
    await call('POST', '/api/student/selections', { token: student, body: { courseId: 4 } });
    await call('DELETE', '/api/student/selections/4', { token: student });
  }, Math.min(15, N));
  const tx = { name: '选课+退课事务', n: txTimes.length, min: Math.min(...txTimes), avg: avg(txTimes), p50: pct(txTimes, 50), p95: pct(txTimes, 95), max: Math.max(...txTimes) };
  console.log(`${tx.name.padEnd(16)} 平均 ${tx.avg.toFixed(1).padStart(6)} ms   P95 ${tx.p95.toFixed(1).padStart(6)} ms   最大 ${tx.max.toFixed(1).padStart(6)} ms`);

  // 并发吞吐：100 个并发请求打同一个只读接口
  const CONC = 100;
  const t0 = performance.now();
  const results = await Promise.all(
    Array.from({ length: CONC }, () => call('GET', '/api/admin/courses?page=1&size=10', { token: admin })),
  );
  const wall = performance.now() - t0;
  const okCount = results.filter((r) => r.code === 200).length;
  const conc = { concurrency: CONC, ok: okCount, wallMs: wall, qps: (CONC / wall) * 1000 };
  console.log(`\n并发 ${CONC} 请求: 全部完成耗时 ${wall.toFixed(0)} ms，成功 ${okCount}/${CONC}，吞吐约 ${conc.qps.toFixed(1)} req/s`);

  const meta = {
    base: BASE, iterations: N, concurrency: CONC, time: new Date().toISOString(),
    os: process.platform, node: process.version,
  };
  fs.writeFileSync(path.join(proj, '.tmp', 'bench-result.json'), JSON.stringify({ meta, rows, tx, conc }, null, 2));
  fs.writeFileSync(path.join(proj, '.tmp', 'bench-chart.html'), html(rows, tx, conc, meta));
  console.log('\n已生成 .tmp/bench-result.json 与 .tmp/bench-chart.html');
};

const bar = (v, max, color) => {
  const w = Math.max(2, Math.round((v / max) * 100));
  return `<div class="bar"><span style="width:${w}%;background:${color}"></span></div>`;
};

function html(rows, tx, conc, meta) {
  const all = [...rows, { ...tx, ep: 'POST + DELETE /api/student/selections' }];
  const maxAvg = Math.max(...all.map((r) => r.avg));
  const tr = all.map((r, i) => `
      <tr>
        <td class="idx">${i + 1}</td>
        <td class="name">${r.name}</td>
        <td class="ep">${r.ep}</td>
        <td class="num">${r.n}</td>
        <td class="num">${r.min.toFixed(1)}</td>
        <td class="num strong">${r.avg.toFixed(1)}</td>
        <td class="num">${r.p95.toFixed(1)}</td>
        <td class="num">${r.max.toFixed(1)}</td>
        <td class="chartcell">${bar(r.avg, maxAvg, i === all.length - 1 ? '#e6a23c' : '#409eff')}</td>
      </tr>`).join('');
  return `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>接口性能测试结果</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 0; padding: 28px 34px 34px; font-family: "Microsoft YaHei", "微软雅黑", sans-serif; background: #fff; color: #1f2329; }
  h1 { font-size: 25px; margin: 0 0 6px; }
  .sub { color: #6b7280; font-size: 13px; margin-bottom: 18px; }
  .meta { display: flex; gap: 26px; flex-wrap: wrap; background: #f5f8fc; border: 1px solid #dbe6f3; border-radius: 6px; padding: 11px 16px; font-size: 13px; margin-bottom: 20px; }
  .meta b { color: #1f4e9c; }
  table { width: 100%; border-collapse: collapse; font-size: 13px; }
  th { background: #deeaf6; border: 1px solid #9db2ce; padding: 8px 9px; font-weight: 600; text-align: center; }
  td { border: 1px solid #c9d6e8; padding: 7px 9px; vertical-align: middle; }
  .idx { text-align: center; color: #6b7280; width: 40px; }
  .name { font-weight: 600; white-space: nowrap; }
  .ep { color: #4b5563; font-family: Consolas, monospace; font-size: 12px; }
  .num { text-align: right; width: 74px; font-variant-numeric: tabular-nums; }
  .strong { font-weight: 700; color: #1f4e9c; }
  .chartcell { width: 190px; }
  .bar { background: #eef2f7; border-radius: 3px; height: 15px; overflow: hidden; }
  .bar span { display: block; height: 100%; border-radius: 3px; }
  .cards { display: flex; gap: 16px; margin: 22px 0 6px; }
  .card { flex: 1; border: 1px solid #dbe6f3; border-radius: 8px; padding: 14px 16px; background: #fafcff; text-align: center; }
  .card .v { font-size: 26px; font-weight: 700; color: #1f4e9c; }
  .card .k { font-size: 12px; color: #6b7280; margin-top: 4px; }
  .note { margin-top: 18px; font-size: 12px; color: #6b7280; line-height: 1.7; border-top: 1px dashed #d5dfeb; padding-top: 12px; }
</style></head>
<body>
  <h1>接口性能测试结果</h1>
  <div class="sub">高校智能教务与选课系统 · 核心接口响应延迟与并发吞吐</div>
  <div class="meta">
    <span>测量方式：<b>顺序单请求延迟</b>（每接口预热后连续 ${meta.iterations} 次）</span>
    <span>并发测试：<b>${conc.concurrency} 并发</b></span>
    <span>测试时间：${meta.time.slice(0, 19).replace('T', ' ')}</span>
  </div>
  <table>
    <thead><tr>
      <th>序号</th><th>功能</th><th>接口</th><th>样本</th><th>最小(ms)</th><th>平均(ms)</th><th>P95(ms)</th><th>最大(ms)</th><th>平均耗时对比</th>
    </tr></thead>
    <tbody>${tr}</tbody>
  </table>
  <div class="cards">
    <div class="card"><div class="v">${conc.qps.toFixed(0)}</div><div class="k">并发吞吐 (req/s)</div></div>
    <div class="card"><div class="v">${conc.ok}/${conc.concurrency}</div><div class="k">并发请求成功率</div></div>
    <div class="card"><div class="v">${all.reduce((a, b) => Math.max(a, b.p95), 0).toFixed(0)}</div><div class="k">最大 P95 延迟 (ms)</div></div>
    <div class="card"><div class="v">${(all.reduce((a, b) => a + b.avg, 0) / all.length).toFixed(1)}</div><div class="k">各接口平均延迟均值 (ms)</div></div>
  </div>
  <div class="note">
    测试环境：Windows 单机，MySQL 8.0 与 Spring Boot 应用同机部署，未做连接池与 JVM 调优，客户端与被测服务在同一台机器上；
    结果为<b>本机开发环境</b>相对性能，用于验证接口在常规数据量（26 名学生 / 14 门课程 / 135 条选课）下的响应水平，不代表生产环境容量。
  </div>
</body></html>`;
}

main().catch((e) => {
  console.error('性能测试失败: ' + e.message);
  process.exit(1);
});
