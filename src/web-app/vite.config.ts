import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/v1/chats': 'http://localhost:8083',
      '/ws': {
        target: 'ws://localhost:8083',
        ws: true,
      },
      '/api/v1/products': 'http://localhost:8082',
      '/api': 'http://localhost:8081',
    },
  },
})
