import { ApiError } from '../lib/errors'
import { getDb } from './db'

const routes = []

/** Registra uma rota. Padroes aceitam :params, ex.: '/saloes/:slug/servicos'. */
export function route(method, pattern, handler) {
  const keys = []
  const re = new RegExp('^' + pattern.replace(/:([a-zA-Z]+)/g, (_, k) => { keys.push(k); return '([^/]+)' }) + '$')
  routes.push({ method, re, keys, handler })
}

/** token mock: "mock.<tipo>.<id>" */
function sessionFromToken(token) {
  if (!token) return null
  const [, tipo, id] = token.split('.')
  if (tipo === 'staff') {
    const u = getDb().usuarios.find((x) => x.id === id)
    return u ? { tipo: 'staff', usuario: u } : null
  }
  if (tipo === 'cliente') return { tipo: 'cliente', contato: decodeURIComponent(id || '') }
  return null
}

const delay = (ms) => new Promise((r) => setTimeout(r, ms))

export async function mockRequest(method, path, { body, query, token } = {}) {
  await delay(120 + Math.random() * 180)
  const cleanPath = path.split('?')[0].replace(/\/$/, '') || '/'
  for (const r of routes) {
    if (r.method !== method) continue
    const m = r.re.exec(cleanPath)
    if (!m) continue
    const params = Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])]))
    const ctx = {
      params,
      query: query || {},
      body: body ? structuredClone(body) : {},
      session: sessionFromToken(token),
    }
    const out = await r.handler(ctx)
    return out === undefined || out === null ? null : structuredClone(out)
  }
  throw new ApiError(404, `Rota não encontrada: ${method} ${path}`)
}

/* ---------- guardas ---------- */
export function requireStaff(ctx) {
  if (ctx.session?.tipo !== 'staff') throw new ApiError(401, 'Sua sessão expirou. Entre novamente.')
  return ctx.session.usuario
}
export function requireAdmin(ctx) {
  const u = requireStaff(ctx)
  if (u.papel !== 'administrador') throw new ApiError(403, 'Apenas o administrador pode fazer isso.')
  return u
}
export function requireCliente(ctx) {
  if (ctx.session?.tipo !== 'cliente') throw new ApiError(401, 'Confirme seu código de acesso para continuar.')
  return ctx.session
}
