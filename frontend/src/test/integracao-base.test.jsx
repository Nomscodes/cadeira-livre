import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import StatusChip from '../components/ui/StatusChip'
import { hhmm } from '../lib/dates'
import { STATUS } from '../lib/status'

/** Resposta falsa do backend, no formato que o fetch devolve. */
const resposta = (status, corpo) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => {
    if (corpo === undefined) throw new Error('sem corpo')
    return corpo
  },
})

/** Carrega o http.js apontando para o backend real (sem a API simulada), com um fetch falso no lugar da rede. */
async function apiReal(fetchFalso) {
  vi.stubEnv('VITE_USE_MOCK', 'false')
  vi.stubEnv('VITE_API_URL', 'http://localhost:8080/api')
  vi.stubGlobal('fetch', fetchFalso)
  vi.resetModules()
  const { api } = await import('../lib/http')
  return api
}

afterEach(() => {
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
  vi.resetModules()
})

describe('chamadas ao backend real', () => {
  it('rota do painel envia o token do dono no cabecalho Authorization', async () => {
    window.localStorage.setItem('cl:staff', JSON.stringify({ token: 'token-do-gabriel' }))
    const fetchFalso = vi.fn(async () => resposta(200, []))
    const api = await apiReal(fetchFalso)

    await api.get('/painel/servicos', { as: 'staff' })

    const [url, opcoes] = fetchFalso.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/painel/servicos')
    expect(opcoes.method).toBe('GET')
    expect(opcoes.headers.Authorization).toBe('Bearer token-do-gabriel')
  })

  it('rota publica nao envia token e monta os parametros da consulta', async () => {
    const fetchFalso = vi.fn(async () => resposta(200, { profissionais: [] }))
    const api = await apiReal(fetchFalso)

    await api.get('/saloes/barbearia-teste/disponibilidade', { query: { servicoId: 'abc', data: '2026-11-16', vazio: '' } })

    const [url, opcoes] = fetchFalso.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/saloes/barbearia-teste/disponibilidade?servicoId=abc&data=2026-11-16')
    expect(opcoes.headers.Authorization).toBeUndefined()
  })

  it('envia o corpo em JSON', async () => {
    const fetchFalso = vi.fn(async () => resposta(200, { token: 't' }))
    const api = await apiReal(fetchFalso)

    await api.post('/auth/login', { email: 'gabriel@teste.com', senha: '12345678' })

    const [, opcoes] = fetchFalso.mock.calls[0]
    expect(opcoes.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(opcoes.body)).toEqual({ email: 'gabriel@teste.com', senha: '12345678' })
  })

  it('resposta 204 (sem corpo) devolve null', async () => {
    const api = await apiReal(vi.fn(async () => resposta(204)))

    expect(await api.post('/auth/logout', undefined, { as: 'staff' })).toBeNull()
  })

  it('erro do backend vira ApiError com a mensagem e a lista de problemas', async () => {
    const erro422 = { message: 'Dados inválidos.', problemas: ['nome: não deve estar em branco'] }
    const api = await apiReal(vi.fn(async () => resposta(422, erro422)))

    const erro = await api.post('/painel/servicos', {}, { as: 'staff' }).catch((e) => e)

    expect(erro.status).toBe(422)
    expect(erro.message).toBe('Dados inválidos.')
    expect(erro.details.problemas).toEqual(['nome: não deve estar em branco'])
  })

  it('401 em rota com login avisa a aplicacao para encerrar a sessao', async () => {
    const api = await apiReal(vi.fn(async () => resposta(401, { message: 'Faça login para continuar.', problemas: [] })))
    const avisos = []
    const ouvir = (e) => avisos.push(e.detail.as)
    window.addEventListener('cl:unauthorized', ouvir)

    const erro = await api.get('/cliente/agendamentos', { as: 'cliente' }).catch((e) => e)
    window.removeEventListener('cl:unauthorized', ouvir)

    expect(erro.status).toBe(401)
    expect(avisos).toEqual(['cliente'])
  })

  it('servidor fora do ar vira erro com mensagem para o usuario', async () => {
    const api = await apiReal(vi.fn(async () => { throw new TypeError('Failed to fetch') }))

    const erro = await api.get('/saloes/barbearia-teste').catch((e) => e)

    expect(erro.status).toBe(0)
    expect(erro.message).toMatch(/Não foi possível conectar ao servidor/)
  })
})

describe('formatos do backend', () => {
  it('hora com segundos e mostrada como HH:mm', () => {
    expect(hhmm('09:00:00')).toBe('09:00')
    expect(hhmm('14:30')).toBe('14:30')
    expect(hhmm(null)).toBe('')
  })

  it('cada status do agendamento tem o seu rotulo', () => {
    const rotulos = { PENDENTE: 'pendente', APROVADO: 'aprovado', RECUSADO: 'recusado', CANCELADO: 'cancelado', CONCLUIDO: 'concluído' }
    // nenhum status do backend pode ficar sem rotulo
    expect(Object.keys(rotulos).sort()).toEqual(Object.keys(STATUS).sort())

    Object.entries(rotulos).forEach(([status, rotulo]) => {
      const { unmount } = render(<StatusChip status={STATUS[status]} />)
      expect(screen.getByText(rotulo)).toBeInTheDocument()
      unmount()
    })
  })

  it('pedido recusado ou cancelado aparece em vermelho e o concluido em verde', () => {
    const { rerender } = render(<StatusChip status={STATUS.RECUSADO} />)
    expect(screen.getByText('recusado')).toHaveClass('bad')
    rerender(<StatusChip status={STATUS.CANCELADO} />)
    expect(screen.getByText('cancelado')).toHaveClass('bad')
    rerender(<StatusChip status={STATUS.CONCLUIDO} />)
    expect(screen.getByText('concluído')).toHaveClass('ok')
  })
})
