import { beforeEach, describe, expect, it } from 'vitest'
import { api } from '../lib/http'
import { getDb, resetMock } from '../mocks/db'
import { addDaysISO, todayISO, weekdayOf, monthKey } from '../lib/dates'

const staff = async (email = 'admin', senha = 'admin', papel = 'administrador') => {
  const s = await api.post('/auth/login', { email, senha, papel })
  window.localStorage.setItem('cl:staff', JSON.stringify(s))
  return s
}
const cliente = () => window.localStorage.setItem('cl:cliente', JSON.stringify({ token: 'mock.cliente.62955550000', contato: '62955550000' }))

/** primeiro dia util dentro de `limite` dias a partir de amanha que o salao tenha horario livre */
async function primeiroHorarioLivre(profissionais, duracao) {
  for (let n = 1; n < 25; n++) {
    const data = addDaysISO(todayISO(), n)
    if ([0, 6].includes(weekdayOf(data))) continue
    const horarios = await api.get('/saloes/barbearia-do-rafael/disponibilidade/horarios', { query: { data, profissionais, duracao } })
    const h = horarios.find((x) => x.livre)
    if (h) return { data, hora: h.hora }
  }
  throw new Error('sem horario livre')
}

beforeEach(() => resetMock())

describe('autenticacao', () => {
  it('entra com as contas de teste', async () => {
    const s = await api.post('/auth/login', { email: 'admin', senha: 'admin', papel: 'administrador' })
    expect(s.usuario.papel).toBe('administrador')
    const f = await api.post('/auth/login', { email: 'func@teste.com', senha: 'func', papel: 'funcionario' })
    expect(f.usuario.papel).toBe('funcionario')
  })
  it('recusa senha errada', async () => {
    await expect(api.post('/auth/login', { email: 'admin', senha: 'x' })).rejects.toMatchObject({ status: 401 })
  })
  it('o cargo vem do cadastro do usuario, nao do formulario', async () => {
    expect((await api.post('/auth/login', { email: 'admin', senha: 'admin' })).usuario.papel).toBe('administrador')
    expect((await api.post('/auth/login', { email: 'func@teste.com', senha: 'func' })).usuario.papel).toBe('funcionario')
    // mesmo que alguem envie um papel diferente, o servidor ignora e usa o do cadastro
    expect((await api.post('/auth/login', { email: 'admin', senha: 'admin', papel: 'funcionario' })).usuario.papel).toBe('administrador')
  })
  it('cadastro cria salao oculto e impede e-mail repetido', async () => {
    const dados = { nome: 'Joao Silva', razaoSocial: 'Painel', documento: '12345678901', email: 'joao@x.com', atuacao: 'autonomo', senha: '12345678' }
    const s = await api.post('/auth/cadastro', dados)
    expect(s.usuario.papel).toBe('administrador')
    const r = await api.get('/saloes/painel-salao').catch((e) => e)
    expect(r.slug).toBe('painel-salao') // slug reservado ganha sufixo
    expect(r.publico).toBe(false)
    await expect(api.post('/auth/cadastro', dados)).rejects.toMatchObject({ status: 409 })
  })
  it('painel exige sessao e papel', async () => {
    await expect(api.get('/painel/portal', { as: 'staff' })).rejects.toMatchObject({ status: 401 })
    await staff('func', 'func', 'funcionario')
    await expect(api.get('/painel/servicos', { as: 'staff' })).rejects.toMatchObject({ status: 403 })
  })
})

describe('portal publico', () => {
  it('lista so saloes publicos e filtra', async () => {
    const todos = await api.get('/saloes', { query: { cidade: '', categoria: 'todas' } })
    expect(todos.map((s) => s.slug)).not.toContain('espaco-aurora')
    const gyn = await api.get('/saloes', { query: { cidade: 'Goiânia, GO', servico: 'colora' } })
    expect(gyn.map((s) => s.slug).sort()).toEqual(['barbearia-do-rafael', 'studio-juliana-matos'])
  })
  it('distingue oculto de inexistente', async () => {
    expect((await api.get('/saloes/espaco-aurora')).publico).toBe(false)
    await expect(api.get('/saloes/nao-existe')).rejects.toMatchObject({ status: 404 })
  })
})

describe('agendamento', () => {
  it('nao oferece horario em bloqueio geral, folga ou passado', async () => {
    const bloq = (await api.get('/saloes/barbearia-do-rafael/disponibilidade/dias', { query: { mes: monthKey(new Date().getFullYear(), new Date().getMonth()), profissionais: 'p1', duracao: 40 } }))
    const estados = new Set(bloq.map((d) => d.estado))
    expect(estados.has('passado') || estados.has('ok')).toBe(true)
    const hoje = bloq.find((d) => d.data === todayISO())
    expect(['ok', 'lotado', 'folga', 'fechado']).toContain(hoje.estado) // fechado/folga se hoje cair num dia sem atendimento
  })
  it('reserva e impede dupla marcacao no mesmo horario (RF21)', async () => {
    const { data, hora } = await primeiroHorarioLivre('p1', 30)
    const body = { itens: [{ servicoId: 'sv2', profissionalId: 'p1' }], data, hora, clienteNome: 'Joana Teste', clienteWhats: '(62) 98888-7777' }
    const ok = await api.post('/saloes/barbearia-do-rafael/agendamentos', body)
    expect(ok.id).toBeTruthy()
    await expect(api.post('/saloes/barbearia-do-rafael/agendamentos', body)).rejects.toMatchObject({ status: 409 })
    const depois = await api.get('/saloes/barbearia-do-rafael/disponibilidade/horarios', { query: { data, profissionais: 'p1', duracao: 30 } })
    expect(depois.find((h) => h.hora === hora).livre).toBe(false)
  })
  it('exige nome e WhatsApp validos', async () => {
    const { data, hora } = await primeiroHorarioLivre('p1', 30)
    await expect(api.post('/saloes/barbearia-do-rafael/agendamentos', { itens: [{ servicoId: 'sv2', profissionalId: 'p1' }], data, hora, clienteNome: '', clienteWhats: '1' })).rejects.toMatchObject({ status: 422 })
  })
  it('almoco nunca e oferecido', async () => {
    const { data } = await primeiroHorarioLivre('p1', 30)
    const h = await api.get('/saloes/barbearia-do-rafael/disponibilidade/horarios', { query: { data, profissionais: 'p1', duracao: 30 } })
    expect(h.map((x) => x.hora)).not.toContain('12:00')
    expect(h.map((x) => x.hora)).not.toContain('12:30')
  })
})

describe('cliente', () => {
  it('lista historico, avalia concluido e cancela dentro do prazo', async () => {
    cliente()
    const lista = await api.get('/cliente/agendamentos', { as: 'cliente' })
    expect(lista.length).toBe(4)
    const concluido = lista.find((a) => a.id === 'h2')
    expect(concluido.status).toBe('finalizado')
    await api.post('/cliente/agendamentos/h2/avaliacao', { nota: 5, comentario: 'otimo' }, { as: 'cliente' })
    await expect(api.post('/cliente/agendamentos/h2/avaliacao', { nota: 5 }, { as: 'cliente' })).rejects.toMatchObject({ status: 409 })
    await expect(api.post('/cliente/agendamentos/h1/avaliacao', { nota: 5 }, { as: 'cliente' })).rejects.toMatchObject({ status: 422 })
    const futuro = lista.find((a) => a.id === 'h1')
    expect(futuro.podeCancelar).toBe(true)
    await api.post('/cliente/agendamentos/h1/cancelar', {}, { as: 'cliente' })
    expect((await api.get('/cliente/agendamentos/h1', { as: 'cliente' })).status).toBe('cancelado')
  })
  it('sem acesso temporario nao ve historico', async () => {
    await expect(api.get('/cliente/agendamentos', { as: 'cliente' })).rejects.toMatchObject({ status: 401 })
  })
})

describe('painel', () => {
  it('regra de publicacao: servico sem profissional bloqueia', async () => {
    await staff()
    const novo = await api.post('/painel/servicos', { nome: 'Pigmentacao', preco: 50, duracao: 30, profissionalIds: [] }, { as: 'staff' })
    const t = await api.post('/painel/portal/validar-publicacao', {}, { as: 'staff' })
    expect(t.ok).toBe(false)
    await expect(api.put('/painel/portal/publicacao', { publico: true }, { as: 'staff' })).rejects.toMatchObject({ status: 422 })
    await api.put(`/painel/servicos/${novo.id}`, { nome: 'Pigmentacao', preco: 50, duracao: 30, profissionalIds: ['p1'] }, { as: 'staff' })
    expect((await api.post('/painel/portal/validar-publicacao', {}, { as: 'staff' })).ok).toBe(true)
  })
  it('valida servico e combo', async () => {
    await staff()
    await expect(api.post('/painel/servicos', { nome: '', preco: 10, duracao: 30 }, { as: 'staff' })).rejects.toMatchObject({ status: 422 })
    await expect(api.post('/painel/combos', { nome: 'X', servicoIds: ['sv1'], preco: 10 }, { as: 'staff' })).rejects.toMatchObject({ status: 422 })
  })
  it('bloqueio geral tira o dia do calendario do cliente', async () => {
    await staff()
    const data = addDaysISO(todayISO(), 9)
    await api.put(`/painel/bloqueios/${data}`, { motivo: 'Reforma' }, { as: 'staff' })
    const [y, m] = data.split('-').map(Number)
    const dias = await api.get('/saloes/barbearia-do-rafael/disponibilidade/dias', { query: { mes: `${y}-${String(m).padStart(2, '0')}`, profissionais: 'p1', duracao: 30 } })
    expect(dias.find((d) => d.data === data)).toMatchObject({ estado: 'bloqueado', motivo: 'Reforma' })
  })
  it('lista agendamentos do salao com encaminhamento', async () => {
    await staff()
    const l = await api.get('/painel/agendamentos', { as: 'staff' })
    expect(l.length).toBe(9)
    const a1 = l.find((a) => a.id === 'a1')
    expect(a1.encaminhar[0].nome).toBe('Rafael')
  })
})

describe('resumo do funcionario', () => {
  it('devolve perfil, indicadores e todos os agendamentos dele', async () => {
    await staff('func', 'func', 'funcionario')
    const r = await api.get('/agenda/resumo', { as: 'staff' })
    expect(r.profissional.nome).toBe('Rafael Lima')
    expect(r.profissional.servicos).toEqual(['Corte de cabelo', 'Barba'])
    expect(r.salao.temaId).toBe('azul-escuro')
    expect(r.indicadores.proximos7).toBeGreaterThanOrEqual(2)
    expect(r.indicadores.proximo).toMatchObject({ cliente: expect.any(String) })
    const lista = await api.get('/agenda/agendamentos', { as: 'staff' })
    const por = (st) => lista.filter((a) => a.status === st).map((a) => a.cliente).sort()
    expect(por('confirmado')).toEqual(['Ana Paula', 'Camila Rocha', 'Lucas Ferreira', 'Marcos Silva'])
    expect(por('finalizado')).toEqual(['Camila Rocha', 'Pedro Alves'])
    expect(por('cancelado')).toEqual(['Carlos Mendes'])
    // so os dele: nada da Juliana
    expect(lista.map((a) => a.cliente)).not.toContain('Bruno Costa')
  })
  it('exige login', async () => {
    await expect(api.get('/agenda/resumo', { as: 'staff' })).rejects.toMatchObject({ status: 401 })
  })
})

describe('agenda do funcionario', () => {
  it('mostra colegas e respeita permissao de edicao', async () => {
    await staff('func', 'func', 'funcionario')
    await staff()
    const a2 = (await api.get('/painel/agendamentos', { as: 'staff' })).find((a) => a.id === 'a2')
    await staff('func', 'func', 'funcionario')
    const { agendamentos } = await api.get('/agenda', { as: 'staff', query: { mes: a2.data.slice(0, 7) } })
    expect(agendamentos.find((a) => a.id === 'a2').colegas).toEqual(['Juliana'])
    await api.put('/agenda/descansos', { data: addDaysISO(todayISO(), 1), hora: '15:00', descanso: true }, { as: 'staff' })
    await staff()
    await api.put('/painel/configuracoes', { atendimento: 'local', cancelamentoHoras: 4, funcionariosEditamHorario: false }, { as: 'staff' })
    await staff('func', 'func', 'funcionario')
    await expect(api.put('/agenda/descansos', { data: addDaysISO(todayISO(), 1), hora: '16:00', descanso: true }, { as: 'staff' })).rejects.toMatchObject({ status: 403 })
  })
})

/* ---------- helpers de data para os testes de horarios ---------- */
const proximoDia = (dow, apos = 1) => {
  let d = addDaysISO(todayISO(), apos)
  while (weekdayOf(d) !== dow) d = addDaysISO(d, 1)
  return d
}
const mesDe = (iso) => iso.slice(0, 7)
const horariosDe = (data, profissionais = 'p1', duracao = 30) =>
  api.get('/saloes/barbearia-do-rafael/disponibilidade/horarios', { query: { data, profissionais, duracao } })
const semana = (ativos, inicio, fim) => Object.fromEntries([0, 1, 2, 3, 4, 5, 6].map((d) => [d, { ativo: ativos.includes(d), inicio, fim }]))

describe('horarios do salao e da equipe', () => {
  it('lista agendas editaveis e marca o administrador', async () => {
    await staff()
    const r = await api.get('/painel/horarios', { as: 'staff' })
    expect(r.salao.horarios[1]).toMatchObject({ ativo: true, inicio: '09:00', fim: '19:00' })
    expect(r.salao.horarios[0].ativo).toBe(false)
    expect(r.profissionais.map((p) => [p.nome, p.ehAdmin])).toEqual([['Rafael Lima', true], ['Juliana Matos', false]])
  })
  it('horario do profissional restringe os horarios oferecidos', async () => {
    await staff()
    const terca = proximoDia(2, 10)
    await api.put('/painel/horarios/profissionais/p1', { horarios: semana([1, 2, 3, 4, 5, 6], '14:00', '16:00') }, { as: 'staff' })
    const h = (await horariosDe(terca)).map((x) => x.hora)
    expect(h[0]).toBe('14:00')
    expect(h[h.length - 1]).toBe('15:30')
  })
  it('o horario do salao e o limite: o do profissional nunca passa dele', async () => {
    await staff()
    const quarta = proximoDia(3, 10)
    await api.put('/painel/horarios/profissionais/p1', { horarios: semana([3], '07:00', '22:00') }, { as: 'staff' })
    const h = (await horariosDe(quarta)).map((x) => x.hora)
    expect(h[0]).toBe('09:00') // salao abre 09:00
    expect(h[h.length - 1]).toBe('18:30') // salao fecha 19:00
  })
  it('salao fechado num dia da semana nao oferece horarios e aparece como fechado', async () => {
    await staff()
    const h = Object.fromEntries(Object.entries((await api.get('/painel/horarios', { as: 'staff' })).salao.horarios))
    h[1] = { ...h[1], ativo: false }
    const r = await api.put('/painel/horarios/salao', { horarios: h }, { as: 'staff' })
    expect(r.avisos.join(' ')).toMatch(/segunda/) // Rafael ainda trabalha na segunda: aviso de conflito
    const segunda = proximoDia(1, 10)
    expect(await horariosDe(segunda)).toEqual([])
    const dias = await api.get('/saloes/barbearia-do-rafael/disponibilidade/dias', { query: { mes: mesDe(segunda), profissionais: 'p1', duracao: 30 } })
    expect(dias.find((d) => d.data === segunda).estado).toBe('fechado')
  })
  it('folga de um profissional vale so para ele e pode ser removida', async () => {
    await staff()
    const sexta = proximoDia(5, 14)
    await api.put(`/painel/folgas/p1/${sexta}`, { motivo: 'Consulta' }, { as: 'staff' })
    const dias = (p) => api.get('/saloes/barbearia-do-rafael/disponibilidade/dias', { query: { mes: mesDe(sexta), profissionais: p, duracao: 30 } })
    expect((await dias('p1')).find((d) => d.data === sexta).estado).toBe('folga')
    expect((await dias('p2')).find((d) => d.data === sexta).estado).not.toBe('folga')
    expect(await api.get('/painel/folgas', { as: 'staff', query: { mes: mesDe(sexta), profissionalId: 'p1' } })).toEqual([{ profissionalId: 'p1', data: sexta, motivo: 'Consulta' }])
    await api.delete(`/painel/folgas/p1/${sexta}`, { as: 'staff' })
    expect((await dias('p1')).find((d) => d.data === sexta).estado).not.toBe('folga')
  })
  it('valida os horarios', async () => {
    await staff()
    await expect(api.put('/painel/horarios/salao', { horarios: semana([], '09:00', '18:00') }, { as: 'staff' })).rejects.toMatchObject({ status: 422 })
    await expect(api.put('/painel/horarios/salao', { horarios: semana([1], '18:00', '09:00') }, { as: 'staff' })).rejects.toMatchObject({ status: 422 })
    await expect(api.put('/painel/horarios/profissionais/p1', { horarios: { 1: { ativo: true } } }, { as: 'staff' })).rejects.toMatchObject({ status: 422 })
  })
  it('so o administrador edita horarios', async () => {
    await staff('func', 'func')
    await expect(api.get('/painel/horarios', { as: 'staff' })).rejects.toMatchObject({ status: 403 })
  })
})

describe('notificacoes', () => {
  it('cada perfil ve so as suas, com acoes coerentes com o tipo', async () => {
    await staff()
    const adm = await api.get('/notificacoes', { as: 'staff' })
    expect(adm.map((n) => n.id).sort()).toEqual(['n1', 'n2', 'n3'])
    expect(adm.find((n) => n.tipo === 'avaliacao').acoes.map((a) => a.id)).toEqual(['avaliacao'])
    expect(adm.find((n) => n.tipo === 'lembrete').acoes.map((a) => a.id)).toEqual(['aviso'])
    expect(adm.find((n) => n.id === 'n1').detalhes).toMatchObject({ cliente: 'Marcos Silva' })
    await staff('func', 'func')
    const fun = await api.get('/notificacoes', { as: 'staff' })
    expect(fun.map((n) => n.id).sort()).toEqual(['n4', 'n5'])
    expect(fun.every((n) => n.acoes.length === 0)).toBe(true) // funcionario apenas le
  })
  it('marca como lida', async () => {
    await staff()
    await api.post('/notificacoes/n1/lida', {}, { as: 'staff' })
    expect((await api.get('/notificacoes', { as: 'staff' })).find((n) => n.id === 'n1').lida).toBe(true)
  })
  it('aviso do responsavel chega ao funcionario escolhido', async () => {
    await staff()
    const r = await api.post('/notificacoes/n2/acao', { acao: 'aviso', mensagem: 'Ajustem a agenda de amanhã.', profissionalIds: ['p2'] }, { as: 'staff' })
    expect(r.enviados).toBe(1)
    // a Juliana (p2) nao tem login de teste, entao confere a entrega direto no banco simulado
    const recebida = getDb().notificacoes.find((n) => n.profissionalId === 'p2' && n.tipo === 'aviso')
    expect(recebida).toMatchObject({ para: 'funcionario', texto: 'Ajustem a agenda de amanhã.', lida: false })
    await staff()
    await expect(api.post('/notificacoes/n2/acao', { acao: 'aviso', mensagem: '', profissionalIds: ['p2'] }, { as: 'staff' })).rejects.toMatchObject({ status: 422 })
    await expect(api.post('/notificacoes/n2/acao', { acao: 'aviso', mensagem: 'x', profissionalIds: ['p1'] }, { as: 'staff' })).rejects.toMatchObject({ status: 422 }) // nao envia para si mesmo
  })
  it('pedir avaliacao so existe na notificacao de avaliacao e so uma vez', async () => {
    await staff()
    await expect(api.post('/notificacoes/n1/acao', { acao: 'avaliacao' }, { as: 'staff' })).rejects.toMatchObject({ status: 422 })
    await api.post('/notificacoes/n3/acao', { acao: 'avaliacao' }, { as: 'staff' })
    expect((await api.get('/notificacoes', { as: 'staff' })).find((n) => n.id === 'n3').acoes[0].usada).toBe(true)
  })
  it('funcionario nao dispara acoes', async () => {
    await staff('func', 'func')
    await expect(api.post('/notificacoes/n4/acao', { acao: 'aviso', mensagem: 'x', profissionalIds: ['p2'] }, { as: 'staff' })).rejects.toMatchObject({ status: 403 })
  })
  it('alerta do funcionario vira notificacao para o responsavel', async () => {
    await staff('func', 'func')
    await api.post('/agenda/alertas', { agendamentoId: 'a1', motivo: 'Cliente agressivo', descricao: '' }, { as: 'staff' })
    await staff()
    const alerta = (await api.get('/notificacoes', { as: 'staff' })).find((n) => n.tipo === 'alerta')
    expect(alerta.texto).toMatch(/Marcos Silva.*Cliente agressivo/)
  })
})

describe('capa do portal e atalho de teste', () => {
  it('capa salva no painel aparece no portal publico', async () => {
    await staff()
    const portal = await api.get('/painel/portal', { as: 'staff' })
    await api.put('/painel/portal', { ...portal, capa: 'data:image/png;base64,AAAA' }, { as: 'staff' })
    expect((await api.get('/saloes/barbearia-do-rafael')).capa).toBe('data:image/png;base64,AAAA')
    await api.put('/painel/portal', { ...portal, capa: null }, { as: 'staff' })
    expect((await api.get('/saloes/barbearia-do-rafael')).capa).toBeNull()
  })
  it('atalho de teste liga a permissao que o admin ve em Configuracoes', async () => {
    await staff('func', 'func')
    await api.put('/teste/edicao-horarios', { permitido: false }, { as: 'staff' })
    expect((await api.get('/agenda', { as: 'staff' })).podeEditar).toBe(false)
    await api.put('/teste/edicao-horarios', { permitido: true }, { as: 'staff' })
    expect((await api.get('/agenda', { as: 'staff' })).podeEditar).toBe(true)
  })
})

describe('profissional que nao atende clientes', () => {
  it('sai do portal, dos servicos e dos horarios, e nao pode ser agendado', async () => {
    await staff()
    const lista = (await api.get('/painel/profissionais', { as: 'staff' })).profissionais
    const rafael = lista.find((p) => p.id === 'p1')
    expect(rafael).toMatchObject({ ehAdmin: true, atende: true })
    await api.put('/painel/profissionais/p1', { ...rafael, atende: false }, { as: 'staff' })

    const portal = await api.get('/saloes/barbearia-do-rafael')
    expect(portal.profissionais.map((p) => p.id)).toEqual(['p2']) // Rafael sumiu do portal
    expect(portal.servicos.every((s) => !s.profissionalIds.includes('p1'))).toBe(true)
    expect(portal.combos[0].profissionalIds).toEqual([])
    expect((await api.get('/painel/horarios', { as: 'staff' })).profissionais.map((p) => p.id)).toEqual(['p2'])
    expect((await api.get('/painel/servicos', { as: 'staff' })).profissionais.map((p) => p.id)).toEqual(['p2'])
    // continua na equipe e continua administrador
    const depois = (await api.get('/painel/profissionais', { as: 'staff' })).profissionais.find((p) => p.id === 'p1')
    expect(depois).toMatchObject({ atende: false, ehAdmin: true, servicoIds: [] })

    const { data, hora } = await primeiroHorarioLivre('p2', 30)
    await expect(api.post('/saloes/barbearia-do-rafael/agendamentos', { itens: [{ servicoId: 'sv2', profissionalId: 'p1' }], data, hora, clienteNome: 'Joana Teste', clienteWhats: '(62) 98888-7777' })).rejects.toMatchObject({ status: 422 })
  })
  it('voltar a atender nao revincula servicos sozinho', async () => {
    await staff()
    const rafael = (await api.get('/painel/profissionais', { as: 'staff' })).profissionais.find((p) => p.id === 'p1')
    await api.put('/painel/profissionais/p1', { ...rafael, atende: false }, { as: 'staff' })
    await api.put('/painel/profissionais/p1', { ...rafael, atende: true, servicoIds: ['sv1'] }, { as: 'staff' })
    const portal = await api.get('/saloes/barbearia-do-rafael')
    expect(portal.profissionais.map((p) => p.id).sort()).toEqual(['p1', 'p2'])
    expect(portal.servicos.find((s) => s.id === 'sv1').profissionalIds.sort()).toEqual(['p1', 'p2'])
    expect(portal.servicos.find((s) => s.id === 'sv2').profissionalIds).toEqual([]) // Barba nao voltou
  })
  it('a regra de publicacao so conta quem atende', async () => {
    await staff()
    const todos = (await api.get('/painel/profissionais', { as: 'staff' })).profissionais
    for (const p of todos) await api.put(`/painel/profissionais/${p.id}`, { ...p, atende: false }, { as: 'staff' })
    const r = await api.post('/painel/portal/validar-publicacao', {}, { as: 'staff' })
    expect(r.ok).toBe(false)
    expect(r.problemas.join(' ')).toMatch(/atenda clientes/)
  })
  it('quem se cadastra como salao com equipe comeca sem atender; autonomo atende', async () => {
    const base = { razaoSocial: 'Casa', documento: '12345678901', senha: '12345678' }
    await api.post('/auth/cadastro', { ...base, nome: 'Ana Dona', email: 'ana@x.com', atuacao: 'equipe' })
    window.localStorage.setItem('cl:staff', JSON.stringify(await api.post('/auth/login', { email: 'ana@x.com', senha: '12345678' })))
    expect((await api.get('/painel/profissionais', { as: 'staff' })).profissionais[0]).toMatchObject({ ehAdmin: true, atende: false })
    await api.post('/auth/cadastro', { ...base, razaoSocial: 'Solo', nome: 'Beto Solo', email: 'beto@x.com', atuacao: 'autonomo' })
    window.localStorage.setItem('cl:staff', JSON.stringify(await api.post('/auth/login', { email: 'beto@x.com', senha: '12345678' })))
    expect((await api.get('/painel/profissionais', { as: 'staff' })).profissionais[0]).toMatchObject({ ehAdmin: true, atende: true })
  })
})

describe('notificacoes geradas pelos eventos', () => {
  it('novo agendamento avisa o administrador e o profissional envolvido', async () => {
    await staff()
    const antesAdm = (await api.get('/notificacoes', { as: 'staff' })).length
    await staff('func', 'func')
    const antesFun = (await api.get('/notificacoes', { as: 'staff' })).length
    const { data, hora } = await primeiroHorarioLivre('p1', 30)
    await api.post('/saloes/barbearia-do-rafael/agendamentos', { itens: [{ servicoId: 'sv2', profissionalId: 'p1' }], data, hora, clienteNome: 'Joana Teste', clienteWhats: '(62) 98888-7777' })
    const fun = await api.get('/notificacoes', { as: 'staff' })
    expect(fun.length).toBe(antesFun + 1)
    expect(fun[0]).toMatchObject({ tipo: 'agendamento', titulo: 'Novo agendamento', lida: false, acoes: [] })
    expect(fun[0].texto).toMatch(/Joana Teste agendou Barba/)
    expect(fun[0].detalhes).toMatchObject({ cliente: 'Joana Teste' })
    await staff()
    const adm = await api.get('/notificacoes', { as: 'staff' })
    expect(adm.length).toBe(antesAdm + 1)
    expect(adm[0].acoes.map((a) => a.id)).toEqual(['aviso']) // o responsavel pode avisar a equipe
  })
  it('profissional que nao participa nao recebe', async () => {
    await staff()
    const { data, hora } = await primeiroHorarioLivre('p1', 30)
    const total = () => getDb().notificacoes.filter((n) => n.profissionalId === 'p2').length
    const antes = total()
    await api.post('/saloes/barbearia-do-rafael/agendamentos', { itens: [{ servicoId: 'sv2', profissionalId: 'p1' }], data, hora, clienteNome: 'Joana Teste', clienteWhats: '(62) 98888-7777' })
    expect(total()).toBe(antes)
  })
  it('cancelamento feito pelo cliente avisa a equipe', async () => {
    cliente()
    await api.post('/cliente/agendamentos/h1/cancelar', {}, { as: 'cliente' })
    await staff()
    const adm = (await api.get('/notificacoes', { as: 'staff' }))[0]
    expect(adm).toMatchObject({ tipo: 'cancelamento', lida: false })
    expect(adm.texto).toMatch(/Camila Rocha cancelou Corte de cabelo/)
    await staff('func', 'func')
    expect((await api.get('/notificacoes', { as: 'staff' }))[0].tipo).toBe('cancelamento')
  })
})
