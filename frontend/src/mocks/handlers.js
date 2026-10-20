import { ApiError } from '../lib/errors'
import { digits, isEmail, slugify } from '../lib/format'
import { addDaysISO, formatDateBR, monthKey, startOf, todayISO } from '../lib/dates'
import { getDb, nextId, resetMock, save, semana } from './db'
import { route, requireAdmin, requireCliente, requireStaff } from './router'
import {
  avisosDeConflito, bloqueioDoDia, calcularItens, diasDoMes, horariosDoDia, statusEfetivo,
} from './availability'

const CODIGO_TESTE = '123456'
const db = () => getDb()
const norm = (t = '') => String(t).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()

const salaoPorSlug = (slug) => db().saloes.find((s) => s.slug === slug)
const salaoDoStaff = (u) => db().saloes.find((s) => s.id === u.salaoId)
const nomeProf = (p) => p?.nomeSocial || p?.nome || ''

function sessaoStaff(u) {
  return {
    token: `mock.staff.${u.id}`,
    usuario: { id: u.id, nome: u.nome, papel: u.papel, salaoId: u.salaoId, profissionalId: u.profissionalId },
  }
}

/* =========================================================
   AUTENTICACAO
   ========================================================= */
route('POST', '/auth/login', ({ body }) => {
  const id = norm(body.email).trim()
  const u = db().usuarios.find((x) => norm(x.email) === id || x.apelidos?.includes(id))
  if (!u || u.senha !== body.senha) throw new ApiError(401, 'E-mail ou senha incorretos.')
  return sessaoStaff(u)
})

route('POST', '/auth/cadastro', ({ body }) => {
  const email = String(body.email || '').trim().toLowerCase()
  if (!isEmail(email)) throw new ApiError(422, 'Informe um e-mail válido.')
  if (db().usuarios.some((u) => norm(u.email) === email)) throw new ApiError(409, 'Este e-mail já está cadastrado.')
  if (!body.senha || body.senha.length < 8) throw new ApiError(422, 'A senha precisa ter ao menos 8 caracteres.')

  const RESERVADOS = ['login', 'recuperar-senha', 'acesso', 'buscar', 'meus-agendamentos', 'avaliar', 'painel', 'agenda', 'api']
  let base = slugify(body.razaoSocial || body.nome) || 'meu-salao'
  if (RESERVADOS.includes(base)) base = `${base}-salao`
  let slug = base
  let n = 2
  while (db().saloes.some((s) => s.slug === slug)) slug = `${base}-${n++}`

  const salaoId = nextId('s')
  const profId = nextId('p')
  db().saloes.push({
    id: salaoId, slug, nome: body.razaoSocial || body.nome, cidade: '', categoria: 'barbearia',
    temaId: 'vermelho-escuro', publico: false, logo: null, galeria: [], sobre: '', whatsapp: '', endereco: '',
    redes: '', capa: null, horarios: semana([1, 2, 3, 4, 5], '09:00', '18:00'), atendimento: 'local', cancelamentoHoras: 4, funcionariosEditamHorario: false,
    adminIds: [profId], documento: body.documento, atuacao: body.atuacao,
  })
  db().profissionais.push({
    id: profId, salaoId, nome: body.nome, nomeSocial: body.nome.split(' ')[0], whatsapp: '', encaminhar: false,
    atende: body.atuacao !== 'equipe', // quem tem equipe pode so gerenciar; o autonomo atende por definicao
    servicoIds: [], horarios: semana([1, 2, 3, 4, 5], '09:00', '18:00'),
  })
  const u = { id: nextId('u'), nome: body.nome, email, apelidos: [], senha: body.senha, papel: 'administrador', salaoId, profissionalId: profId }
  db().usuarios.push(u)
  save()
  return sessaoStaff(u)
})

route('POST', '/auth/recuperar', ({ body }) => {
  const email = norm(body.email).trim()
  if (!db().usuarios.some((u) => norm(u.email) === email)) throw new ApiError(404, 'Este e-mail não está cadastrado.')
  return null
})
route('POST', '/auth/recuperar/codigo', ({ body }) => {
  if (body.codigo !== CODIGO_TESTE) throw new ApiError(422, 'Código incorreto. Confira o e-mail e tente de novo.')
  return null
})
route('POST', '/auth/recuperar/redefinir', ({ body }) => {
  if (body.codigo !== CODIGO_TESTE) throw new ApiError(422, 'Código incorreto.')
  const u = db().usuarios.find((x) => norm(x.email) === norm(body.email))
  if (!u) throw new ApiError(404, 'Este e-mail não está cadastrado.')
  if (!body.novaSenha || body.novaSenha.length < 8) throw new ApiError(422, 'A senha precisa ter ao menos 8 caracteres.')
  // No mock, as contas de teste (admin/func) mantem a senha para nao travar as demonstracoes.
  if (!u.apelidos?.length) { u.senha = body.novaSenha; save() }
  return null
})

route('POST', '/acesso/codigo', ({ body }) => {
  if (!String(body.contato || '').trim()) throw new ApiError(422, 'Informe seu WhatsApp ou e-mail.')
  return null
})
route('POST', '/acesso/confirmar', ({ body }) => {
  if (body.codigo !== CODIGO_TESTE) throw new ApiError(422, 'Código incorreto. Confira a mensagem e tente de novo.')
  return { token: `mock.cliente.${encodeURIComponent(body.contato)}`, contato: body.contato }
})

/** Eventos do sistema avisam o administrador e cada profissional envolvido (novo agendamento, cancelamento do cliente). */
function notificarEvento(ag, tipo, titulo, texto) {
  const base = { salaoId: ag.salaoId, tipo, titulo, texto, criadaEm: new Date().toISOString(), lida: false, agendamentoId: ag.id }
  db().notificacoes.push({ id: nextId('n'), para: 'administrador', ...base })
  new Set(ag.itens.map((i) => i.profissionalId)).forEach((profissionalId) => db().notificacoes.push({ id: nextId('n'), para: 'funcionario', profissionalId, ...base }))
}

/* =========================================================
   PUBLICO: busca e portal do salao
   ========================================================= */
route('GET', '/saloes', ({ query }) => {
  const { cidade, categoria, servico, nome } = query
  return db().saloes
    .filter((s) => s.publico)
    .filter((s) => {
      // compara so o nome da cidade (antes da virgula), pelo inicio: "goi" acha Goiania, mas "goiania" nao acha "Aparecida de Goiania"
      const busca = norm(cidade || '').split(',')[0].trim()
      return !busca || norm(s.cidade).split(',')[0].trim().startsWith(busca)
    })
    .filter((s) => !categoria || categoria === 'todas' || s.categoria === categoria)
    .filter((s) => !nome || norm(s.nome).includes(norm(nome)))
    .filter((s) => !servico || db().servicos.some((v) => v.salaoId === s.id && norm(v.nome).includes(norm(servico))))
    .map(({ id, slug, nome: n, cidade: c, categoria: cat, temaId, logo }) => ({ id, slug, nome: n, cidade: c, categoria: cat, temaId, logo }))
})

function payloadPortal(s) {
  const atendem = db().profissionais.filter((p) => p.salaoId === s.id && p.atende !== false)
  const ids = new Set(atendem.map((p) => p.id))
  const servicos = db().servicos.filter((v) => v.salaoId === s.id).map((v) => ({ ...v, profissionalIds: v.profissionalIds.filter((id) => ids.has(id)) }))
  const combos = db().combos.filter((c) => c.salaoId === s.id).map((c) => ({
    ...c,
    profissionalIds: c.profissionalIds.filter((id) => ids.has(id)),
    duracao: c.servicoIds.reduce((t, id) => t + (servicos.find((v) => v.id === id)?.duracao || 0), 0),
  }))
  const profissionais = atendem.map((p) => ({ id: p.id, nome: nomeProf(p), servicoIds: p.servicoIds, horarios: p.horarios }))
  const { documento, atuacao, adminIds, ...publico } = s
  return { ...publico, servicos, combos, profissionais }
}

route('GET', '/saloes/:slug', ({ params }) => {
  const s = salaoPorSlug(params.slug)
  if (!s) throw new ApiError(404, 'Esta página não existe.')
  if (!s.publico) return { slug: s.slug, nome: s.nome, temaId: s.temaId, publico: false }
  return payloadPortal(s)
})

route('GET', '/saloes/:slug/disponibilidade/dias', ({ params, query }) => {
  const s = salaoPorSlug(params.slug)
  if (!s) throw new ApiError(404, 'Salão não encontrado.')
  const [y, m] = String(query.mes).split('-').map(Number)
  const profIds = String(query.profissionais || '').split(',').filter(Boolean)
  return diasDoMes(s.id, profIds, y, m - 1, Number(query.duracao) || 30)
})

route('GET', '/saloes/:slug/disponibilidade/horarios', ({ params, query }) => {
  const s = salaoPorSlug(params.slug)
  if (!s) throw new ApiError(404, 'Salão não encontrado.')
  const profIds = String(query.profissionais || '').split(',').filter(Boolean)
  return horariosDoDia(s.id, profIds, query.data, Number(query.duracao) || 30)
})

route('POST', '/saloes/:slug/agendamentos', ({ params, body, session }) => {
  const s = salaoPorSlug(params.slug)
  if (!s || !s.publico) throw new ApiError(404, 'Salão não encontrado.')
  if (!body.itens?.length) throw new ApiError(422, 'Escolha ao menos um serviço.')
  if (!String(body.clienteNome || '').trim() || digits(body.clienteWhats).length < 10) {
    throw new ApiError(422, 'Informe seu nome e um WhatsApp válido, com DDD.')
  }
  const { duracao, valor } = calcularItens(body.itens)
  const profIds = [...new Set(body.itens.map((i) => i.profissionalId))]
  if (profIds.some((id) => db().profissionais.find((p) => p.id === id)?.atende === false)) throw new ApiError(422, 'Este profissional não atende clientes.')
  const livre = horariosDoDia(s.id, profIds, body.data, duracao).find((h) => h.hora === body.hora)?.livre
  if (!livre) throw new ApiError(409, 'Esse horário acabou de ser ocupado. Escolha outro horário.')

  const ag = {
    id: nextId('a'), salaoId: s.id, clienteNome: body.clienteNome.trim(), clienteWhats: body.clienteWhats,
    clienteContato: session?.tipo === 'cliente' ? session.contato : null,
    itens: body.itens, data: body.data, hora: body.hora, duracao, valor, status: 'confirmado', avaliado: false,
  }
  db().agendamentos.push(ag)
  notificarEvento(ag, 'agendamento', 'Novo agendamento', `${ag.clienteNome} agendou ${textoItens(ag)} para ${formatDateBR(ag.data)} às ${ag.hora}.`)
  save()
  return { id: ag.id, salao: { nome: s.nome, endereco: s.endereco, whatsapp: s.whatsapp } }
})

/* =========================================================
   CLIENTE (acesso temporario)
   ========================================================= */
const textoItens = (a) =>
  a.itens.map((i) => (i.comboId ? db().combos.find((c) => c.id === i.comboId)?.nome : db().servicos.find((s) => s.id === i.servicoId)?.nome)).filter(Boolean).join(' + ')
const textoProfs = (a) =>
  [...new Set(a.itens.map((i) => nomeProf(db().profissionais.find((p) => p.id === i.profissionalId))))].join(', ')

const pertenceAoCliente = (a, sess) =>
  a.demo || (a.clienteContato && a.clienteContato === sess.contato) || (digits(sess.contato).length >= 10 && digits(a.clienteWhats) === digits(sess.contato))

function resumoCliente(a) {
  const s = db().saloes.find((x) => x.id === a.salaoId)
  const inicio = startOf(a.data, a.hora).getTime()
  const status = statusEfetivo(a)
  return {
    id: a.id, status, servicos: textoItens(a), profissionais: textoProfs(a), data: a.data, hora: a.hora,
    valor: a.valor, avaliado: a.avaliado,
    salao: { nome: s.nome, slug: s.slug, temaId: s.temaId, whatsapp: s.whatsapp },
    podeCancelar: status === 'confirmado' && Date.now() <= inicio - s.cancelamentoHoras * 3600000,
    cancelamentoHoras: s.cancelamentoHoras,
  }
}

route('GET', '/cliente/agendamentos', (ctx) => {
  const sess = requireCliente(ctx)
  return db().agendamentos.filter((a) => pertenceAoCliente(a, sess)).map(resumoCliente)
})
route('GET', '/cliente/agendamentos/:id', (ctx) => {
  const sess = requireCliente(ctx)
  const a = db().agendamentos.find((x) => x.id === ctx.params.id && pertenceAoCliente(x, sess))
  if (!a) throw new ApiError(404, 'Agendamento não encontrado.')
  return resumoCliente(a)
})
route('POST', '/cliente/agendamentos/:id/cancelar', (ctx) => {
  const sess = requireCliente(ctx)
  const a = db().agendamentos.find((x) => x.id === ctx.params.id && pertenceAoCliente(x, sess))
  if (!a) throw new ApiError(404, 'Agendamento não encontrado.')
  if (!resumoCliente(a).podeCancelar) {
    throw new ApiError(422, 'O prazo para cancelar já passou. Fale com o salão pelo WhatsApp.')
  }
  a.status = 'cancelado'
  notificarEvento(a, 'cancelamento', 'Agendamento cancelado', `${a.clienteNome} cancelou ${textoItens(a)} de ${formatDateBR(a.data)} às ${a.hora}.`)
  save()
  return null
})
route('POST', '/cliente/agendamentos/:id/avaliacao', (ctx) => {
  const sess = requireCliente(ctx)
  const a = db().agendamentos.find((x) => x.id === ctx.params.id && pertenceAoCliente(x, sess))
  if (!a) throw new ApiError(404, 'Agendamento não encontrado.')
  if (statusEfetivo(a) !== 'finalizado') throw new ApiError(422, 'Você só pode avaliar atendimentos já concluídos.')
  if (a.avaliado) throw new ApiError(409, 'Você já avaliou este atendimento.')
  const nota = Number(ctx.body.nota)
  if (!(nota >= 1 && nota <= 5)) throw new ApiError(422, 'Escolha uma nota de 1 a 5 tesouras.')
  db().avaliacoes.push({ id: nextId('v'), agendamentoId: a.id, salaoId: a.salaoId, nota, comentario: ctx.body.comentario || '' })
  a.avaliado = true
  save()
  return null
})

/* =========================================================
   PAINEL DO RESPONSAVEL
   ========================================================= */
function problemasPublicacao(s) {
  const servicos = db().servicos.filter((v) => v.salaoId === s.id)
  const profs = db().profissionais.filter((p) => p.salaoId === s.id && p.atende !== false)
  const out = []
  if (!servicos.length) out.push('Cadastre ao menos um serviço.')
  if (!profs.length) out.push('Cadastre ao menos um profissional que atenda clientes (pode ser você mesmo, em Profissionais).')
  servicos.filter((v) => !v.profissionalIds.length).forEach((v) => out.push(`O serviço "${v.nome}" não tem nenhum profissional vinculado.`))
  return out
}

route('GET', '/painel/portal', (ctx) => {
  const u = requireStaff(ctx)
  const s = salaoDoStaff(u)
  return {
    ...s,
    profissionais: db().profissionais.filter((p) => p.salaoId === s.id).map((p) => ({ id: p.id, nome: p.nome })),
  }
})
route('PUT', '/painel/portal', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  const b = ctx.body
  if (!String(b.nome || '').trim()) throw new ApiError(422, 'Informe o nome do salão.')
  Object.assign(s, {
    nome: b.nome.trim(), temaId: b.temaId, sobre: b.sobre ?? '', whatsapp: b.whatsapp ?? '', categoria: b.categoria,
    cidade: b.cidade ?? s.cidade, endereco: b.endereco ?? '', redes: b.redes ?? '', capa: b.capa ?? null,
    adminIds: b.adminIds?.length ? b.adminIds : s.adminIds, logo: b.logo ?? null, galeria: b.galeria ?? [],
  })
  save()
  return s
})
route('POST', '/painel/portal/validar-publicacao', (ctx) => {
  const problemas = problemasPublicacao(salaoDoStaff(requireAdmin(ctx)))
  return { ok: problemas.length === 0, problemas }
})
route('PUT', '/painel/portal/publicacao', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  if (ctx.body.publico) {
    const problemas = problemasPublicacao(s)
    if (problemas.length) throw new ApiError(422, 'O portal ainda não pode ficar público.', { problemas })
  }
  s.publico = !!ctx.body.publico
  save()
  return { publico: s.publico }
})

route('GET', '/painel/agendamentos', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  return db().agendamentos.filter((a) => a.salaoId === s.id)
    .map((a) => {
      const profs = [...new Set(a.itens.map((i) => i.profissionalId))].map((id) => db().profissionais.find((p) => p.id === id)).filter(Boolean)
      return {
        id: a.id, cliente: a.clienteNome, whatsapp: a.clienteWhats, servicos: textoItens(a), profissionais: textoProfs(a), profissionalIds: profs.map((p) => p.id),
        data: a.data, hora: a.hora, valor: a.valor, status: statusEfetivo(a),
        encaminhar: profs.filter((p) => p.encaminhar && p.whatsapp).map((p) => ({ nome: nomeProf(p), whatsapp: p.whatsapp })),
      }
    })
    .sort((x, y) => (x.data + x.hora).localeCompare(y.data + y.hora))
})

const servicoOut = (v) => v
route('GET', '/painel/servicos', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  return {
    servicos: db().servicos.filter((v) => v.salaoId === s.id).map(servicoOut),
    combos: db().combos.filter((c) => c.salaoId === s.id),
    profissionais: db().profissionais.filter((p) => p.salaoId === s.id && p.atende !== false).map((p) => ({ id: p.id, nome: p.nome })),
  }
})

function validarServico(b) {
  if (!String(b.nome || '').trim()) throw new ApiError(422, 'Informe o nome do serviço.')
  if (!(Number(b.preco) >= 0) || b.preco === '' || b.preco === null) throw new ApiError(422, 'Informe um valor válido.')
  if (!(Number(b.duracao) >= 5)) throw new ApiError(422, 'O tempo médio precisa ser de pelo menos 5 minutos.')
}
function vincularServico(servicoId, profIds) {
  db().profissionais.forEach((p) => {
    const tem = p.servicoIds.includes(servicoId)
    const deve = profIds.includes(p.id)
    if (deve && !tem) p.servicoIds.push(servicoId)
    if (!deve && tem) p.servicoIds = p.servicoIds.filter((id) => id !== servicoId)
  })
}
route('POST', '/painel/servicos', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  validarServico(ctx.body)
  const v = { id: nextId('sv'), salaoId: s.id, nome: ctx.body.nome.trim(), preco: Number(ctx.body.preco), duracao: Number(ctx.body.duracao), profissionalIds: ctx.body.profissionalIds || [], destaque: true }
  db().servicos.push(v)
  vincularServico(v.id, v.profissionalIds)
  save()
  return v
})
route('PUT', '/painel/servicos/:id', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  const v = db().servicos.find((x) => x.id === ctx.params.id && x.salaoId === s.id)
  if (!v) throw new ApiError(404, 'Serviço não encontrado.')
  validarServico(ctx.body)
  Object.assign(v, { nome: ctx.body.nome.trim(), preco: Number(ctx.body.preco), duracao: Number(ctx.body.duracao), profissionalIds: ctx.body.profissionalIds || [] })
  vincularServico(v.id, v.profissionalIds)
  save()
  return v
})
route('DELETE', '/painel/servicos/:id', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  db().servicos = db().servicos.filter((x) => !(x.id === ctx.params.id && x.salaoId === s.id))
  db().combos.forEach((c) => { c.servicoIds = c.servicoIds.filter((id) => id !== ctx.params.id) })
  db().combos = db().combos.filter((c) => c.servicoIds.length)
  db().profissionais.forEach((p) => { p.servicoIds = p.servicoIds.filter((id) => id !== ctx.params.id) })
  save()
  return null
})

function validarCombo(b, salaoId) {
  if (!String(b.nome || '').trim()) throw new ApiError(422, 'Informe o nome do combo.')
  if ((b.servicoIds || []).length < 2) throw new ApiError(422, 'Um combo precisa de pelo menos dois serviços.')
  if (!(Number(b.preco) > 0)) throw new ApiError(422, 'Informe o valor final do combo.')
  const soma = db().servicos.filter((v) => b.servicoIds.includes(v.id) && v.salaoId === salaoId).reduce((t, v) => t + v.preco, 0)
  return soma
}
route('POST', '/painel/combos', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  validarCombo(ctx.body, s.id)
  const c = { id: nextId('cb'), salaoId: s.id, nome: ctx.body.nome.trim(), servicoIds: ctx.body.servicoIds, preco: Number(ctx.body.preco), profissionalIds: ctx.body.profissionalIds || [] }
  db().combos.push(c)
  save()
  return c
})
route('PUT', '/painel/combos/:id', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  const c = db().combos.find((x) => x.id === ctx.params.id && x.salaoId === s.id)
  if (!c) throw new ApiError(404, 'Combo não encontrado.')
  validarCombo(ctx.body, s.id)
  Object.assign(c, { nome: ctx.body.nome.trim(), servicoIds: ctx.body.servicoIds, preco: Number(ctx.body.preco), profissionalIds: ctx.body.profissionalIds || [] })
  save()
  return c
})
route('DELETE', '/painel/combos/:id', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  db().combos = db().combos.filter((x) => !(x.id === ctx.params.id && x.salaoId === s.id))
  save()
  return null
})

route('GET', '/painel/profissionais', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  return {
    profissionais: db().profissionais.filter((p) => p.salaoId === s.id).map((p) => ({ ...p, atende: p.atende !== false, ehAdmin: s.adminIds.includes(p.id) })),
    servicos: db().servicos.filter((v) => v.salaoId === s.id).map((v) => ({ id: v.id, nome: v.nome })),
  }
})
function validarProf(b) {
  if (!String(b.nome || '').trim()) throw new ApiError(422, 'Informe o nome completo.')
  if (b.encaminhar && digits(b.whatsapp).length < 10) throw new ApiError(422, 'Para ativar o encaminhamento, informe o WhatsApp do profissional.')
}
function vincularProf(profId, servicoIds) {
  db().servicos.forEach((v) => {
    const tem = v.profissionalIds.includes(profId)
    const deve = servicoIds.includes(v.id)
    if (deve && !tem) v.profissionalIds.push(profId)
    if (!deve && tem) v.profissionalIds = v.profissionalIds.filter((id) => id !== profId)
  })
}
route('POST', '/painel/profissionais', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  validarProf(ctx.body)
  const b = ctx.body
  const p = { id: nextId('p'), salaoId: s.id, nome: b.nome.trim(), nomeSocial: (b.nomeSocial || '').trim(), whatsapp: b.whatsapp || '', encaminhar: !!b.encaminhar, servicoIds: b.atende === false ? [] : (b.servicoIds || []), atende: b.atende !== false, horarios: semana([1, 2, 3, 4, 5], '09:00', '18:00') }
  db().profissionais.push(p)
  vincularProf(p.id, p.servicoIds)
  save()
  return { profissional: p, convite: Math.random().toString(36).slice(2, 8).toUpperCase() }
})
route('PUT', '/painel/profissionais/:id', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  const p = db().profissionais.find((x) => x.id === ctx.params.id && x.salaoId === s.id)
  if (!p) throw new ApiError(404, 'Profissional não encontrado.')
  validarProf(ctx.body)
  const b = ctx.body
  Object.assign(p, { nome: b.nome.trim(), nomeSocial: (b.nomeSocial || '').trim(), whatsapp: b.whatsapp || '', encaminhar: !!b.encaminhar, servicoIds: b.atende === false ? [] : (b.servicoIds || []), atende: b.atende !== false })
  vincularProf(p.id, p.servicoIds)
  // quem deixa de atender sai dos servicos e dos combos (continua na equipe)
  if (!p.atende) db().combos.forEach((c) => { c.profissionalIds = c.profissionalIds.filter((id) => id !== p.id) })
  save()
  return { profissional: p, convite: null }
})
route('DELETE', '/painel/profissionais/:id', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  if (s.adminIds.includes(ctx.params.id) && s.adminIds.length === 1) {
    throw new ApiError(422, 'Este profissional é o administrador do salão. Transfira o cargo antes de remover.')
  }
  db().profissionais = db().profissionais.filter((x) => !(x.id === ctx.params.id && x.salaoId === s.id))
  db().servicos.forEach((v) => { v.profissionalIds = v.profissionalIds.filter((id) => id !== ctx.params.id) })
  save()
  return null
})

route('GET', '/painel/bloqueios', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  const mes = String(ctx.query.mes || '')
  return db().bloqueios.filter((b) => b.salaoId === s.id && b.data.startsWith(mes))
})
route('PUT', '/painel/bloqueios/:data', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  const atual = bloqueioDoDia(s.id, ctx.params.data)
  const motivo = String(ctx.body.motivo || '').trim() || 'Sem motivo informado'
  if (atual) atual.motivo = motivo
  else db().bloqueios.push({ id: nextId('b'), salaoId: s.id, data: ctx.params.data, motivo })
  save()
  return null
})
route('DELETE', '/painel/bloqueios/:data', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  db().bloqueios = db().bloqueios.filter((b) => !(b.salaoId === s.id && b.data === ctx.params.data))
  save()
  return null
})

route('PUT', '/painel/configuracoes', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  const h = Number(ctx.body.cancelamentoHoras)
  if (!(h >= 0 && h <= 72)) throw new ApiError(422, 'Informe as horas de antecedência entre 0 e 72.')
  Object.assign(s, { atendimento: ctx.body.atendimento, cancelamentoHoras: h, funcionariosEditamHorario: !!ctx.body.funcionariosEditamHorario })
  save()
  return null
})
route('POST', '/painel/configuracoes/transferir-admin', (ctx) => {
  const u = requireAdmin(ctx)
  const s = salaoDoStaff(u)
  const alvo = db().profissionais.find((p) => p.id === ctx.body.profissionalId && p.salaoId === s.id)
  if (!alvo) throw new ApiError(404, 'Profissional não encontrado.')
  s.adminIds = [alvo.id]
  u.papel = 'funcionario'
  const dest = db().usuarios.find((x) => x.profissionalId === alvo.id)
  if (dest) dest.papel = 'administrador'
  save()
  return null
})
route('POST', '/painel/senha', (ctx) => {
  const u = requireStaff(ctx)
  if (ctx.body.atual !== u.senha) throw new ApiError(422, 'A senha atual está incorreta.')
  if (!ctx.body.nova || ctx.body.nova.length < 8) throw new ApiError(422, 'A nova senha precisa ter ao menos 8 caracteres.')
  if (!u.apelidos?.length) { u.senha = ctx.body.nova; save() } // contas de teste mantem a senha
  return null
})
route('DELETE', '/painel/dados', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  db().agendamentos = db().agendamentos.filter((a) => a.salaoId !== s.id)
  db().servicos = db().servicos.filter((v) => v.salaoId !== s.id)
  db().combos = db().combos.filter((c) => c.salaoId !== s.id)
  db().bloqueios = db().bloqueios.filter((b) => b.salaoId !== s.id)
  s.publico = false
  save()
  return null
})
route('DELETE', '/painel/conta', (ctx) => {
  const u = requireAdmin(ctx)
  const s = salaoDoStaff(u)
  const id = s.id
  db().saloes = db().saloes.filter((x) => x.id !== id)
  ;['servicos', 'combos', 'profissionais', 'agendamentos', 'bloqueios'].forEach((k) => { db()[k] = db()[k].filter((x) => x.salaoId !== id) })
  db().usuarios = db().usuarios.filter((x) => x.salaoId !== id)
  save()
  return null
})

/* =========================================================
   NOTIFICACOES (responsavel e funcionario)
   ========================================================= */
const minhasNotificacoes = (u) => db().notificacoes.filter((n) => n.salaoId === u.salaoId
  && (u.papel === 'administrador' ? n.para === 'administrador' : n.para === 'funcionario' && n.profissionalId === u.profissionalId))

function detalhesDa(n) {
  const a = n.agendamentoId && db().agendamentos.find((x) => x.id === n.agendamentoId)
  if (!a) return null
  return { cliente: a.clienteNome, servicos: textoItens(a), profissionais: textoProfs(a), data: a.data, hora: a.hora }
}
/** Quem pode fazer o que: so o responsavel envia algo, e cada tipo tem a sua acao. O funcionario apenas le. */
function acoesDa(n, u) {
  if (u.papel !== 'administrador') return []
  if (n.tipo === 'avaliacao') return [{ id: 'avaliacao', rotulo: 'Pedir avaliação ao cliente', usada: !!n.avaliacaoEnviada }]
  if (n.tipo === 'aviso') return []
  return [{ id: 'aviso', rotulo: 'Enviar aviso à equipe' }]
}

route('GET', '/notificacoes', (ctx) => {
  const u = requireStaff(ctx)
  return minhasNotificacoes(u)
    .sort((a, b) => b.criadaEm.localeCompare(a.criadaEm))
    .map((n) => ({
      id: n.id, tipo: n.tipo, titulo: n.titulo, texto: n.texto, criadaEm: n.criadaEm, lida: !!n.lida,
      remetente: n.remetente || null, detalhes: detalhesDa(n), acoes: acoesDa(n, u),
    }))
})
route('POST', '/notificacoes/:id/lida', (ctx) => {
  const u = requireStaff(ctx)
  const n = minhasNotificacoes(u).find((x) => x.id === ctx.params.id)
  if (!n) throw new ApiError(404, 'Notificação não encontrada.')
  n.lida = true
  save()
  return null
})
route('POST', '/notificacoes/:id/acao', (ctx) => {
  const u = requireAdmin(ctx)
  const n = minhasNotificacoes(u).find((x) => x.id === ctx.params.id)
  if (!n) throw new ApiError(404, 'Notificação não encontrada.')
  const { acao, mensagem, profissionalIds } = ctx.body
  if (!acoesDa(n, u).some((a) => a.id === acao)) throw new ApiError(422, 'Esta ação não está disponível para esta notificação.')
  if (acao === 'avaliacao') {
    n.avaliacaoEnviada = true
    save()
    return { enviados: 1 }
  }
  const texto = String(mensagem || '').trim()
  if (!texto) throw new ApiError(422, 'Escreva a mensagem do aviso.')
  const alvos = (profissionalIds || []).filter((id) => id !== u.profissionalId && db().profissionais.some((p) => p.id === id && p.salaoId === u.salaoId))
  if (!alvos.length) throw new ApiError(422, 'Escolha ao menos um profissional da equipe para receber o aviso.')
  alvos.forEach((profissionalId) => db().notificacoes.push({
    id: nextId('n'), salaoId: u.salaoId, para: 'funcionario', profissionalId, tipo: 'aviso', titulo: 'Aviso do responsável',
    texto, remetente: u.nome, criadaEm: new Date().toISOString(), lida: false, agendamentoId: n.agendamentoId,
  }))
  save()
  return { enviados: alvos.length }
})

/* =========================================================
   FUNCIONARIO: minha agenda
   ========================================================= */
route('GET', '/agenda', (ctx) => {
  const u = requireStaff(ctx)
  const s = salaoDoStaff(u)
  const p = db().profissionais.find((x) => x.id === u.profissionalId)
  if (!p) throw new ApiError(404, 'Profissional não encontrado.')
  const mes = String(ctx.query.mes || monthKey(new Date().getFullYear(), new Date().getMonth()))
  const meus = db().agendamentos.filter((a) => a.salaoId === s.id && a.data.startsWith(mes) && statusEfetivo(a) !== 'cancelado' && a.itens.some((i) => i.profissionalId === p.id))
  return {
    profissional: { id: p.id, nome: nomeProf(p) || p.nome, horarios: p.horarios },
    podeEditar: !!s.funcionariosEditamHorario,
    agendamentos: meus.map((a) => ({
      id: a.id, data: a.data, hora: a.hora, duracao: a.duracao, cliente: a.clienteNome, servicos: textoItens(a),
      colegas: [...new Set(a.itens.map((i) => i.profissionalId))].filter((id) => id !== p.id).map((id) => nomeProf(db().profissionais.find((x) => x.id === id))),
    })),
    descansos: db().descansos.filter((d) => d.profissionalId === p.id && d.data.startsWith(mes)).map(({ data, hora }) => ({ data, hora })),
    bloqueios: db().bloqueios.filter((b) => b.salaoId === s.id && b.data.startsWith(mes)).map(({ data, motivo }) => ({ data, motivo })),
    folgas: db().folgas.filter((f) => f.profissionalId === p.id && f.data.startsWith(mes)).map(({ data, motivo }) => ({ data, motivo })),
    salaoHorarios: s.horarios,
  }
})
/** Agendamentos do profissional logado (todos os periodos), do mais recente para o mais antigo no front. */
const meusAgendamentos = (u) => db().agendamentos.filter((a) => a.salaoId === u.salaoId && a.itens.some((i) => i.profissionalId === u.profissionalId))

route('GET', '/agenda/resumo', (ctx) => {
  const u = requireStaff(ctx)
  const s = salaoDoStaff(u)
  const p = db().profissionais.find((x) => x.id === u.profissionalId)
  if (!p) throw new ApiError(404, 'Profissional não encontrado.')
  const meus = meusAgendamentos(u)
  const hoje = todayISO()
  const em7 = addDaysISO(hoje, 7)
  const ativos = meus.filter((a) => statusEfetivo(a) !== 'cancelado')
  const futuros = ativos.filter((a) => statusEfetivo(a) === 'confirmado').sort((a, b) => (a.data + a.hora).localeCompare(b.data + b.hora))
  const prox = futuros[0]
  return {
    profissional: {
      id: p.id, nome: p.nome, nomeSocial: p.nomeSocial, whatsapp: p.whatsapp, horarios: p.horarios,
      servicos: db().servicos.filter((v) => p.servicoIds.includes(v.id)).map((v) => v.nome),
    },
    salao: { nome: s.nome, temaId: s.temaId },
    indicadores: {
      hoje: ativos.filter((a) => a.data === hoje).length,
      proximos7: futuros.filter((a) => a.data <= em7).length,
      concluidosMes: meus.filter((a) => statusEfetivo(a) === 'finalizado' && a.data.startsWith(hoje.slice(0, 7))).length,
      proximo: prox ? { data: prox.data, hora: prox.hora, cliente: prox.clienteNome, servicos: textoItens(prox) } : null,
    },
  }
})
route('GET', '/agenda/agendamentos', (ctx) => {
  const u = requireStaff(ctx)
  return meusAgendamentos(u).map((a) => ({
    id: a.id, data: a.data, hora: a.hora, duracao: a.duracao, cliente: a.clienteNome, servicos: textoItens(a), status: statusEfetivo(a),
    colegas: [...new Set(a.itens.map((i) => i.profissionalId))].filter((id) => id !== u.profissionalId).map((id) => nomeProf(db().profissionais.find((x) => x.id === id))),
  }))
})
route('PUT', '/agenda/descansos', (ctx) => {
  const u = requireStaff(ctx)
  const s = salaoDoStaff(u)
  if (!s.funcionariosEditamHorario) throw new ApiError(403, 'O responsável não liberou a edição de horários.')
  const { data, hora, descanso } = ctx.body
  db().descansos = db().descansos.filter((d) => !(d.profissionalId === u.profissionalId && d.data === data && d.hora === hora))
  if (descanso) db().descansos.push({ profissionalId: u.profissionalId, data, hora })
  save()
  return null
})
route('POST', '/agenda/alertas', (ctx) => {
  const u = requireStaff(ctx)
  if (!ctx.body.motivo) throw new ApiError(422, 'Escolha o motivo do alerta.')
  db().alertas.push({ id: nextId('al'), salaoId: u.salaoId, agendamentoId: ctx.body.agendamentoId, motivo: ctx.body.motivo, descricao: ctx.body.descricao || '' })
  const ag = db().agendamentos.find((a) => a.id === ctx.body.agendamentoId)
  db().notificacoes.push({
    id: nextId('n'), salaoId: u.salaoId, para: 'administrador', tipo: 'alerta', titulo: 'Alerta de funcionário',
    texto: `${u.nome} sinalizou ${ag ? `o cliente ${ag.clienteNome}` : 'um cliente'}: ${ctx.body.motivo}.${ctx.body.descricao ? ` ${ctx.body.descricao}` : ''}`,
    remetente: u.nome, criadaEm: new Date().toISOString(), lida: false, agendamentoId: ctx.body.agendamentoId,
  })
  save()
  return null
})

/* =========================================================
   HORARIOS (responsavel): funcionamento do salao, horario de cada profissional e folgas
   ========================================================= */
const HORA = /^([01]\d|2[0-3]):[0-5]\d$/
const NOMES_DIA = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado']

function validarSemana(h, { exigirUmDia }) {
  if (!h || typeof h !== 'object') throw new ApiError(422, 'Horários inválidos.')
  const out = {}
  for (let d = 0; d < 7; d++) {
    const x = h[d]
    if (!x) throw new ApiError(422, 'Horários incompletos: envie os 7 dias da semana.')
    if (x.ativo && (!HORA.test(x.inicio) || !HORA.test(x.fim))) throw new ApiError(422, `Informe um horário válido na ${NOMES_DIA[d]}.`)
    if (x.ativo && x.inicio >= x.fim) throw new ApiError(422, `Na ${NOMES_DIA[d]}, o término precisa ser depois do início.`)
    out[d] = { ativo: !!x.ativo, inicio: x.inicio || '09:00', fim: x.fim || '18:00' }
  }
  if (exigirUmDia && !Object.values(out).some((x) => x.ativo)) throw new ApiError(422, 'O salão precisa abrir em pelo menos um dia da semana.')
  return out
}

route('GET', '/painel/horarios', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  return {
    salao: { horarios: s.horarios },
    profissionais: db().profissionais.filter((p) => p.salaoId === s.id && p.atende !== false)
      .map((p) => ({ id: p.id, nome: p.nome, ehAdmin: s.adminIds.includes(p.id), horarios: p.horarios })),
    avisos: avisosDeConflito(s.id),
  }
})
route('PUT', '/painel/horarios/salao', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  s.horarios = validarSemana(ctx.body.horarios, { exigirUmDia: true })
  save()
  return { avisos: avisosDeConflito(s.id) }
})
route('PUT', '/painel/horarios/profissionais/:id', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  const p = db().profissionais.find((x) => x.id === ctx.params.id && x.salaoId === s.id)
  if (!p) throw new ApiError(404, 'Profissional não encontrado.')
  p.horarios = validarSemana(ctx.body.horarios, { exigirUmDia: false })
  save()
  return { avisos: avisosDeConflito(s.id) }
})
route('GET', '/painel/folgas', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  const { mes = '', profissionalId } = ctx.query
  return db().folgas.filter((f) => f.salaoId === s.id && f.data.startsWith(mes) && (!profissionalId || f.profissionalId === profissionalId))
    .map(({ profissionalId: pid, data, motivo }) => ({ profissionalId: pid, data, motivo }))
})
route('PUT', '/painel/folgas/:profissionalId/:data', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  if (!db().profissionais.some((p) => p.id === ctx.params.profissionalId && p.salaoId === s.id)) throw new ApiError(404, 'Profissional não encontrado.')
  const motivo = String(ctx.body.motivo || '').trim() || 'Folga'
  const atual = db().folgas.find((f) => f.profissionalId === ctx.params.profissionalId && f.data === ctx.params.data)
  if (atual) atual.motivo = motivo
  else db().folgas.push({ id: nextId('f'), salaoId: s.id, profissionalId: ctx.params.profissionalId, data: ctx.params.data, motivo })
  save()
  return null
})
route('DELETE', '/painel/folgas/:profissionalId/:data', (ctx) => {
  const s = salaoDoStaff(requireAdmin(ctx))
  db().folgas = db().folgas.filter((f) => !(f.salaoId === s.id && f.profissionalId === ctx.params.profissionalId && f.data === ctx.params.data))
  save()
  return null
})

/**
 * SOMENTE TESTE (existe so na API simulada, nao esta no openapi.yaml):
 * atalho da tela do funcionario para ligar/desligar a permissao de editar horarios.
 * No sistema real esta permissao e do administrador, em Configuracoes.
 */
if (__MODO_TESTE__) {
  route('PUT', '/teste/edicao-horarios', (ctx) => {
    const u = requireStaff(ctx)
    salaoDoStaff(u).funcionariosEditamHorario = !!ctx.body.permitido
    save()
    return null
  })
}

/* utilitario de desenvolvimento: window.__resetMock() */
if (typeof window !== 'undefined') {
  window.__resetMock = () => { resetMock(); window.location.reload() }
}
