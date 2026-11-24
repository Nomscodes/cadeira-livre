import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

/**
 * Marca de compilacao __MODO_TESTE__ (substituida por true/false LITERAL em cada arquivo na hora do build).
 * So os testes (mode "test") ligam recursos de teste. No dev e no build de deploy ela e false.
 */
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    port: 5173,
    // em desenvolvimento, chamadas para /api vao para o backend: dispensa o .env
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
  define: {
    __MODO_TESTE__: JSON.stringify(mode === 'test'),
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    globals: true,
    css: false,
    testTimeout: 15000,
  },
}))
