// 提取 .docx 文本（含批注），用于阅读模板要求。
// 用法: node tools/extract-docx.mjs <file.docx> [--xml <part>]
// 默认输出正文段落；--comments 输出批注；--raw 输出指定 part 的纯文本。
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { execFileSync } from 'node:child_process';

const file = process.argv[2];
if (!file) {
  console.error('用法: node tools/extract-docx.mjs <file.docx> [--comments] [--part word/document.xml]');
  process.exit(1);
}
const wantComments = process.argv.includes('--comments');
const partArgIdx = process.argv.indexOf('--part');

const abs = path.resolve(file);
if (!fs.existsSync(abs)) {
  console.error('文件不存在: ' + abs);
  process.exit(1);
}

// docx 是 zip：复制成 .zip 后用 Expand-Archive 解压（不依赖第三方 zip 库）
const work = path.join(os.tmpdir(), 'docx-x-' + Date.now());
fs.mkdirSync(work, { recursive: true });
const zip = path.join(work, 'a.zip');
fs.copyFileSync(abs, zip);
execFileSync('powershell.exe', [
  '-NoProfile', '-Command',
  `Expand-Archive -LiteralPath '${zip}' -DestinationPath '${path.join(work, 'x')}' -Force`,
], { stdio: 'ignore' });

const dec = (s) => s
  .replace(/&lt;/g, '<').replace(/&gt;/g, '>')
  .replace(/&quot;/g, '"').replace(/&apos;/g, "'")
  .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
  .replace(/&amp;/g, '&');

// 把 XML 拆成段落，提取每段的样式名和文字（含表格单元格内的段落）
function extractParagraphs(xml) {
  const out = [];
  const paras = xml.split(/<w:p[ >]/).slice(1);
  for (const raw of paras) {
    const p = raw.slice(0, raw.indexOf('</w:p>') === -1 ? raw.length : raw.indexOf('</w:p>'));
    const style = /<w:pStyle\s+w:val="([^"]+)"/.exec(p)?.[1] ?? '';
    const texts = [...p.matchAll(/<w:t(?:\s[^>]*)?>([\s\S]*?)<\/w:t>/g)].map((m) => dec(m[1]));
    const line = texts.join('').replace(/\s+$/, '');
    if (line.trim() || style) out.push({ style, line });
  }
  return out;
}

const part = partArgIdx !== -1 ? process.argv[partArgIdx + 1] : null;
if (part) {
  const xml = fs.readFileSync(path.join(work, 'x', part), 'utf8');
  console.log(extractParagraphs(xml).map((p) => p.line).join('\n'));
} else if (wantComments) {
  const p = path.join(work, 'x', 'word', 'comments.xml');
  if (!fs.existsSync(p)) { console.log('（无批注）'); }
  else {
    const xml = fs.readFileSync(p, 'utf8');
    const comments = xml.split(/<w:comment[ >]/).slice(1);
    comments.forEach((c, i) => {
      const author = /w:author="([^"]*)"/.exec(c)?.[1] ?? '';
      const body = extractParagraphs(c).map((x) => x.line).join('\n').trim();
      console.log(`--- 批注 ${i + 1}${author ? ' （' + author + '）' : ''} ---`);
      console.log(body);
      console.log('');
    });
  }
} else {
  const xml = fs.readFileSync(path.join(work, 'x', 'word', 'document.xml'), 'utf8');
  const paras = extractParagraphs(xml);
  paras.forEach((p, i) => {
    const tag = p.style ? `[${p.style}] ` : '';
    console.log(`${String(i + 1).padStart(4)}| ${tag}${p.line}`);
  });
  console.log(`\n（共 ${paras.length} 段）`);
}

fs.rmSync(work, { recursive: true, force: true });
