// Download build tools into the workspace (sandbox only allows writes here).
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const dl = path.join(root, 'tools', 'downloads');
fs.mkdirSync(dl, { recursive: true });

const targets = [
  {
    name: 'apache-maven-3.9.9-bin.zip',
    urls: [
      'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip',
      'https://mirrors.aliyun.com/apache/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip',
    ],
    minBytes: 5_000_000,
  },
  {
    name: 'plantuml.jar',
    urls: [
      'https://repo.maven.apache.org/maven2/net/sourceforge/plantuml/plantuml/1.2024.7/plantuml-1.2024.7.jar',
      'https://repo.maven.apache.org/maven2/net/sourceforge/plantuml/plantuml/1.2024.8/plantuml-1.2024.8.jar',
    ],
    minBytes: 5_000_000,
  },
];

for (const t of targets) {
  const dest = path.join(dl, t.name);
  if (fs.existsSync(dest) && fs.statSync(dest).size >= t.minBytes) {
    console.log(`SKIP  ${t.name} (${fs.statSync(dest).size} bytes already present)`);
    continue;
  }
  let ok = false;
  for (const url of t.urls) {
    try {
      console.log(`GET   ${url}`);
      const res = await fetch(url, { redirect: 'follow' });
      if (!res.ok) {
        console.log(`  -> HTTP ${res.status}`);
        continue;
      }
      const buf = Buffer.from(await res.arrayBuffer());
      if (buf.length < t.minBytes) {
        console.log(`  -> too small: ${buf.length}`);
        continue;
      }
      fs.writeFileSync(dest, buf);
      console.log(`OK    ${t.name} (${buf.length} bytes)`);
      ok = true;
      break;
    } catch (e) {
      console.log(`  -> ${e.message}`);
    }
  }
  if (!ok) {
    console.log(`FAIL  ${t.name}`);
    process.exitCode = 1;
  }
}
console.log('download phase done');
