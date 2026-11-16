// Povoa o banco com dados de demonstração usando só a API (nada de SQL direto: e-mail e telefone são cifrados,
// o login usa o hash do e-mail e a senha é BCrypt, então tudo tem que passar pelas regras do backend).
//
// Como usar (com o banco e a API rodando):
//   node database/povoar-pela-api.mjs
//   API_URL=http://localhost:8080/api node database/povoar-pela-api.mjs   (para apontar outra API)
//
// Pode rodar de novo: o que já existe é pulado (a conta do dono já cadastrada = salão já povoado).
// As datas são calculadas a partir de hoje, então os agendamentos sempre caem nos próximos dias úteis.
// Todas as contas usam a senha 12345678 (só para demonstração).

const API = process.env.API_URL || 'http://localhost:8080/api'
const SENHA = '12345678'

// ---------- chamadas à API ----------

class ErroDaApi extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

async function api(metodo, caminho, { token, corpo } = {}) {
  const resposta = await fetch(API + caminho, {
    method: metodo,
    headers: {
      'Content-Type': 'application/json; charset=UTF-8',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: corpo === undefined ? undefined : JSON.stringify(corpo),
  })
  const texto = await resposta.text()
  const dados = texto ? JSON.parse(texto) : null
  if (!resposta.ok) {
    const detalhe = dados?.problemas?.length ? ` (${dados.problemas.join('; ')})` : ''
    throw new ErroDaApi(resposta.status, `${metodo} ${caminho} -> ${resposta.status}: ${dados?.message}${detalhe}`)
  }
  return dados
}

const entrar = (email) => api('POST', '/auth/login', { corpo: { email, senha: SENHA } })

/** Cadastra o cliente; se o e-mail já existe, só entra. */
async function cliente(nome, email, telefone) {
  try {
    return await api('POST', '/auth/cadastro/cliente', { corpo: { nome, email, telefone, senha: SENHA, aceitouTermos: true } })
  } catch (e) {
    if (e.status === 409) return entrar(email)
    throw e
  }
}

// ---------- datas ----------

const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const hoje = new Date()
hoje.setHours(0, 0, 0, 0)

/** Próximos dias úteis (segunda a sexta) a partir de amanhã, pulando as datas da lista. */
function diasUteis(quantos, pular = []) {
  const dias = []
  const d = new Date(hoje)
  while (dias.length < quantos) {
    d.setDate(d.getDate() + 1)
    const semana = d.getDay()
    if (semana !== 0 && semana !== 6 && !pular.includes(iso(d))) dias.push(iso(d))
  }
  return dias
}

// feriado de 20/11 (Consciência Negra) se ainda não passou neste ano
const feriado = `${hoje.getFullYear()}-11-20`
const temFeriado = feriado > iso(hoje)

// semana no formato do front: "0" = domingo ... "6" = sábado
function semana(dias, inicio, fim, sabado) {
  const s = {}
  for (let d = 0; d < 7; d++) s[d] = { ativo: dias.includes(d), inicio, fim }
  if (sabado) s[6] = { ativo: true, inicio: sabado[0], fim: sabado[1] }
  return { horarios: s }
}

// ---------- montar um salão ----------

/**
 * Cria o dono e o salão completo. Se a conta do dono já existe, o salão já foi povoado: devolve null.
 * servicos: [{ nome, preco, duracao, descricao, quem: [chaves dos profissionais] }]
 */
async function salao(cfg) {
  let sessao
  try {
    sessao = await api('POST', '/auth/cadastro', { corpo: { ...cfg.dono, senha: SENHA, aceitouTermos: true } })
  } catch (e) {
    if (e.status === 409) {
      console.log(`- ${cfg.portal.nome}: já existe, pulando`)
      return null
    }
    throw e
  }
  const token = sessao.token
  const ids = { dono: sessao.usuario.profissionalId }

  // equipe (o dono já nasce como profissional administrador)
  const convites = {}
  for (const [chave, p] of Object.entries(cfg.equipe || {})) {
    const r = await api('POST', '/painel/profissionais', { token, corpo: { ...p, servicoIds: [] } })
    ids[chave] = r.profissional.id
    convites[chave] = r.convite
  }

  // serviços e combos, já com quem faz cada um
  const servicos = {}
  for (const s of cfg.servicos) {
    const r = await api('POST', '/painel/servicos', {
      token, corpo: { nome: s.nome, descricao: s.descricao, preco: s.preco, duracao: s.duracao, profissionalIds: s.quem.map((k) => ids[k]) },
    })
    servicos[s.nome] = { id: r.id, duracao: s.duracao }
  }
  const combos = {}
  for (const c of cfg.combos || []) {
    const r = await api('POST', '/painel/combos', {
      token, corpo: { nome: c.nome, servicoIds: c.servicos.map((n) => servicos[n].id), preco: c.preco, profissionalIds: c.quem.map((k) => ids[k]) },
    })
    combos[c.nome] = { id: r.id, duracao: c.servicos.reduce((t, n) => t + servicos[n].duracao, 0) }
  }

  // portal, horários, configurações e publicação
  await api('PUT', '/painel/portal', { token, corpo: { ...cfg.portal, logo: null, capa: null, galeria: null, adminIds: null } })
  await api('PUT', '/painel/horarios/salao', { token, corpo: cfg.horarioSalao })
  for (const [chave, h] of Object.entries(cfg.horarioProfissionais || {})) {
    await api('PUT', `/painel/horarios/profissionais/${ids[chave]}`, { token, corpo: h })
  }
  if (temFeriado) await api('PUT', `/painel/bloqueios/${feriado}`, { token, corpo: { motivo: 'Consciência Negra' } })
  await api('PUT', '/painel/configuracoes', { token, corpo: cfg.configuracoes })
  await api('PUT', '/painel/portal/publicacao', { token, corpo: { publico: true } })
  const portal = await api('GET', '/painel/portal', { token })

  console.log(`- ${cfg.portal.nome}: criado e publicado em /${portal.slug}`)
  return { token, ids, convites, servicos, combos, slug: portal.slug, email: cfg.dono.email }
}

/** Pede um horário: tenta a hora preferida; se não estiver livre, pega a primeira livre do dia. */
async function agendar(s, clienteSessao, { item, quem, data, hora }) {
  const ref = s.servicos[item] ? { servicoId: s.servicos[item].id } : { comboId: s.combos[item].id }
  const duracao = (s.servicos[item] || s.combos[item]).duracao
  const livres = (await api('GET', `/saloes/${s.slug}/disponibilidade/horarios?data=${data}&profissionais=${s.ids[quem]}&duracao=${duracao}`))
    .filter((h) => h.livre).map((h) => h.hora)
  const escolhida = livres.includes(hora) ? hora : livres[0]
  if (!escolhida) throw new Error(`sem horário livre em ${data} para ${item}`)
  const r = await api('POST', `/saloes/${s.slug}/agendamentos`, {
    token: clienteSessao.token,
    corpo: {
      itens: [{ ...ref, profissionalId: s.ids[quem] }], data, hora: escolhida,
      clienteNome: clienteSessao.usuario.nome, clienteWhats: '(62) 98888-7777',
    },
  })
  return r.id
}

// ---------- dados da demonstração ----------

async function main() {
  console.log(`Povoando ${API} ...`)
  const [d1, d2, d3, d4, d5, d6] = diasUteis(6, temFeriado ? [feriado] : [])

  // salão com equipe: o dono só gerencia; Rafael e Diego entram pelo convite; Lucas ainda não usou o convite
  const barbearia = await salao({
    dono: { nome: 'Gabriel Naoki', razaoSocial: 'Barbearia Gabriel Naoki', documento: '11222333000181', email: 'gabriel.naoki@teste.com', atuacao: 'equipe' },
    equipe: {
      rafael: { nome: 'Rafael Lima', nomeSocial: 'Rafael', whatsapp: '(62) 99101-0001', encaminhar: true, atende: true },
      diego: { nome: 'Diego Santos', nomeSocial: 'Diego', whatsapp: '(62) 99101-0002', encaminhar: false, atende: true },
      lucas: { nome: 'Lucas Pereira', nomeSocial: 'Lucas', whatsapp: '(62) 99101-0003', encaminhar: false, atende: true },
    },
    servicos: [
      { nome: 'Corte masculino', preco: 45, duracao: 30, descricao: 'Corte na tesoura ou máquina, com lavagem.', quem: ['rafael', 'diego', 'lucas'] },
      { nome: 'Barba', preco: 35, duracao: 30, descricao: 'Barba com toalha quente e navalha.', quem: ['rafael', 'diego'] },
      { nome: 'Sobrancelha', preco: 20, duracao: 15, descricao: 'Design e limpeza da sobrancelha.', quem: ['lucas'] },
      { nome: 'Pigmentação', preco: 60, duracao: 45, descricao: 'Pigmentação de barba ou cabelo.', quem: ['diego'] },
      { nome: 'Hidratação', preco: 40, duracao: 30, descricao: 'Hidratação capilar com massagem.', quem: ['rafael', 'lucas'] },
    ],
    combos: [{ nome: 'Corte + Barba', servicos: ['Corte masculino', 'Barba'], preco: 70, quem: ['rafael', 'diego'] }],
    portal: {
      nome: 'Barbearia Gabriel Naoki', temaId: 'azul-escuro', categoria: 'barbearia', cidade: 'Goiânia, GO',
      endereco: 'Av. T-63, 1200, Setor Bueno', whatsapp: '(62) 99100-0000', redes: 'instagram.com/barbeariagabrielnaoki',
      sobre: 'Barbearia tradicional com atendimento por agendamento. Corte, barba e cuidados masculinos com hora marcada.',
    },
    horarioSalao: semana([1, 2, 3, 4, 5], '09:00', '19:00', ['09:00', '14:00']),
    horarioProfissionais: { rafael: semana([1, 2, 3, 4, 5], '09:00', '18:00', ['09:00', '14:00']) },
    configuracoes: { atendimento: 'local', cancelamentoHoras: 4, funcionariosEditamHorario: true },
  })

  // salão de profissional autônomo (a dona também atende)
  const studio = await salao({
    dono: { nome: 'Juliana Matos', razaoSocial: 'Studio Juliana Matos', documento: '12345678909', email: 'juliana.matos@teste.com', atuacao: 'autonomo' },
    servicos: [
      { nome: 'Escova', preco: 60, duracao: 45, descricao: 'Escova modeladora.', quem: ['dono'] },
      { nome: 'Corte feminino', preco: 80, duracao: 60, descricao: 'Corte com lavagem e finalização.', quem: ['dono'] },
      { nome: 'Manicure', preco: 35, duracao: 45, descricao: 'Cutilagem e esmaltação.', quem: ['dono'] },
    ],
    combos: [{ nome: 'Corte + Escova', servicos: ['Corte feminino', 'Escova'], preco: 125, quem: ['dono'] }],
    portal: {
      nome: 'Studio Juliana Matos', temaId: 'violeta-pastel', categoria: 'salão de beleza', cidade: 'Goiânia, GO',
      endereco: 'Rua 9, 450, Setor Marista', whatsapp: '(62) 99200-0000', redes: 'instagram.com/studiojulianamatos',
      sobre: 'Studio de beleza com atendimento individual: corte, escova e unhas.',
    },
    horarioSalao: semana([1, 2, 3, 4, 5], '09:00', '19:00', ['09:00', '15:00']),
    configuracoes: { atendimento: 'local', cancelamentoHoras: 6, funcionariosEditamHorario: false },
  })

  // clientes (o cadastro devolve a sessão; se já existir, só entra)
  const gabriel = await cliente('Gabriel Naoki', 'gabriel.naoki.cliente@teste.com', '(62) 98100-0001')
  const ana = await cliente('Ana Souza', 'ana.souza@teste.com', '(62) 98100-0002')
  const bruno = await cliente('Bruno Costa', 'bruno.costa@teste.com', '(62) 98100-0003')
  const carla = await cliente('Carla Dias', 'carla.dias@teste.com', '(62) 98100-0004')

  if (barbearia) {
    const t = barbearia.token
    // funcionários criam a conta com o código de convite
    for (const [chave, email] of [['rafael', 'rafael.lima@teste.com'], ['diego', 'diego.santos@teste.com']]) {
      await api('POST', '/auth/convite', { corpo: { codigo: barbearia.convites[chave], email, senha: SENHA, aceitouTermos: true } })
    }
    // folga do Diego e descanso do Rafael
    await api('PUT', `/painel/folgas/${barbearia.ids.diego}/${d5}`, { token: t, corpo: { motivo: 'Consulta médica' } })

    // agendamentos em vários status
    const a1 = await agendar(barbearia, gabriel, { item: 'Corte + Barba', quem: 'rafael', data: d1, hora: '10:00' })
    const a2 = await agendar(barbearia, ana, { item: 'Corte masculino', quem: 'diego', data: d1, hora: '14:00' })
    await agendar(barbearia, bruno, { item: 'Pigmentação', quem: 'diego', data: d2, hora: '10:00' })
    await agendar(barbearia, carla, { item: 'Sobrancelha', quem: 'lucas', data: d2, hora: '15:00' })
    const a5 = await agendar(barbearia, gabriel, { item: 'Barba', quem: 'rafael', data: d3, hora: '11:00' })
    const a6 = await agendar(barbearia, ana, { item: 'Corte masculino', quem: 'rafael', data: d3, hora: '16:00' })
    const a7 = await agendar(barbearia, bruno, { item: 'Corte masculino', quem: 'rafael', data: d4, hora: '09:00' })
    for (const id of [a1, a2, a6, a7]) await api('POST', `/painel/agendamentos/${id}/aprovar`, { token: t })
    await api('POST', `/painel/agendamentos/${a5}/recusar`, { token: t })
    await api('POST', `/cliente/agendamentos/${a6}/cancelar`, { token: ana.token })

    // o dono repassa um aviso à equipe a partir do pedido de pigmentação
    const notificacoes = await api('GET', '/notificacoes', { token: t })
    const pedido = notificacoes.find((n) => n.tipo === 'agendamento' && n.detalhes?.servicos === 'Pigmentação')
    if (pedido) {
      await api('POST', `/notificacoes/${pedido.id}/acao`, {
        token: t, corpo: { acao: 'aviso', mensagem: 'O Bruno pediu para chegar 10 minutos antes. Deixem a cadeira pronta.', profissionalIds: [barbearia.ids.diego] },
      })
    }
    // o Diego sinaliza um cliente e o Rafael marca um descanso
    const diego = await entrar('diego.santos@teste.com')
    await api('POST', '/agenda/alertas', { token: diego.token, corpo: { agendamentoId: a2, motivo: 'Não pagou o último serviço', descricao: 'Ficou devendo o corte do mês passado.' } })
    const rafael = await entrar('rafael.lima@teste.com')
    await api('PUT', '/agenda/descansos', { token: rafael.token, corpo: { data: d2, hora: '16:00', descanso: true } })
  }

  if (studio) {
    const s1 = await agendar(studio, carla, { item: 'Escova', quem: 'dono', data: d1, hora: '10:00' })
    await agendar(studio, gabriel, { item: 'Manicure', quem: 'dono', data: d2, hora: '14:00' })
    await agendar(studio, ana, { item: 'Corte + Escova', quem: 'dono', data: d6, hora: '10:00' })
    await api('POST', `/painel/agendamentos/${s1}/aprovar`, { token: studio.token })
  }

  console.log('\nContas (senha 12345678):')
  console.table([
    { papel: 'dono (salão com equipe)', email: 'gabriel.naoki@teste.com', onde: '/login' },
    { papel: 'funcionário', email: 'rafael.lima@teste.com', onde: '/login' },
    { papel: 'funcionário', email: 'diego.santos@teste.com', onde: '/login' },
    { papel: 'dona (autônoma)', email: 'juliana.matos@teste.com', onde: '/login' },
    { papel: 'cliente', email: 'gabriel.naoki.cliente@teste.com', onde: '/acesso' },
    { papel: 'cliente', email: 'ana.souza@teste.com', onde: '/acesso' },
    { papel: 'cliente', email: 'bruno.costa@teste.com', onde: '/acesso' },
    { papel: 'cliente', email: 'carla.dias@teste.com', onde: '/acesso' },
  ])
  if (barbearia) console.log(`Convite ainda não usado (Lucas Pereira): ${barbearia.convites.lucas}`)
}

main().catch((e) => {
  console.error(`\nFalhou: ${e.message}`)
  process.exit(1)
})
