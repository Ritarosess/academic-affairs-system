/**
 * 受限沙箱环境专用的构建配置 —— 正常环境请直接使用 `vite.config.mjs`。
 *
 * 背景：某些受限执行环境（如 DSH 文件沙箱）禁止 Node 以 piped stdio 派生子进程，
 * 任何 `child_process.spawn` 都会抛 `EPERM`。这会影响两条与业务无关的链路：
 *   1. esbuild 的常驻服务进程必须经管道通信，因此无法启动；
 *   2. Vite 的 Windows realpath 优化会执行 `net use` 探测网络驱动器映射。
 *
 * 本配置在构建链路中完全绕开 esbuild，代价是关闭 JS/CSS 压缩（不影响功能与语义）：
 *   - esbuild: false        关闭 vite:esbuild / vite:esbuild-transpile
 *   - minify: 'terser'      改用进程内的 terser 压缩 JS
 *   - cssMinify: false      CSS 压缩依赖 esbuild，故关闭
 *
 * 配合 `tools/prepare-build-env.mjs`（对 node_modules 内的 Vite 打两处等价补丁）
 * 与 `--configLoader native`（跳过 esbuild 配置打包）即可完成构建。
 *
 * 用法：npm run build:sandbox
 */
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  base: '/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // 后端地址可用 VITE_API_TARGET 覆盖（后端改用其它端口时）
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  esbuild: false,
  // 依赖预打包（optimizeDeps）同样依赖 esbuild 服务进程，沙箱下必须关闭：
  // 关闭后 Vite 直接把 node_modules 中的 ESM 产物按原生模块提供给浏览器，
  // 首屏略慢，但功能与最终构建产物不受影响。
  optimizeDeps: {
    noDiscovery: true,
    include: []
  },
  build: {
    outDir: 'dist',
    // 显式清空输出目录：本配置通过 --configLoader native 加载，Vite 对 root 的判定
    // 可能与常规路径不同，导致默认的 emptyOutDir 失效——届时 dist/assets 会不断累积
    // 历次构建遗留的旧 chunk，并被构建脚本一并复制进后端 jar，白白增大体积。
    emptyOutDir: true,
    chunkSizeWarningLimit: 2000,
    minify: 'terser',
    cssMinify: false
  }
})
