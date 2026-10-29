// 学业预警邮件通知功能调试
// 需先启动后端（真实发送模式: APP_MAIL_ENABLED=true, MAIL_HOST=127.0.0.1, MAIL_PORT=2525）
// 并启动本地 SMTP 接收器 tools/smtp-sink.mjs
import fs from 'node:fs';
import path from 'node:path';

const BASE = process.env.BASE ?? 'http://127.0.0.1:8080';
const INBOX = process.env.SMTP_OUT
  ?? path.resolve(import.meta.dirname, 'smtp-inbox');

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
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function inboxFiles() {
  if (!fs.existsSync(INBOX)) return [];
  return fs.readdirSync(INBOX).filter((f) => f.endsWith('.json')).sort();
}
function readMail(file) {
  return JSON.parse(fs.readFileSync(path.join(INBOX, file), 'utf8'));
}

const adminToken = (await login('admin')).data?.token;
const studentToken = (await login('2024001')).data?.token;
const teacherToken = (await login('t001')).data?.token;

section('1. 邮件模式与预警数据准备');
const summary = await call('GET', '/api/admin/warnings/summary', { token: adminToken });
const MAIL_ON = summary.data?.mailEnabled === true;
console.log(`    当前模式：${MAIL_ON ? '真实发送 (app.mail.enabled=true)' : '模拟发送 (app.mail.enabled=false)'}`);
check('summary 返回 mailEnabled 布尔值', typeof summary.data?.mailEnabled === 'boolean',
  String(summary.data?.mailEnabled));
check('summary 返回发件人地址', !!summary.data?.mailFrom, summary.data?.mailFrom);
check('预警人数为 6', summary.data?.flaggedStudents === 6, `flagged=${summary.data?.flaggedStudents}`);

const list = await call('GET', '/api/admin/warnings?page=1&size=50', { token: adminToken });
const warned = list.data?.records ?? [];
check('预警名单返回 6 人', warned.length === 6, `len=${warned.length}`);
check('名单含 email 字段', warned.every((r) => 'email' in r), JSON.stringify(warned[0] ?? {}).slice(0, 200));
const noEmail = warned.filter((r) => !r.email);
const withEmail = warned.filter((r) => !!r.email);
console.log(`    有邮箱 ${withEmail.length} 人，无邮箱 ${noEmail.length} 人（${noEmail.map((r) => r.studentNo).join(',') || '无'}）`);
check('存在 1 名无邮箱的预警学生（2024007，用于验证跳过分支）',
  noEmail.length === 1 && noEmail[0].studentNo === '2024007',
  JSON.stringify(noEmail.map((r) => r.studentNo)));

section('2. 邮件内容预览');
const sample = withEmail.find((r) => r.level === 'RED') ?? withEmail[0];
const preview = await call('GET', `/api/admin/warnings/preview/${sample.studentId}`, { token: adminToken });
check('preview 接口成功', preview.code === 200, JSON.stringify(preview).slice(0, 160));
check('预览含收件邮箱', preview.data?.email === sample.email, `${preview.data?.email} vs ${sample.email}`);
check('预览含主题', String(preview.data?.subject ?? '').includes('学业预警'), preview.data?.subject);
const content = String(preview.data?.content ?? '');
check('正文含学生姓名与学号',
  content.includes(sample.studentName) && content.includes(sample.studentNo), content.slice(0, 80));
check('正文含预警等级', content.includes(sample.levelLabel), sample.levelLabel);
check('正文含"累计不及格学分"', content.includes('累计不及格学分'));
check('正文含处置建议章节', content.includes('四、处置建议'));
check('正文含教务处落款', content.includes('教务处'));
check('正文长度合理（> 400 字）', content.length > 400, `len=${content.length}`);

section('3. 校验与权限');
const emptyIds = await call('POST', '/api/admin/warnings/notify', { token: adminToken, body: { studentIds: [] } });
check('空列表被拒（400）', emptyIds.code === 400, `code=${emptyIds.code} msg=${emptyIds.message}`);
const stuTry = await call('POST', '/api/admin/warnings/notify', { token: studentToken, body: { studentIds: [1] } });
check('学生调用发送接口 → 403', stuTry.code === 403, `code=${stuTry.code}`);
const teaTry = await call('POST', '/api/admin/warnings/notify', { token: teacherToken, body: { studentIds: [1] } });
check('教师调用发送接口 → 403', teaTry.code === 403, `code=${teaTry.code}`);
const noToken = await call('POST', '/api/admin/warnings/notify', { body: { studentIds: [1] } });
check('无令牌 → 401', noToken.code === 401, `code=${noToken.code}`);

section(`4. ${MAIL_ON ? '真实发送（本地 SMTP 接收器）' : '模拟发送（不连网络）'}`);

// 安全护栏：真实模式下若学生邮箱是种子假地址（域名不存在），SMTP 会"接受"这封邮件
// 但随后异步退信，把发件人邮箱（通常是使用者本人的真实邮箱）塞满退信。
// 因此真实模式必须显式设置 ALLOW_REAL_SEND=1 才继续，避免误跑污染收件箱。
if (MAIL_ON && process.env.ALLOW_REAL_SEND !== '1') {
  const suspicious = withEmail.filter((r) => /@student\.school\.edu\.cn$/.test(r.email)).length;
  if (suspicious > 0) {
    console.log(`  已跳过发送：当前为真实发送模式，且 ${suspicious} 个收件人使用种子假邮箱`);
    console.log('  （域名 school.edu.cn 不存在，QQ 等会先接受再异步退信，污染发件人收件箱）');
    console.log('  如确需在真实模式下测试，请设置环境变量 ALLOW_REAL_SEND=1 后重跑。');
    console.log(`\n${'='.repeat(62)}`);
    console.log('学业预警邮件通知调试: 已跳过真实发送（安全护栏）');
    console.log('='.repeat(62));
    process.exit(0);
  }
}

const beforeCount = inboxFiles().length;
const targets = warned.map((r) => r.studentId);
const send = await call('POST', '/api/admin/warnings/notify', { token: adminToken, body: { studentIds: targets } });
check('发送接口返回 200', send.code === 200, JSON.stringify(send).slice(0, 240));
const s = send.data ?? {};
console.log(`    汇总: 共 ${s.total} / 已发送 ${s.sent} / 模拟 ${s.simulated} / 失败 ${s.failed} / 跳过 ${s.skipped} / mailEnabled=${s.mailEnabled}`);
check('mailEnabled 与 summary 一致', s.mailEnabled === MAIL_ON, `${s.mailEnabled} vs ${MAIL_ON}`);
check('总数 = 6', s.total === 6, `total=${s.total}`);
check('失败 0', s.failed === 0, `failed=${s.failed}`);
check('跳过 1（无邮箱）', s.skipped === 1, `skipped=${s.skipped}`);
if (MAIL_ON) {
  check('已发送 5 封（1 人无邮箱被跳过）', s.sent === 5, `sent=${s.sent}`);
} else {
  check('已发送 0（模拟模式不真实投递）', s.sent === 0, `sent=${s.sent}`);
  check('模拟发送 5 条（1 人无邮箱被跳过）', s.simulated === 5, `simulated=${s.simulated}`);
}

const skippedItem = (s.results ?? []).find((r) => r.status === 'SKIPPED');
check('跳过项为学生 2024007', skippedItem?.studentNo === '2024007', JSON.stringify(skippedItem));
check('跳过原因说明为未登记邮箱',
  String(skippedItem?.message ?? '').includes('未登记邮箱'), skippedItem?.message);
const expectedStatus = MAIL_ON ? 'SENT' : 'SIMULATED';
check(`成功项状态为 ${expectedStatus}`,
  (s.results ?? []).filter((r) => r.status === expectedStatus).length === 5,
  JSON.stringify((s.results ?? []).map((r) => r.status)));

await sleep(1200); // 等 SMTP 写入落盘
const afterFiles = inboxFiles();
console.log(`    SMTP 接收器: ${beforeCount} -> ${afterFiles.length} 封`);
if (MAIL_ON) {
  check('SMTP 服务器实际收到 5 封邮件', afterFiles.length - beforeCount === 5,
    `delta=${afterFiles.length - beforeCount}`);
} else {
  check('模拟模式未向 SMTP 投递任何邮件', afterFiles.length - beforeCount === 0,
    `delta=${afterFiles.length - beforeCount}`);
}

const newMails = afterFiles.slice(beforeCount).map(readMail);
const recipients = newMails.map((m) => (m.envelope.to[0] ?? '').replace(/[<>]/g, ''));
console.log('    收件人:', recipients.join(', ') || '(无)');
if (MAIL_ON) {
  check('收件人与预警学生邮箱一一对应',
    recipients.length === 5 && withEmail.every((r) => recipients.includes(r.email)),
    JSON.stringify(recipients));
  check('无邮箱学生未被投递', !recipients.some((x) => x.includes('2024007')), JSON.stringify(recipients));
}

// 真实发送模式下校验实际投递的 MIME 报文；
// 模拟模式不产生报文，邮件内容改由第 2 节的预览接口校验。
function checkMailContent() {
section('5. 邮件报文内容校验');
const firstMail = newMails[0];
check('报文含 RFC2047 编码的中文主题',
  /Subject:.*=\?UTF-8\?/i.test(firstMail.raw) || firstMail.raw.includes('Subject:'),
  (firstMail.raw.match(/Subject:.*/) ?? [''])[0].slice(0, 120));
check('报文声明为 MIME 多部分（纯文本 + HTML）',
  /Content-Type:\s*multipart\/alternative/i.test(firstMail.raw),
  (firstMail.raw.match(/Content-Type:.*/) ?? [''])[0]);
check('报文含纯文本部分', /Content-Type:\s*text\/plain/i.test(firstMail.raw));
check('报文含 HTML 部分', /Content-Type:\s*text\/html/i.test(firstMail.raw));
check('报文含发件人 noreply@school.edu.cn',
  firstMail.raw.includes('noreply@school.edu.cn'),
  (firstMail.raw.match(/From:.*/) ?? [''])[0].slice(0, 120));
check('报文长度合理（> 2KB）', firstMail.raw.length > 2048, `bytes=${firstMail.raw.length}`);

/** 按 MIME 分段取出正文部分（JavaMail 对纯文本用 base64、对 HTML 常用 quoted-printable） */
function extractParts(raw) {
  const parts = [];
  const re = /Content-Type:\s*(text\/(?:plain|html))[^\r\n]*[\s\S]*?Content-Transfer-Encoding:\s*([\w-]+)\s*[\r\n]+([\s\S]*?)(?=\r?\n------|$)/gi;
  let m;
  while ((m = re.exec(raw)) !== null) {
    parts.push({ type: m[1].toLowerCase(), encoding: m[2].toLowerCase(), body: m[3] });
  }
  return parts;
}

function decodeQuotedPrintable(body) {
  const cleaned = body.replace(/=\r?\n/g, '');
  const bytes = [];
  for (let i = 0; i < cleaned.length; i += 1) {
    const ch = cleaned[i];
    const hex = cleaned.slice(i + 1, i + 3);
    if (ch === '=' && /^[0-9A-Fa-f]{2}$/.test(hex)) {
      bytes.push(parseInt(hex, 16));
      i += 2;
    } else {
      bytes.push(ch.charCodeAt(0) & 0xff);
    }
  }
  return Buffer.from(bytes).toString('utf8');
}

function decodePart(part) {
  if (part.encoding.includes('base64')) {
    return Buffer.from(part.body.replace(/\s/g, ''), 'base64').toString('utf8');
  }
  if (part.encoding.includes('quoted-printable')) {
    return decodeQuotedPrintable(part.body);
  }
  return part.body;
}

const parts = extractParts(firstMail.raw);
check('报文解析出 2 个正文段（纯文本 + HTML）', parts.length === 2,
  JSON.stringify(parts.map((x) => `${x.type}:${x.encoding}`)));
const plainPart = parts.find((x) => x.type === 'text/plain');
const htmlPart = parts.find((x) => x.type === 'text/html');
check('纯文本段使用 base64 编码', plainPart?.encoding === 'base64', plainPart?.encoding);
check('HTML 段使用 quoted-printable 编码',
  htmlPart?.encoding === 'quoted-printable', htmlPart?.encoding);

const plainDecoded = plainPart ? decodePart(plainPart) : '';
const htmlDecoded = htmlPart ? decodePart(htmlPart) : '';

check('纯文本正文含"学业红线预警"', plainDecoded.includes('学业红线预警'), plainDecoded.slice(0, 80));
check('纯文本正文含"累计不及格学分"', plainDecoded.includes('累计不及格学分'));
check('纯文本正文含"处置建议"', plainDecoded.includes('处置建议'));
check('纯文本正文含查询入口链接', plainDecoded.includes('http://localhost:8080'));
check('纯文本中文未乱码（含"教务处"）', plainDecoded.includes('教务处'));

check('HTML 正文含内联样式表格',
  htmlDecoded.includes('<table') && htmlDecoded.includes('border-collapse'),
  htmlDecoded.slice(0, 120));
check('HTML 正文含三级标题与色块', htmlDecoded.includes('一、预警等级') && htmlDecoded.includes('border-radius'));
check('HTML 正文中文未乱码（含"教务处"）', htmlDecoded.includes('教务处'));
check('HTML 正文含预警等级原文', htmlDecoded.includes(withEmail.find((r) => r.email === recipients[0])?.levelLabel ?? ''),
  htmlDecoded.slice(0, 60));
}

if (MAIL_ON) {
  checkMailContent();
} else {
  section('5. 邮件报文内容校验（模拟模式跳过：无真实报文，内容已由第 2 节预览校验）');
  console.log('    跳过 MIME 报文校验');
}

section('6. 边界：未预警学生与不存在学生');
const normalStudent = (await call('GET', '/api/admin/warnings?level=NORMAL&page=1&size=1', { token: adminToken }))
  .data.records[0];
const skipNormal = await call('POST', '/api/admin/warnings/notify', {
  token: adminToken, body: { studentIds: [normalStudent.studentId] },
});
check('未触发预警的学生被跳过（不发送）',
  skipNormal.data?.skipped === 1 && skipNormal.data?.sent === 0,
  JSON.stringify(skipNormal.data).slice(0, 200));
check('跳过原因为未触发预警',
  String(skipNormal.data?.results?.[0]?.message ?? '').includes('未触发'), skipNormal.data?.results?.[0]?.message);

await sleep(800);
const afterSkip = inboxFiles().length;
check('未预警学生未产生真实投递', afterSkip === afterFiles.length, `${afterFiles.length} -> ${afterSkip}`);

const ghost = await call('POST', '/api/admin/warnings/notify', { token: adminToken, body: { studentIds: [999999] } });
check('不存在的学生被跳过而非报错',
  ghost.code === 200 && ghost.data?.skipped === 1, JSON.stringify(ghost.data).slice(0, 200));

section('7. 发送历史记录');
const history = await call('GET', '/api/admin/warnings/notifications?page=1&size=50', { token: adminToken });
check('历史记录接口成功', history.code === 200, JSON.stringify(history).slice(0, 160));
// 落库规则：SENT / SIMULATED / FAILED，以及「已触发预警但账号无邮箱」都会入库
// （后者是教务处可跟进的行动项）；而「未触发预警」与「学生不存在」属管理员输入错误，
// 只在本次响应中反馈，不写入历史，避免污染发送记录。
check('历史记录 = 5 条 SENT + 1 条无邮箱 SKIPPED = 6', history.data?.total === 6,
  `total=${history.data?.total}`);
check('无邮箱跳过已入库（教务处可据此补录邮箱）',
  (history.data?.records ?? []).some((r) => r.status === 'SKIPPED' && r.studentNo === '2024007'),
  JSON.stringify((history.data?.records ?? []).filter((r) => r.status === 'SKIPPED').map((r) => r.studentNo)));
check('未触发预警的学生未入库（属输入错误，仅响应反馈）',
  !(history.data?.records ?? []).some((r) => r.studentNo === String(normalStudent.studentNo)),
  `正常学生 ${normalStudent.studentNo}`);
check('不存在的学生未入库',
  !(history.data?.records ?? []).some((r) => r.studentId === 999999));
const histRecords = history.data?.records ?? [];
console.log('    最近记录:',
  histRecords.slice(0, 3).map((r) => `${r.studentNo}/${r.status}/${r.email ?? '无邮箱'}`).join('  '));
check(`历史记录含 ${expectedStatus} 状态`, histRecords.some((r) => r.status === expectedStatus),
  JSON.stringify([...new Set(histRecords.map((r) => r.status))]));
check('历史记录含 SKIPPED 状态', histRecords.some((r) => r.status === 'SKIPPED'));
check('历史记录含中文状态标签', histRecords.every((r) => !!r.statusLabel), JSON.stringify(histRecords[0]));
check('历史记录含发送时间', histRecords.every((r) => !!r.sendTime));
check('SENT 记录含收件邮箱', histRecords.filter((r) => r.status === 'SENT').every((r) => !!r.email));

const redHistory = histRecords.find((r) => r.level === 'RED');
check('历史记录保存了发送时的等级快照', redHistory?.levelLabel === '红色预警（学业红线）',
  redHistory?.levelLabel);

const byStudent = await call('GET', `/api/admin/warnings/notifications?studentId=${sample.studentId}`, { token: adminToken });
check('可按学生过滤历史记录',
  byStudent.code === 200 && (byStudent.data?.records ?? []).every((r) => r.studentId === sample.studentId),
  JSON.stringify((byStudent.data?.records ?? []).map((r) => r.studentId)));

const teaHistory = await call('GET', '/api/admin/warnings/notifications', { token: teacherToken });
check('教师访问历史记录 → 403', teaHistory.code === 403, `code=${teaHistory.code}`);

console.log(`\n${'='.repeat(62)}`);
console.log(`学业预警邮件通知调试: ${pass} 通过 / ${fail} 失败  (共 ${pass + fail} 项)`);
if (failures.length) {
  console.log('\n失败项:');
  failures.forEach((f) => console.log('  - ' + f));
}
console.log('='.repeat(62));
process.exitCode = fail === 0 ? 0 : 1;
