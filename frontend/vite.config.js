import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Vite build configuration.
 *
 * The output goes straight into Spring Boot's static folder, so `npm run build`
 * is the only step between changing a component and seeing it at
 * http://localhost:8080/. Spring serves whatever is in there; it does not need
 * to know React exists.
 *
 * `emptyOutDir` is false on purpose. That folder also holds media/how-it-works.mp4
 * and the original plain-HTML pages, and a build must not delete them.
 *
 * `base: './'` makes the generated <script> and <link> tags use relative paths.
 * With an absolute base the bundle would be requested from /assets/... which is
 * fine here, but relative keeps it working if the app is ever served from a
 * sub-path.
 *
 * During `npm run dev` the proxy sends /api calls to the Spring app on 8080, so
 * the dev server on 5173 talks to the real backend — including the JSESSIONID
 * cookie, which is what keeps you logged in while developing.
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
