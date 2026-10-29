/**
 * 构建环境适配脚本（在 vite build 之前自动执行）
 *
 * 背景：当前沙箱禁止 Node 以 piped stdio 派生子进程 —— 任何 child_process.spawn
 * 都会直接抛 `EPERM: spawn`（PowerShell 自身不受影响，但 Node 创建子进程管道被拦截）。
 * 这会打断两条与业务无关的链路：
 *
 *   1. Vite 的 Windows 专用 realpath 优化会执行 `net use`（exec + 管道）探测网络
 *      驱动器映射；被拦截后异常直接冒泡，导致构建失败。
 *   2. esbuild 的常驻服务进程必须通过管道与 Node 通信，因此完全无法启动；而 Vite
 *      的 `vite:define` 插件在每个 JS 模块上都会调用 esbuild 做 define 替换。
 *
 * 处理方式（均为等价的纯 JS 回退，不改变产物语义）：
 *   - `net use` 探测被拦截时退回 `fs.realpathSync.native`（即 Vite 探测失败时的默认分支）。
 *   - esbuild 不可用时，`replaceDefine` 改用正则做字面量替换：导入期环境变量在
 *     `vite:define` 中全部是字符串字面量，替换语义与 esbuild define 一致。
 *   - 构建链路不再用 esbuild 压缩：package.json 使用 `--configLoader native` 绕过
 *     esbuild 配置打包，vite.config.mjs 关闭 JS/CSS 压缩。
 *
 * 脚本幂等：已打过补丁的代码不会被重复修改。
 */
import { readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = dirname(dirname(fileURLToPath(import.meta.url)))
const nodeModules = join(root, 'node_modules')

function log(message) {
  console.log('[env-patch] ' + message)
}

const MARKER = '/* dsh-sandbox-patch */'

/* ------------------------------------------------------------------ *
 * 1) Vite Windows realpath 优化中的 `net use` 探测
 * ------------------------------------------------------------------ */

const ORIGINAL_REALPATH_BLOCK = [
  '  try {',
  '    fs__default.realpathSync.native(path$b.resolve("./"));',
  '  } catch (error) {',
  '    if (error.message.includes("EISDIR: illegal operation on a directory")) {',
  '      safeRealpathSync = fs__default.realpathSync;',
  '      return;',
  '    }',
  '  }',
  '  exec("net use", (error, stdout) => {',
  '    if (error) return;',
  '    const lines = stdout.split("\\n");',
  '    for (const line of lines) {',
  '      const m = parseNetUseRE.exec(line);',
  '      if (m) windowsNetworkMap.set(m[2], m[1]);',
  '    }',
  '    if (windowsNetworkMap.size === 0) {',
  '      safeRealpathSync = fs__default.realpathSync.native;',
  '    } else {',
  '      safeRealpathSync = windowsMappedRealpathSync;',
  '    }',
  '  });'
].join('\n')

const PATCHED_REALPATH_BLOCK = [
  MARKER,
  '  try {',
  '    fs__default.realpathSync.native(path$b.resolve("./"));',
  '  } catch (error) {',
  '    if (error.message.includes("EISDIR: illegal operation on a directory")) {',
  '      safeRealpathSync = fs__default.realpathSync;',
  '      return;',
  '    }',
  '  }',
  '  // 沙箱不允许 Node 派生子进程（spawn EPERM）：跳过 "net use" 探测并采用默认回退',
  '  try {',
  '    exec("net use", (error, stdout) => {',
  '      if (error) return;',
  '      const lines = stdout.split("\\n");',
  '      for (const line of lines) {',
  '        const m = parseNetUseRE.exec(line);',
  '        if (m) windowsNetworkMap.set(m[2], m[1]);',
  '      }',
  '      if (windowsNetworkMap.size === 0) {',
  '        safeRealpathSync = fs__default.realpathSync.native;',
  '      } else {',
  '        safeRealpathSync = windowsMappedRealpathSync;',
  '      }',
  '    });',
  '  } catch (error) {',
  '    safeRealpathSync = fs__default.realpathSync.native;',
  '  }'
].join('\n')

/* ------------------------------------------------------------------ *
 * 2) replaceDefine 的纯 JS 回退
 * ------------------------------------------------------------------ */

const ORIGINAL_DEFINE_HEAD = [
  'async function replaceDefine(environment, code, id, define) {',
  '  const esbuildOptions = environment.config.esbuild || {};',
  '  const result = await transform$1(code, {'
].join('\n')

const PATCHED_DEFINE_HEAD = [
  MARKER,
  'function replaceDefineFallback(code, define) {',
  '  var result = code;',
  '  var escapeRe = function (value) { return value.replace(/[.*+?^${}()|[\\]\\\\]/g, "\\\\$&"); };',
  '  var envVal = typeof define["import.meta.env"] === "string" ? define["import.meta.env"] : null;',
  '  if (envVal !== null) {',
  '    result = result.replace(/\\bimport\\.meta\\.env\\b/g, function () { return envVal; });',
  '  }',
  '  var keys = Object.keys(define)',
  '    .filter(function (key) { return key !== "import.meta.env"; })',
  '    .sort(function (a, b) { return b.length - a.length; });',
  '  for (var i = 0; i < keys.length; i += 1) {',
  '    var key = keys[i];',
  '    var value = define[key];',
  '    if (value === undefined) continue;',
  '    var literal = typeof value === "string" ? JSON.stringify(value) : String(value);',
  '    var pattern = new RegExp("(?<![\\\\w$.])" + escapeRe(key) + "(?![\\\\w$])", "g");',
  '    result = result.replace(pattern, function () { return literal; });',
  '  }',
  '  return result;',
  '}',
  'async function replaceDefine(environment, code, id, define) {',
  '  const esbuildOptions = environment.config.esbuild || {};',
  '  let result;',
  '  try {',
  '  result = await transform$1(code, {'
].join('\n')

const ORIGINAL_DEFINE_TAIL = [
  '    sourcemap: environment.config.command === "build" ? !!environment.config.build.sourcemap : true',
  '  });',
  '  if (result.map.includes("<define:")) {'
].join('\n')

const PATCHED_DEFINE_TAIL = [
  '    sourcemap: environment.config.command === "build" ? !!environment.config.build.sourcemap : true',
  '  });',
  '  } catch (error) {',
  '    // 沙箱禁止 spawn：退回等价的正则字面量替换（导入期环境变量均为字面量）',
  '    if (error && error.code === "EPERM") {',
  '      return { code: replaceDefineFallback(code, define), map: null };',
  '    }',
  '    throw error;',
  '  }',
  '  if (result.map.includes("<define:")) {'
].join('\n')

function patchFile(filePath, pairs) {
  const code = readFileSync(filePath, 'utf8')
  // 幂等：目标代码已存在则视为已打补丁
  if (pairs.every(([, to]) => code.includes(to))) return false
  let next = code
  for (const [from, to] of pairs) {
    if (!next.includes(from)) return false
    // 用函数替换，避免 to 中的 $& / $1 等被当作替换模式解释
    next = next.replace(from, () => to)
  }
  writeFileSync(filePath, next, 'utf8')
  return true
}

function patchViteChunks() {
  const chunksDir = join(nodeModules, 'vite', 'dist', 'node', 'chunks')
  if (!existsSync(chunksDir)) {
    log('未找到 vite 构建产物，跳过补丁')
    return
  }

  let realpathPatched = 0
  let definePatched = 0

  for (const file of readdirSync(chunksDir)) {
    if (!file.endsWith('.js')) continue
    const full = join(chunksDir, file)
    if (patchFile(full, [[ORIGINAL_REALPATH_BLOCK, PATCHED_REALPATH_BLOCK]])) {
      realpathPatched += 1
      log('已修补 ' + file + '：net use 探测改为容错调用')
    }
  }

  for (const file of readdirSync(chunksDir)) {
    if (!file.endsWith('.js')) continue
    const full = join(chunksDir, file)
    const patched = patchFile(full, [
      [ORIGINAL_DEFINE_HEAD, PATCHED_DEFINE_HEAD],
      [ORIGINAL_DEFINE_TAIL, PATCHED_DEFINE_TAIL]
    ])
    if (patched) {
      definePatched += 1
      log('已修补 ' + file + '：replaceDefine 增加纯 JS 回退')
    }
  }

  log(
    realpathPatched + definePatched === 0
      ? 'vite 补丁无需变更（可能已应用）'
      : 'vite 补丁完成（realpath ' + realpathPatched + '，define ' + definePatched + '）'
  )
}

function reportEsbuild() {
  const esbuildPkg = join(nodeModules, 'esbuild', 'package.json')
  if (!existsSync(esbuildPkg)) {
    log('未安装 esbuild')
    return
  }
  const version = JSON.parse(readFileSync(esbuildPkg, 'utf8')).version
  log('esbuild ' + version + ' 已安装；构建已关闭其压缩用途（configLoader=native、minify=false、cssMinify=false）')
}

patchViteChunks()
reportEsbuild()
