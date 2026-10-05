import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Builds straight into Spring Boot's static folder. emptyOutDir is false because
 * that folder also holds media/how-it-works.mp4, which a build must not delete.
 * In dev, /api calls are proxied to Spring on port 8080 (session cookie included).
 */
export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: false,
    assetsDir: 'assets'
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false
      }
    }
  }
})
