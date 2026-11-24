// Povoa o banco com dados de demonstração usando só a API (nada de SQL direto: e-mail e telefone são cifrados,
// o login usa o hash do e-mail e a senha é BCrypt, então tudo tem que passar pelas regras do backend).
//
// Como usar (com o banco e a API rodando):
//   node database/povoar-pela-api.mjs
//   API_URL=http://localhost:8080/api node database/povoar-pela-api.mjs   (para apontar outra API)
//
// Pode rodar de novo: o salão cujo dono já existe é pulado. Para recriar com os dados novos, zere o banco antes.
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

const SEG_SEX = [1, 2, 3, 4, 5]
const SEG_SAB = [1, 2, 3, 4, 5, 6]
const TER_SAB = [2, 3, 4, 5, 6]

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

/** Cenário da equipe do salão de barbearia (folga, aviso, alerta e descanso), só no salão Sete Lâminas. */
async function cenarioDaEquipe(s, { ids, dias }) {
  const t = s.token
  // folga do Diego
  await api('PUT', `/painel/folgas/${s.ids.diego}/${dias[4]}`, { token: t, corpo: { motivo: 'Consulta médica' } })

  // o dono repassa um aviso à equipe a partir do pedido de pigmentação
  const notificacoes = await api('GET', '/notificacoes', { token: t })
  const pedido = notificacoes.find((n) => n.tipo === 'agendamento' && n.detalhes?.servicos === 'Pigmentação')
  if (pedido) {
    await api('POST', `/notificacoes/${pedido.id}/acao`, {
      token: t, corpo: { acao: 'aviso', mensagem: 'O Bruno pediu para chegar 10 minutos antes. Deixem a cadeira pronta.', profissionalIds: [s.ids.diego] },
    })
  }

  // o Diego sinaliza um cliente e o Rafael marca um descanso
  const diego = await entrar('diego.santos@teste.com')
  await api('POST', '/agenda/alertas', { token: diego.token, corpo: { agendamentoId: ids[1], motivo: 'Não pagou o último serviço', descricao: 'Ficou devendo o corte do mês passado.' } })
  const rafael = await entrar('rafael.lima@teste.com')
  await api('PUT', '/agenda/descansos', { token: rafael.token, corpo: { data: dias[1], hora: '16:00', descanso: true } })
}

// ---------- dados da demonstração ----------

/** Clientes: o primeiro é a conta da apresentação. */
const CLIENTES = {
  gabriel: ['Gabriel Naoki', 'cliente@teste.com', '(62) 98100-0001'],
  ana: ['Ana Souza', 'ana.souza@teste.com', '(62) 98100-0002'],
  bruno: ['Bruno Costa', 'bruno.costa@teste.com', '(62) 98100-0003'],
  carla: ['Carla Dias', 'carla.dias@teste.com', '(62) 98100-0004'],
  helena: ['Helena Rocha', 'helena.rocha@teste.com', '(62) 98100-0005'],
  tiago: ['Tiago Nunes', 'tiago.nunes@teste.com', '(62) 98100-0006'],
  paula: ['Paula Reis', 'paula.reis@teste.com', '(62) 98100-0007'],
  rodrigo: ['Rodrigo Melo', 'rodrigo.melo@teste.com', '(62) 98100-0008'],
}

/**
 * Os 10 salões. Em agendamentos: dia é o índice em `dias` (0 = primeiro dia útil);
 * status: 'aprovar' | 'recusar' | 'cancelar' (pelo cliente) | ausente = fica pendente.
 */
const SALOES = [
  {
    // salão da apresentação: admin@teste.com (dono) e func@teste.com (funcionário)
    chave: 'lamina',
    dono: { nome: 'Rafael Lima', razaoSocial: 'Barbearia Lâmina Clássica', documento: '53464097000150', email: 'admin@teste.com', atuacao: 'equipe' },
    equipe: { thiago: { nome: 'Thiago Rocha', nomeSocial: 'Thiago', whatsapp: '(62) 99301-0001', encaminhar: true, atende: true } },
    convites: { thiago: 'func@teste.com' },
    servicos: [
      { nome: 'Corte masculino', preco: 45, duracao: 30, descricao: 'Corte na tesoura ou máquina, com lavagem.', quem: ['thiago'] },
      { nome: 'Barba', preco: 35, duracao: 30, descricao: 'Barba com toalha quente e navalha.', quem: ['thiago'] },
      { nome: 'Sobrancelha', preco: 20, duracao: 15, descricao: 'Design e limpeza da sobrancelha.', quem: ['thiago'] },
      { nome: 'Hidratação', preco: 40, duracao: 30, descricao: 'Hidratação capilar com massagem.', quem: ['thiago'] },
    ],
    combos: [{ nome: 'Corte + Barba', servicos: ['Corte masculino', 'Barba'], preco: 70, quem: ['thiago'] }],
    portal: {
      nome: 'Barbearia Lâmina Clássica', temaId: 'azul-escuro', categoria: 'barbearia', cidade: 'Goiânia, GO',
      endereco: 'Av. T-10, 500, Setor Oeste', whatsapp: '(62) 99300-0000', redes: 'instagram.com/laminaclassica',
      sobre: 'Barbearia clássica com atendimento por agendamento. Corte, barba e cuidados masculinos com hora marcada.',
    },
    horarioSalao: semana(SEG_SEX, '09:00', '19:00', ['09:00', '14:00']),
    horarioProfissionais: { thiago: semana(SEG_SEX, '09:00', '18:00', ['09:00', '14:00']) },
    configuracoes: { atendimento: 'local', cancelamentoHoras: 4, funcionariosEditamHorario: true },
    agendamentos: [
      { cliente: 'gabriel', item: 'Corte + Barba', quem: 'thiago', dia: 0, hora: '10:00', status: 'aprovar' },
      { cliente: 'ana', item: 'Corte masculino', quem: 'thiago', dia: 1, hora: '14:00', status: 'aprovar' },
      { cliente: 'bruno', item: 'Barba', quem: 'thiago', dia: 2, hora: '11:00' },
    ],
  },
  {
    // salão com equipe, com o cenário completo de equipe (folga, aviso, alerta e descanso)
    chave: 'sete',
    demo: true,
    dono: { nome: 'Gabriel Naoki', razaoSocial: 'Barbearia Sete Lâminas', documento: '11222333000181', email: 'gabriel.naoki@teste.com', atuacao: 'equipe' },
    equipe: {
      rafael: { nome: 'Rafael Lima', nomeSocial: 'Rafael', whatsapp: '(62) 99101-0001', encaminhar: true, atende: true },
      diego: { nome: 'Diego Santos', nomeSocial: 'Diego', whatsapp: '(62) 99101-0002', encaminhar: false, atende: true },
      lucas: { nome: 'Lucas Pereira', nomeSocial: 'Lucas', whatsapp: '(62) 99101-0003', encaminhar: false, atende: true },
    },
    // Rafael e Diego entram pelo convite; Lucas ainda não usou o convite
    convites: { rafael: 'rafael.lima@teste.com', diego: 'diego.santos@teste.com' },
    servicos: [
      { nome: 'Corte masculino', preco: 45, duracao: 30, descricao: 'Corte na tesoura ou máquina, com lavagem.', quem: ['rafael', 'diego', 'lucas'] },
      { nome: 'Barba', preco: 35, duracao: 30, descricao: 'Barba com toalha quente e navalha.', quem: ['rafael', 'diego'] },
      { nome: 'Sobrancelha', preco: 20, duracao: 15, descricao: 'Design e limpeza da sobrancelha.', quem: ['lucas'] },
      { nome: 'Pigmentação', preco: 60, duracao: 45, descricao: 'Pigmentação de barba ou cabelo.', quem: ['diego'] },
      { nome: 'Hidratação', preco: 40, duracao: 30, descricao: 'Hidratação capilar com massagem.', quem: ['rafael', 'lucas'] },
    ],
    combos: [{ nome: 'Corte + Barba', servicos: ['Corte masculino', 'Barba'], preco: 70, quem: ['rafael', 'diego'] }],
    portal: {
      nome: 'Barbearia Sete Lâminas', temaId: 'azul-escuro', categoria: 'barbearia', cidade: 'Goiânia, GO',
      endereco: 'Av. T-63, 1200, Setor Bueno', whatsapp: '(62) 99100-0000', redes: 'instagram.com/setelaminas',
      sobre: 'Barbearia tradicional com atendimento por agendamento. Corte, barba e cuidados masculinos com hora marcada.',
    },
    horarioSalao: semana(SEG_SEX, '09:00', '19:00', ['09:00', '14:00']),
    horarioProfissionais: { rafael: semana(SEG_SEX, '09:00', '18:00', ['09:00', '14:00']) },
    configuracoes: { atendimento: 'local', cancelamentoHoras: 4, funcionariosEditamHorario: true },
    agendamentos: [
      { cliente: 'gabriel', item: 'Corte + Barba', quem: 'rafael', dia: 0, hora: '10:00', status: 'aprovar' },
      { cliente: 'ana', item: 'Corte masculino', quem: 'diego', dia: 0, hora: '14:00', status: 'aprovar' },
      { cliente: 'bruno', item: 'Pigmentação', quem: 'diego', dia: 1, hora: '10:00' },
      { cliente: 'carla', item: 'Sobrancelha', quem: 'lucas', dia: 1, hora: '15:00' },
      { cliente: 'gabriel', item: 'Barba', quem: 'rafael', dia: 2, hora: '11:00', status: 'recusar' },
      { cliente: 'ana', item: 'Corte masculino', quem: 'rafael', dia: 2, hora: '16:00', status: 'cancelar' },
      { cliente: 'bruno', item: 'Corte masculino', quem: 'rafael', dia: 3, hora: '09:00', status: 'aprovar' },
    ],
  },
  {
    // salão de profissional autônomo (a dona também atende)
    chave: 'studio',
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
    horarioSalao: semana(SEG_SEX, '09:00', '19:00', ['09:00', '15:00']),
    configuracoes: { atendimento: 'local', cancelamentoHoras: 6, funcionariosEditamHorario: false },
    agendamentos: [
      { cliente: 'carla', item: 'Escova', quem: 'dono', dia: 0, hora: '10:00', status: 'aprovar' },
      { cliente: 'gabriel', item: 'Manicure', quem: 'dono', dia: 1, hora: '14:00' },
      { cliente: 'ana', item: 'Corte + Escova', quem: 'dono', dia: 5, hora: '10:00' },
    ],
  },
  {
    chave: 'diego',
    dono: { nome: 'Vinícius Prado', razaoSocial: 'Diego Santos Barber', documento: '30246633000145', email: 'diego.barber@teste.com', atuacao: 'equipe' },
    equipe: { bruno: { nome: 'Bruno Almeida', nomeSocial: 'Bruno', whatsapp: '(62) 99401-0001', encaminhar: false, atende: true } },
    servicos: [
      { nome: 'Corte masculino', preco: 45, duracao: 30, descricao: 'Corte na tesoura ou máquina, com lavagem.', quem: ['bruno'] },
      { nome: 'Barba', preco: 35, duracao: 30, descricao: 'Barba com toalha quente e navalha.', quem: ['bruno'] },
      { nome: 'Pigmentação', preco: 60, duracao: 45, descricao: 'Pigmentação de barba ou cabelo.', quem: ['bruno'] },
    ],
    combos: [{ nome: 'Corte + Barba', servicos: ['Corte masculino', 'Barba'], preco: 70, quem: ['bruno'] }],
    portal: {
      nome: 'Diego Santos Barber', temaId: 'verde-escuro', categoria: 'barbearia', cidade: 'Aparecida de Goiânia, GO',
      endereco: 'Rua 7, 45, Centro', whatsapp: '(62) 99400-0000', redes: '',
      sobre: 'Corte clássico e barba na régua.',
    },
    horarioSalao: semana(SEG_SEX, '09:00', '18:00'),
    horarioProfissionais: { bruno: semana(SEG_SEX, '09:00', '18:00') },
    configuracoes: { atendimento: 'local', cancelamentoHoras: 2, funcionariosEditamHorario: false },
    agendamentos: [
      { cliente: 'bruno', item: 'Corte masculino', quem: 'bruno', dia: 3, hora: '09:00' },
      { cliente: 'tiago', item: 'Barba', quem: 'bruno', dia: 4, hora: '10:00' },
    ],
  },
  {
    chave: 'aurora',
    dono: { nome: 'Marina Lopes', razaoSocial: 'Espaço Aurora', documento: '16480894000102', email: 'espaco.aurora@teste.com', atuacao: 'equipe' },
    equipe: { patricia: { nome: 'Patrícia Gomes', nomeSocial: 'Patrícia', whatsapp: '(62) 99501-0001', encaminhar: false, atende: true } },
    servicos: [
      { nome: 'Manicure', preco: 35, duracao: 45, descricao: 'Cutilagem e esmaltação.', quem: ['patricia'] },
      { nome: 'Pedicure', preco: 40, duracao: 45, descricao: 'Cuidado completo dos pés e esmaltação.', quem: ['patricia'] },
      { nome: 'Alongamento de unhas', preco: 120, duracao: 90, descricao: 'Alongamento em gel ou fibra de vidro.', quem: ['patricia'] },
    ],
    combos: [{ nome: 'Manicure + Pedicure', servicos: ['Manicure', 'Pedicure'], preco: 70, quem: ['patricia'] }],
    portal: {
      nome: 'Espaço Aurora', temaId: 'laranja-pastel', categoria: 'salão de beleza', cidade: 'Goiânia, GO',
      endereco: 'Rua 1, 120, Setor Sul', whatsapp: '(62) 99500-0000', redes: 'instagram.com/espacoaurora',
      sobre: 'Manicure, pedicure e alongamento de unhas com hora marcada.',
    },
    horarioSalao: semana(SEG_SAB, '09:00', '18:00'),
    horarioProfissionais: { patricia: semana(SEG_SAB, '09:00', '18:00') },
    configuracoes: { atendimento: 'local', cancelamentoHoras: 4, funcionariosEditamHorario: false },
    agendamentos: [
      { cliente: 'helena', item: 'Manicure', quem: 'patricia', dia: 0, hora: '09:00', status: 'aprovar' },
      { cliente: 'paula', item: 'Alongamento de unhas', quem: 'patricia', dia: 2, hora: '13:00' },
    ],
  },
  {
    chave: 'navalha',
    dono: { nome: 'Henrique Lacerda', razaoSocial: 'Navalha & Cia', documento: '62992312000186', email: 'navalha.cia@teste.com', atuacao: 'equipe' },
    equipe: {
      felipe: { nome: 'Felipe Araújo', nomeSocial: 'Felipe', whatsapp: '(62) 99601-0001', encaminhar: false, atende: true },
      murilo: { nome: 'Murilo Ribeiro', nomeSocial: 'Murilo', whatsapp: '(62) 99601-0002', encaminhar: false, atende: true },
    },
    servicos: [
      { nome: 'Corte masculino', preco: 45, duracao: 30, descricao: 'Corte na tesoura ou máquina, com lavagem.', quem: ['felipe', 'murilo'] },
      { nome: 'Barba', preco: 35, duracao: 30, descricao: 'Barba com toalha quente e navalha.', quem: ['felipe'] },
      { nome: 'Sobrancelha', preco: 20, duracao: 15, descricao: 'Design e limpeza da sobrancelha.', quem: ['murilo'] },
      { nome: 'Hidratação', preco: 40, duracao: 30, descricao: 'Hidratação capilar com massagem.', quem: ['murilo'] },
    ],
    combos: [{ nome: 'Corte + Barba', servicos: ['Corte masculino', 'Barba'], preco: 70, quem: ['felipe'] }],
    portal: {
      nome: 'Navalha & Cia', temaId: 'azul-escuro', categoria: 'barbearia', cidade: 'Goiânia, GO',
      endereco: 'Rua 44, 300, Setor Oeste', whatsapp: '(62) 99600-0000', redes: 'instagram.com/navalhaecia',
      sobre: 'Barbearia com atendimento descontraído e horário marcado pelo site.',
    },
    horarioSalao: semana(SEG_SEX, '10:00', '20:00'),
    horarioProfissionais: {
      felipe: semana(SEG_SEX, '10:00', '19:00'),
      murilo: semana(SEG_SEX, '10:00', '19:00'),
    },
    configuracoes: { atendimento: 'local', cancelamentoHoras: 3, funcionariosEditamHorario: false },
    agendamentos: [
      { cliente: 'rodrigo', item: 'Corte masculino', quem: 'felipe', dia: 1, hora: '10:00', status: 'aprovar' },
      { cliente: 'tiago', item: 'Sobrancelha', quem: 'murilo', dia: 3, hora: '11:00' },
    ],
  },
  {
    chave: 'bella',
    dono: { nome: 'Vitória Mendes', razaoSocial: 'Bella Donna Beauty', documento: '97366946000108', email: 'bella.donna@teste.com', atuacao: 'equipe' },
    equipe: { priscila: { nome: 'Priscila Souza', nomeSocial: 'Priscila', whatsapp: '(62) 99701-0001', encaminhar: false, atende: true } },
    servicos: [
      { nome: 'Escova', preco: 60, duracao: 45, descricao: 'Escova modeladora.', quem: ['priscila'] },
      { nome: 'Corte feminino', preco: 80, duracao: 60, descricao: 'Corte com lavagem e finalização.', quem: ['priscila'] },
      { nome: 'Coloração', preco: 150, duracao: 120, descricao: 'Coloração completa com tonalizante.', quem: ['priscila'] },
      { nome: 'Limpeza de pele', preco: 90, duracao: 60, descricao: 'Limpeza facial com extração e máscara.', quem: ['priscila'] },
    ],
    combos: [{ nome: 'Corte + Escova', servicos: ['Corte feminino', 'Escova'], preco: 125, quem: ['priscila'] }],
    portal: {
      nome: 'Bella Donna Beauty', temaId: 'violeta-pastel', categoria: 'salão de beleza', cidade: 'Goiânia, GO',
      endereco: 'Av. T-4, 800, Setor Bueno', whatsapp: '(62) 99700-0000', redes: 'instagram.com/belladonnabeauty',
      sobre: 'Coloração, cortes e cuidados faciais em um espaço acolhedor.',
    },
    horarioSalao: semana(TER_SAB, '09:00', '18:00'),
    horarioProfissionais: { priscila: semana(TER_SAB, '09:00', '18:00') },
    configuracoes: { atendimento: 'local', cancelamentoHoras: 6, funcionariosEditamHorario: false },
    agendamentos: [
      { cliente: 'paula', item: 'Escova', quem: 'priscila', dia: 2, hora: '09:00', status: 'aprovar' },
      { cliente: 'helena', item: 'Coloração', quem: 'priscila', dia: 4, hora: '10:00' },
    ],
  },
  {
    chave: 'mel',
    dono: { nome: 'Mel Azevedo', razaoSocial: 'Ateliê Mel Hair', documento: '98183655343', email: 'mel.hair@teste.com', atuacao: 'autonomo' },
    servicos: [
      { nome: 'Corte feminino', preco: 80, duracao: 60, descricao: 'Corte com lavagem e finalização.', quem: ['dono'] },
      { nome: 'Progressiva', preco: 180, duracao: 150, descricao: 'Escova progressiva com redução de volume.', quem: ['dono'] },
      { nome: 'Hidratação', preco: 60, duracao: 45, descricao: 'Hidratação capilar profunda.', quem: ['dono'] },
    ],
    portal: {
      nome: 'Ateliê Mel Hair', temaId: 'verde-escuro', categoria: 'salão de beleza', cidade: 'Goiânia, GO',
      endereco: 'Rua 12, 88, Setor Marista', whatsapp: '(62) 99800-0000', redes: 'instagram.com/atelie.melhair',
      sobre: 'Ateliê de cabelo com atendimento individual e agenda reduzida.',
    },
    horarioSalao: semana(TER_SAB, '09:00', '18:00'),
    configuracoes: { atendimento: 'local', cancelamentoHoras: 6, funcionariosEditamHorario: false },
    agendamentos: [
      { cliente: 'ana', item: 'Hidratação', quem: 'dono', dia: 1, hora: '10:00', status: 'aprovar' },
    ],
  },
  {
    chave: 'alfaiate',
    dono: { nome: 'Eduardo Nunes', razaoSocial: 'Alfaiate Barber Club', documento: '19722233000126', email: 'alfaiate.barber@teste.com', atuacao: 'equipe' },
    equipe: {
      henrique: { nome: 'Henrique Lacerda', nomeSocial: 'Henrique', whatsapp: '(62) 99801-0001', encaminhar: false, atende: true },
      igor: { nome: 'Igor Moraes', nomeSocial: 'Igor', whatsapp: '(62) 99801-0002', encaminhar: false, atende: true },
    },
    servicos: [
      { nome: 'Corte masculino', preco: 45, duracao: 30, descricao: 'Corte na tesoura ou máquina, com lavagem.', quem: ['henrique', 'igor'] },
      { nome: 'Barba', preco: 35, duracao: 30, descricao: 'Barba com toalha quente e navalha.', quem: ['henrique', 'igor'] },
      { nome: 'Pigmentação', preco: 60, duracao: 45, descricao: 'Pigmentação de barba ou cabelo.', quem: ['igor'] },
    ],
    combos: [{ nome: 'Corte + Barba', servicos: ['Corte masculino', 'Barba'], preco: 70, quem: ['henrique', 'igor'] }],
    portal: {
      nome: 'Alfaiate Barber Club', temaId: 'azul-escuro', categoria: 'barbearia', cidade: 'Goiânia, GO',
      endereco: 'Av. C-225, 600, Setor Nova Suíça', whatsapp: '(62) 99800-1000', redes: 'instagram.com/alfaiatebarber',
      sobre: 'Clube de barbearia com cortes sob medida e atendimento com hora marcada.',
    },
    horarioSalao: semana(SEG_SEX, '09:00', '19:00'),
    horarioProfissionais: {
      henrique: semana(SEG_SEX, '09:00', '19:00'),
      igor: semana(SEG_SEX, '09:00', '19:00'),
    },
    configuracoes: { atendimento: 'local', cancelamentoHoras: 4, funcionariosEditamHorario: false },
    agendamentos: [
      { cliente: 'tiago', item: 'Corte + Barba', quem: 'igor', dia: 0, hora: '15:00' },
      { cliente: 'paula', item: 'Barba', quem: 'henrique', dia: 5, hora: '09:00' },
    ],
  },
  {
    chave: 'unhas',
    dono: { nome: 'Camila Teixeira', razaoSocial: 'Unhas & Art Studio', documento: '82345678000170', email: 'unhas.art@teste.com', atuacao: 'equipe' },
    equipe: { larissa: { nome: 'Larissa Freitas', nomeSocial: 'Larissa', whatsapp: '(62) 99901-0001', encaminhar: false, atende: true } },
    servicos: [
      { nome: 'Manicure', preco: 35, duracao: 45, descricao: 'Cutilagem e esmaltação.', quem: ['larissa'] },
      { nome: 'Pedicure', preco: 40, duracao: 45, descricao: 'Cuidado completo dos pés e esmaltação.', quem: ['larissa'] },
      { nome: 'Unhas decoradas', preco: 70, duracao: 60, descricao: 'Esmaltação com nail art e decoração à escolha.', quem: ['larissa'] },
    ],
    portal: {
      nome: 'Unhas & Art Studio', temaId: 'laranja-pastel', categoria: 'salão de beleza', cidade: 'Goiânia, GO',
      endereco: 'Rua 30, 210, Setor Pedro Ludovico', whatsapp: '(62) 99900-0000', redes: 'instagram.com/unhasartstudio',
      sobre: 'Manicure, pedicure e unhas decoradas com atendimento tranquilo.',
    },
    horarioSalao: semana(SEG_SEX, '09:00', '18:00'),
    horarioProfissionais: { larissa: semana(SEG_SEX, '09:00', '18:00') },
    configuracoes: { atendimento: 'local', cancelamentoHoras: 4, funcionariosEditamHorario: false },
    agendamentos: [
      { cliente: 'carla', item: 'Unhas decoradas', quem: 'larissa', dia: 3, hora: '10:00' },
    ],
  },
]

async function main() {
  console.log(`Povoando ${API} ...`)
  const dias = diasUteis(6, temFeriado ? [feriado] : [])

  // clientes (o cadastro devolve a sessão; se já existir, só entra)
  const clientes = {}
  for (const [chave, [nome, email, telefone]] of Object.entries(CLIENTES)) {
    clientes[chave] = await cliente(nome, email, telefone)
  }

  // salões: cria o dono, a equipe, os serviços, o portal e publica
  const criados = {}
  for (const cfg of SALOES) criados[cfg.chave] = await salao(cfg)

  for (const cfg of SALOES) {
    const s = criados[cfg.chave]
    if (!s) continue

    // funcionários criam a conta com o código de convite
    for (const [chave, email] of Object.entries(cfg.convites || {})) {
      await api('POST', '/auth/convite', { corpo: { codigo: s.convites[chave], email, senha: SENHA, aceitouTermos: true } })
    }

    // agendamentos em vários status
    const ids = []
    for (const ag of cfg.agendamentos || []) {
      const cli = clientes[ag.cliente]
      const id = await agendar(s, cli, { item: ag.item, quem: ag.quem, data: dias[ag.dia], hora: ag.hora })
      ids.push(id)
      if (ag.status === 'aprovar') await api('POST', `/painel/agendamentos/${id}/aprovar`, { token: s.token })
      if (ag.status === 'recusar') await api('POST', `/painel/agendamentos/${id}/recusar`, { token: s.token })
      if (ag.status === 'cancelar') await api('POST', `/cliente/agendamentos/${id}/cancelar`, { token: cli.token })
    }

    if (cfg.demo) await cenarioDaEquipe(s, { ids, dias })
  }

  console.log('\nContas da apresentação (senha 12345678):')
  console.table([
    { papel: 'dono (Barbearia Lâmina Clássica)', email: 'admin@teste.com', onde: '/login' },
    { papel: 'funcionário (Barbearia Lâmina Clássica)', email: 'func@teste.com', onde: '/login' },
    { papel: 'cliente', email: 'cliente@teste.com', onde: '/acesso' },
  ])
  console.log('Outras contas:')
  console.table([
    { papel: 'dono (Barbearia Sete Lâminas)', email: 'gabriel.naoki@teste.com', onde: '/login' },
    { papel: 'funcionário (Sete Lâminas)', email: 'rafael.lima@teste.com', onde: '/login' },
    { papel: 'funcionário (Sete Lâminas)', email: 'diego.santos@teste.com', onde: '/login' },
    { papel: 'dona (Studio Juliana Matos)', email: 'juliana.matos@teste.com', onde: '/login' },
    { papel: 'donos dos demais salões', email: 'diego.barber, espaco.aurora, navalha.cia, bella.donna, mel.hair, alfaiate.barber, unhas.art (@teste.com)', onde: '/login' },
    { papel: 'clientes', email: 'ana.souza, bruno.costa, carla.dias, helena.rocha, tiago.nunes, paula.reis, rodrigo.melo (@teste.com)', onde: '/acesso' },
  ])
  if (criados.sete) console.log(`Convite ainda não usado (Lucas Pereira): ${criados.sete.convites.lucas}`)
}

main().catch((e) => {
  console.error(`\nFalhou: ${e.message}`)
  process.exit(1)
})
