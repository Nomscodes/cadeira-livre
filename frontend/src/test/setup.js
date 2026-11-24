import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'
import { mockRequest } from './mocks'
import { ApiError } from '../lib/errors'

// jsdom nao implementa algumas APIs usadas pela interface
window.scrollTo = () => {}
window.open = () => null

/**
 * Nos testes, cada chamada de rede `/api/...` e respondida pela API de teste (src/test/mocks),
 * que segue as regras do backend. O codigo do app nao sabe disso: ele faz o fetch normal.
 */
async function fetchDaApiDeTeste(url, init = {}) {
  const alvo = new URL(String(url), 'http://localhost')
  if (!alvo.pathname.startsWith('/api/')) throw new TypeError(`fetch fora da API de teste: ${alvo.pathname}`)
  const path = alvo.pathname.slice('/api'.length)
  const query = Object.fromEntries(alvo.searchParams)
  const token = (init.headers?.Authorization || '').replace(/^Bearer /, '') || null
  const body = init.body === undefined ? undefined : JSON.parse(init.body)
  const cabecalhos = { 'Content-Type': 'application/json' }
  try {
    const dados = await mockRequest(init.method || 'GET', path, { body, query, token })
    if (dados === null) return new Response(null, { status: 204 })
    return new Response(JSON.stringify(dados), { status: 200, headers: cabecalhos })
  } catch (e) {
    if (e instanceof ApiError) {
      return new Response(JSON.stringify({ ...(e.details || {}), message: e.message }), { status: e.status, headers: cabecalhos })
    }
    throw e
  }
}

beforeEach(() => {
  window.localStorage.clear()
  document.documentElement.removeAttribute('style')
  vi.stubGlobal('fetch', fetchDaApiDeTeste)
})
afterEach(() => cleanup())
