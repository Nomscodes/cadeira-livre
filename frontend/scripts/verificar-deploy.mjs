// Confere que o build de DEPLOY (o mesmo de "npm run build") nao leva nenhum recurso de teste.
// Uso: npm run verificar:deploy      (falha com codigo 1 se achar algo)
import { execSync } from 'node:child_process'
import { readdirSync, readFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'

const saida = 'dist-verificacao'
rmSync(saida, { recursive: true, force: true })
execSync(`npx vite build --outDir ${saida}`, { stdio: 'pipe' })

const pasta = join(saida, 'assets')
const arquivos = readdirSync(pasta)
const codigo = arquivos.filter((f) => f.endsWith('.js')).map((f) => readFileSync(join(pasta, f), 'utf8')).join('\n')

const proibidos = ['somente teste', 'Navegação do protótipo', 'liberar a edição dos meus horários', 'edicao-horarios', 'reiniciar dados']
const textosEncontrados = proibidos.filter((t) => codigo.includes(t))
const arquivosDeTeste = arquivos.filter((f) => /ProtoBar|AtalhoTeste/.test(f))
rmSync(saida, { recursive: true, force: true })

if (textosEncontrados.length || arquivosDeTeste.length) {
  console.error('FALHOU: o build de deploy ainda leva recursos de teste.')
  if (textosEncontrados.length) console.error('  textos encontrados:', textosEncontrados.join(' | '))
  if (arquivosDeTeste.length) console.error('  arquivos encontrados:', arquivosDeTeste.join(', '))
  console.error('  Confira frontend/.env.production (VITE_MODO_TESTE=false) e o vite.config.js.')
  process.exit(1)
}
console.log('OK: o build de deploy nao contem nenhum recurso de teste (barra de prototipo, atalho da agenda, rota /teste).')
