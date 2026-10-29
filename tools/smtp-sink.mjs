// 本地 SMTP 接收器（测试用）：不依赖任何第三方包，纯 net 实现最小可用的 SMTP 会话，
// 用于在没有真实邮箱授权码的情况下验证「邮件发送链路」确实跑通。
//
// 收到的邮件会写入 tools/smtp-inbox/mail-NNN.json（含信封与原始报文）与同名 .eml。
//
// 用法: node tools/smtp-sink.mjs          （默认监听 127.0.0.1:2525）
import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';

const PORT = Number(process.env.SMTP_PORT ?? 2525);
const HOST = process.env.SMTP_HOST ?? '127.0.0.1';
const OUT = process.env.SMTP_OUT ?? path.resolve(import.meta.dirname, 'smtp-inbox');
fs.mkdirSync(OUT, { recursive: true });

let seq = 0;

const server = net.createServer((socket) => {
  let buffer = '';
  let inData = false;
  let dataLines = [];
  let envelope = { from: null, to: [] };

  const send = (line) => socket.write(`${line}\r\n`);
  send('220 smtp-sink ESMTP ready');

  socket.on('data', (chunk) => {
    buffer += chunk.toString('utf8');
    let idx;
    while ((idx = buffer.indexOf('\r\n')) >= 0) {
      const line = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      handle(line);
    }
  });

  socket.on('error', () => { /* 客户端提前断开，忽略 */ });

  function handle(line) {
    if (inData) {
      if (line === '.') {
        inData = false;
        const raw = dataLines.join('\r\n');
        seq += 1;
        const base = `mail-${String(seq).padStart(3, '0')}`;
        // 即使输出目录被外部清理也自动重建，避免接收器因 ENOENT 直接崩溃
        try {
          fs.mkdirSync(OUT, { recursive: true });
          fs.writeFileSync(path.join(OUT, `${base}.json`),
            JSON.stringify({ envelope, receivedAt: new Date().toISOString(), bytes: raw.length, raw }, null, 2),
            'utf8');
          fs.writeFileSync(path.join(OUT, `${base}.eml`), raw, 'utf8');
        } catch (err) {
          console.error(`[smtp-sink] 写入失败: ${err.message}`);
        }
        console.log(`[smtp-sink] #${seq} from=${envelope.from} to=${envelope.to.join(',')} bytes=${raw.length}`);
        dataLines = [];
        envelope = { from: null, to: [] };
        send('250 2.0.0 Ok: queued');
        return;
      }
      // 处理 dot-stuffing
      dataLines.push(line.startsWith('..') ? line.slice(1) : line);
      return;
    }

    const upper = line.toUpperCase();
    if (upper.startsWith('EHLO')) {
      send('250-smtp-sink');
      send('250-8BITMIME');
      send('250-SMTPUTF8');
      send('250 SIZE 10485760');
    } else if (upper.startsWith('HELO')) {
      send('250 smtp-sink');
    } else if (upper.startsWith('MAIL FROM')) {
      envelope.from = line.slice(line.indexOf(':') + 1).trim();
      send('250 2.1.0 Ok');
    } else if (upper.startsWith('RCPT TO')) {
      envelope.to.push(line.slice(line.indexOf(':') + 1).trim());
      send('250 2.1.5 Ok');
    } else if (upper === 'DATA') {
      inData = true;
      send('354 End data with <CR><LF>.<CR><LF>');
    } else if (upper === 'RSET') {
      envelope = { from: null, to: [] };
      dataLines = [];
      send('250 2.0.0 Ok');
    } else if (upper === 'NOOP') {
      send('250 2.0.0 Ok');
    } else if (upper === 'QUIT') {
      send('221 2.0.0 Bye');
      socket.end();
    } else {
      send('250 2.0.0 Ok');
    }
  }
});

server.listen(PORT, HOST, () => {
  console.log(`[smtp-sink] listening on ${HOST}:${PORT}`);
  console.log(`[smtp-sink] 邮件将写入 ${OUT}`);
});

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    console.log(`\n[smtp-sink] 已接收 ${seq} 封邮件，退出`);
    server.close(() => process.exit(0));
  });
}
