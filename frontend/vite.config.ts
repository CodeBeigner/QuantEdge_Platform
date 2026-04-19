import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

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
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true,
      },
      '/actuator': {
        target: 'http://localhost:8080',
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
