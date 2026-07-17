import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Escuchar en todas las interfaces. Por defecto Vite queda solo en IPv6 ([::1]) y el
    // navegador que resuelva localhost a 127.0.0.1 no encuentra nada.
    host: true,
    // El backend Spring corre aparte en :8080. Proxyeamos /api para que en desarrollo
    // el navegador vea un mismo origen y no haya que configurar CORS.
    proxy: {
      '/api': {
        // 127.0.0.1 y no localhost: si el backend escucha en IPv4 y Node resuelve localhost
        // a ::1, el proxy falla con ECONNREFUSED.
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
