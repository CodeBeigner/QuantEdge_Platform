import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

/** Dev-server proxy target (e.g. http://host.docker.internal:8080 when Vite runs in Docker). */
const backendTarget = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api/risk': {
        target: 'http://localhost:5002',
        changeOrigin: true,
      },
      '/api/calmar': {
        target: 'http://localhost:5005',
        changeOrigin: true,
      },
      '/api/classifier': {
        target: 'http://localhost:5006',
        changeOrigin: true,
      },
      '/api/stacking': {
        target: 'http://localhost:5008',
        changeOrigin: true,
      },
      '/infrastructure': {
        target: 'http://localhost:5007',
        changeOrigin: true,
      },
      '/api': {
        target: backendTarget,
        changeOrigin: true,
      },
      '/ws': {
        target: backendTarget,
        ws: true,
      },
      '/actuator': {
        target: backendTarget,
        changeOrigin: true,
      },
      '/delta-testnet': {
        target: 'https://cdn-ind.testnet.deltaex.org',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/delta-testnet/, ''),
        secure: true,
      },
      '/delta-prod': {
        target: 'https://api.india.delta.exchange',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/delta-prod/, ''),
        secure: true,
      },
    },
  },
})
