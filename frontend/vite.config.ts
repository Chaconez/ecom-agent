import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * 本地开发把 /api 反代到后端，与线上 nginx 行为保持一致：
 * 前端代码默认走同源（window.location.origin），无需再配 VITE_API_BASE。
 * 后端换端口时用 VITE_DEV_PROXY_TARGET 覆盖。
 */
const backendTarget = process.env.VITE_DEV_PROXY_TARGET || 'http://localhost:8094'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 聊天接口是 SSE 流式响应，关闭缓冲保证逐字输出
      '/api': {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },
})
