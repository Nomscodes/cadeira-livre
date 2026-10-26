import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

/**
 * Marcas de compilacao (substituidas por true/false literais em cada arquivo na hora do build):
 *   __MODO_TESTE__     recursos SO de teste: barra do prototipo, atalho da agenda do funcionario, rota /teste da API simulada
 *   __MOSTRAR_PROTO__  a barra de navegacao do prototipo
 * Com literal false o bundler descarta o codigo e ate os arquivos desses recursos. Por isso o build de deploy
 * (frontend/.env.production define VITE_MODO_TESTE=false) NAO leva nada de teste.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const mock = env.VITE_USE_MOCK !== 'false'
  const modoTeste = mock && env.VITE_MODO_TESTE !== 'false'
  const mostrarProto = modoTeste && env.VITE_PROTO_BAR !== 'false' && mode !== 'test' // a barra tem teste proprio
  return {
    plugins: [react()],
    server: { port: 5173 },
    define: {
      __MODO_TESTE__: JSON.stringify(modoTeste),
      __MOSTRAR_PROTO__: JSON.stringify(mostrarProto),
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.js'],
      globals: true,
      css: false,
      testTimeout: 15000,
    },
  }
})
