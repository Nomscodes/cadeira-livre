import { ApiError } from './errors'
import { store } from './storage'

export const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'
const API_URL = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '')

export const STORAGE_KEYS = { staff: 'cl:staff', cliente: 'cl:cliente' }

export { ApiError }

const tokenFor = (as) => {
  if (as === 'cliente') return store.get(STORAGE_KEYS.cliente)?.token || null
  if (as === 'staff') return store.get(STORAGE_KEYS.staff)?.token || null
  return null
}

/**
 * Unico ponto de saida de rede do front.
 * - VITE_USE_MOCK=true  -> responde com src/mocks (sem backend)
 * - VITE_USE_MOCK=false -> fetch no backend Spring Boot (VITE_API_URL)
 * Os componentes nao sabem qual dos dois esta ativo.
 *
 * `as`: 'staff' (responsavel/funcionario), 'cliente' (acesso temporario) ou omitido (publico).
 */
async function perform(method, path, { body, query, as } = {}) {
  const token = tokenFor(as)

  if (USE_MOCK) {
    const { mockRequest } = await import('../mocks')
    return mockRequest(method, path, { body, query, token })
  }

  const qs = query
    ? '?' + new URLSearchParams(Object.entries(query).filter(([, v]) => v !== undefined && v !== null && v !== '')).toString()
    : ''
  let res
  try {
    res = await fetch(`${API_URL}${path}${qs}`, {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Não foi possível conectar ao servidor. Verifique sua internet e tente novamente.')
  }
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new ApiError(res.status, data?.message || 'Algo deu errado. Tente novamente.', data)
  return data
}

export async function request(method, path, opts = {}) {
  try {
    return await perform(method, path, opts)
  } catch (err) {
    if (err instanceof ApiError && err.status === 401 && opts.as) {
      window.dispatchEvent(new CustomEvent('cl:unauthorized', { detail: { as: opts.as } }))
    }
    throw err
  }
}

export const api = {
  get: (path, opts) => request('GET', path, opts),
  post: (path, body, opts) => request('POST', path, { ...opts, body }),
  put: (path, body, opts) => request('PUT', path, { ...opts, body }),
  patch: (path, body, opts) => request('PATCH', path, { ...opts, body }),
  delete: (path, opts) => request('DELETE', path, opts),
}
