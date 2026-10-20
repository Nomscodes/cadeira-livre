import { store } from '../lib/storage'
import { addDaysISO, todayISO, weekdayOf } from '../lib/dates'

const KEY = 'cl:mockdb:v2' // v2: horarios por dia da semana, folgas, capa e notificacoes tipadas

/** Proximo dia util (seg-sex) a partir de `n` dias de hoje, para o seed sempre cair em dia de trabalho. */
function diaUtil(n) {
  let iso = addDaysISO(todayISO(), n)
  while ([0, 6].includes(weekdayOf(iso))) iso = addDaysISO(iso, n >= 0 ? 1 : -1)
  return iso
}

/** Semana completa: { 0..6: { ativo, inicio, fim } } (0 = domingo). */
export const semana = (dias, inicio, fim) =>
  Object.fromEntries([0, 1, 2, 3, 4, 5, 6].map((d) => [d, { ativo: dias.includes(d), inicio, fim }]))

const minutosAtras = (m) => new Date(Date.now() - m * 60000).toISOString()

function seed() {
  const hoje = todayISO()
  return {
    seq: 100,
    usuarios: [
      { id: 'u-admin', nome: 'Rafael Lima', email: 'admin@teste.com', apelidos: ['admin'], senha: 'admin', papel: 'administrador', salaoId: 's1', profissionalId: 'p1' },
      { id: 'u-func', nome: 'Rafael Lima', email: 'func@teste.com', apelidos: ['func'], senha: 'func', papel: 'funcionario', salaoId: 's1', profissionalId: 'p1' },
    ],
    saloes: [
      {
        id: 's1', slug: 'barbearia-do-rafael', nome: 'Barbearia do Rafael', cidade: 'Goiânia, GO', categoria: 'barbearia',
        temaId: 'azul-escuro', publico: true, logo: null, galeria: [],
        sobre: 'Há dez anos cuidando do visual de Goiânia. Ambiente tranquilo, hora marcada e atendimento sem pressa.',
        whatsapp: '(62) 90000-0000', endereco: 'Rua Exemplo, 123, Setor Central, Goiânia, GO',
        redes: 'instagram.com/barbeariadorafael', capa: null, horarios: semana([1, 2, 3, 4, 5, 6], '09:00', '19:00'),
        atendimento: 'local', cancelamentoHoras: 4, funcionariosEditamHorario: true, adminIds: ['p1'],
      },
      {
        id: 's2', slug: 'studio-juliana-matos', nome: 'Studio Juliana Matos', cidade: 'Goiânia, GO', categoria: 'salão de beleza',
        temaId: 'violeta-pastel', publico: true, logo: null, galeria: [],
        sobre: 'Coloração, cortes e design de sobrancelha em um espaço acolhedor.',
        whatsapp: '(62) 91111-0000', endereco: 'Av. T-4, 800, Setor Bueno, Goiânia, GO',
        redes: '', capa: null, horarios: semana([2, 3, 4, 5, 6], '10:00', '19:00'), atendimento: 'local', cancelamentoHoras: 6,
        funcionariosEditamHorario: false, adminIds: ['p4'],
      },
      {
        id: 's3', slug: 'diego-santos-barber', nome: 'Diego Santos Barber', cidade: 'Aparecida de Goiânia, GO', categoria: 'barbearia',
        temaId: 'verde-escuro', publico: true, logo: null, galeria: [],
        sobre: 'Corte clássico e barba na régua.', whatsapp: '(62) 92222-0000',
        endereco: 'Rua 7, 45, Centro, Aparecida de Goiânia, GO', redes: '', capa: null, horarios: semana([1, 2, 3, 4, 5], '09:00', '18:00'),
        atendimento: 'local', cancelamentoHoras: 2, funcionariosEditamHorario: false, adminIds: ['p5'],
      },
      {
        id: 's4', slug: 'espaco-aurora', nome: 'Espaço Aurora', cidade: 'Goiânia, GO', categoria: 'unhas',
        temaId: 'laranja-pastel', publico: false, logo: null, galeria: [], sobre: '', whatsapp: '', endereco: '',
        redes: '', capa: null, horarios: semana([1, 2, 3, 4, 5], '09:00', '18:00'), atendimento: 'local', cancelamentoHoras: 4, funcionariosEditamHorario: false, adminIds: [],
      },
    ],
    servicos: [
      { id: 'sv1', salaoId: 's1', nome: 'Corte de cabelo', preco: 45, duracao: 40, profissionalIds: ['p1', 'p2'], destaque: true },
      { id: 'sv2', salaoId: 's1', nome: 'Barba', preco: 35, duracao: 30, profissionalIds: ['p1'], destaque: true },
      { id: 'sv3', salaoId: 's1', nome: 'Design de sobrancelha', preco: 20, duracao: 15, profissionalIds: ['p2'], destaque: true },
      { id: 'sv4', salaoId: 's1', nome: 'Coloração', preco: 80, duracao: 60, profissionalIds: ['p2'], destaque: true },
      { id: 'sv5', salaoId: 's2', nome: 'Coloração', preco: 120, duracao: 90, profissionalIds: ['p4'], destaque: true },
      { id: 'sv6', salaoId: 's2', nome: 'Design de sobrancelha', preco: 25, duracao: 20, profissionalIds: ['p4'], destaque: true },
      { id: 'sv7', salaoId: 's3', nome: 'Corte clássico', preco: 40, duracao: 40, profissionalIds: ['p5'], destaque: true },
      { id: 'sv8', salaoId: 's3', nome: 'Barba na régua', preco: 30, duracao: 30, profissionalIds: ['p5'], destaque: true },
    ],
    combos: [
      { id: 'cb1', salaoId: 's1', nome: 'Combo Corte + Barba', servicoIds: ['sv1', 'sv2'], preco: 70, profissionalIds: ['p1'] },
    ],
    profissionais: [
      { id: 'p1', salaoId: 's1', nome: 'Rafael Lima', nomeSocial: 'Rafael', whatsapp: '(62) 99999-0001', encaminhar: true, servicoIds: ['sv1', 'sv2'], horarios: semana([1, 2, 3, 4, 5, 6], '09:00', '18:00') },
      { id: 'p2', salaoId: 's1', nome: 'Juliana Matos', nomeSocial: 'Juliana', whatsapp: '(62) 99999-0002', encaminhar: false, servicoIds: ['sv1', 'sv3', 'sv4'], horarios: semana([1, 2, 3, 4, 5], '09:00', '18:00') },
      { id: 'p4', salaoId: 's2', nome: 'Juliana Matos', nomeSocial: 'Juliana', whatsapp: '(62) 99999-0004', encaminhar: true, servicoIds: ['sv5', 'sv6'], horarios: semana([2, 3, 4, 5, 6], '10:00', '19:00') },
      { id: 'p5', salaoId: 's3', nome: 'Diego Santos', nomeSocial: 'Diego', whatsapp: '(62) 99999-0005', encaminhar: true, servicoIds: ['sv7', 'sv8'], horarios: semana([1, 2, 3, 4, 5], '09:00', '18:00') },
    ],
    bloqueios: [{ id: 'b1', salaoId: 's1', data: diaUtil(6), motivo: 'Feriado municipal, salão fechado' }],
    descansos: [], // { profissionalId, data, hora }
    folgas: [{ id: 'f1', salaoId: 's1', profissionalId: 'p2', data: diaUtil(10), motivo: 'Consulta médica' }], // folga de um profissional num dia
    agendamentos: [
      { id: 'a1', salaoId: 's1', clienteNome: 'Marcos Silva', clienteWhats: '(62) 99999-1234', itens: [{ servicoId: 'sv1', profissionalId: 'p1' }, { servicoId: 'sv2', profissionalId: 'p1' }], data: diaUtil(2), hora: '10:00', duracao: 70, valor: 80, status: 'confirmado', avaliado: false },
      { id: 'a2', salaoId: 's1', clienteNome: 'Ana Paula', clienteWhats: '(62) 98888-2222', itens: [{ servicoId: 'sv1', profissionalId: 'p1' }, { servicoId: 'sv3', profissionalId: 'p2' }], data: diaUtil(3), hora: '14:00', duracao: 55, valor: 65, status: 'confirmado', avaliado: false },
      { id: 'a3', salaoId: 's1', clienteNome: 'Bruno Costa', clienteWhats: '(62) 97777-3333', itens: [{ servicoId: 'sv4', profissionalId: 'p2' }], data: diaUtil(-20), hora: '14:00', duracao: 60, valor: 80, status: 'finalizado', avaliado: true },
      { id: 'a4', salaoId: 's1', clienteNome: 'Fernanda Reis', clienteWhats: '(62) 96666-4444', itens: [{ servicoId: 'sv3', profissionalId: 'p2' }], data: diaUtil(-25), hora: '11:00', duracao: 15, valor: 20, status: 'cancelado', avaliado: false },
      { id: 'a5', salaoId: 's1', clienteNome: 'Carlos Mendes', clienteWhats: '(62) 95555-1111', itens: [{ servicoId: 'sv2', profissionalId: 'p1' }], data: diaUtil(-12), hora: '10:00', duracao: 30, valor: 35, status: 'cancelado', avaliado: false },
      { id: 'a6', salaoId: 's1', clienteNome: 'Pedro Alves', clienteWhats: '(62) 94444-2222', itens: [{ servicoId: 'sv1', profissionalId: 'p1' }], data: diaUtil(-6), hora: '15:00', duracao: 40, valor: 45, status: 'finalizado', avaliado: true },
      { id: 'a7', salaoId: 's1', clienteNome: 'Lucas Ferreira', clienteWhats: '(62) 93333-3333', itens: [{ servicoId: 'sv1', profissionalId: 'p1' }, { servicoId: 'sv2', profissionalId: 'p1' }], data: diaUtil(8), hora: '16:00', duracao: 70, valor: 80, status: 'confirmado', avaliado: false },
      // historico da cliente de demonstracao (Camila Rocha), em saloes diferentes
      { id: 'h1', demo: true, salaoId: 's1', clienteNome: 'Camila Rocha', clienteWhats: '(62) 95555-0000', itens: [{ servicoId: 'sv1', profissionalId: 'p1' }], data: diaUtil(4), hora: '09:00', duracao: 40, valor: 45, status: 'confirmado', avaliado: false },
      { id: 'h2', demo: true, salaoId: 's1', clienteNome: 'Camila Rocha', clienteWhats: '(62) 95555-0000', itens: [{ comboId: 'cb1', profissionalId: 'p1' }], data: diaUtil(-18), hora: '14:00', duracao: 70, valor: 70, status: 'finalizado', avaliado: false },
      { id: 'h3', demo: true, salaoId: 's2', clienteNome: 'Camila Rocha', clienteWhats: '(62) 95555-0000', itens: [{ servicoId: 'sv5', profissionalId: 'p4' }], data: diaUtil(-30), hora: '11:00', duracao: 90, valor: 120, status: 'finalizado', avaliado: true },
      { id: 'h4', demo: true, salaoId: 's2', clienteNome: 'Camila Rocha', clienteWhats: '(62) 95555-0000', itens: [{ servicoId: 'sv6', profissionalId: 'p4' }], data: diaUtil(-28), hora: '11:00', duracao: 20, valor: 25, status: 'cancelado', avaliado: false },
    ],
    avaliacoes: [],
    alertas: [],
    notificacoes: [
      { id: 'n1', salaoId: 's1', para: 'administrador', tipo: 'lembrete', titulo: 'Atendimento daqui a 30 minutos', texto: 'Marcos Silva tem horário às 10:00 com Rafael (Corte de cabelo + Barba).', criadaEm: minutosAtras(12), lida: false, agendamentoId: 'a1' },
      { id: 'n2', salaoId: 's1', para: 'administrador', tipo: 'cancelamento', titulo: 'Agendamento cancelado', texto: 'Carlos Mendes cancelou o horário das 10:00 (Barba).', criadaEm: minutosAtras(95), lida: false, agendamentoId: 'a5' },
      { id: 'n3', salaoId: 's1', para: 'administrador', tipo: 'avaliacao', titulo: 'Atendimento concluído', texto: 'O atendimento de Pedro Alves (Corte de cabelo) foi concluído. O cliente já pode avaliar o serviço.', criadaEm: minutosAtras(180), lida: false, agendamentoId: 'a6' },
      { id: 'n4', salaoId: 's1', para: 'funcionario', profissionalId: 'p1', tipo: 'lembrete', titulo: 'Próximo atendimento', texto: 'Marcos Silva, Corte de cabelo + Barba.', criadaEm: minutosAtras(20), lida: false, agendamentoId: 'a1' },
      { id: 'n5', salaoId: 's1', para: 'funcionario', profissionalId: 'p1', tipo: 'aviso', titulo: 'Aviso do responsável', texto: 'Reunião de alinhamento da equipe na sexta, às 08:30, antes da abertura.', remetente: 'Responsável', criadaEm: minutosAtras(300), lida: false },
    ],
    criadoEm: hoje,
  }
}

let db = store.get(KEY) || seed()

export const getDb = () => db
export const save = () => store.set(KEY, db)
export const nextId = (prefix) => `${prefix}${++db.seq}`
export function resetMock() {
  db = seed()
  save()
}
