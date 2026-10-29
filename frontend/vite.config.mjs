import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端地址。默认 8080；若后端改用了其它端口（例如 8080 被别的项目占用），
// 启动前端前设置： $env:VITE_API_TARGET = 'http://localhost:8081'
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080'

// Vite 配置：开发端口 5173，/api 反向代理到后端
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
        target: apiTarget,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 2000
  }
})
