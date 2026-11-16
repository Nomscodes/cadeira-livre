import { getDb } from './db'
import { daysInMonth, startOf, toHHMM, toISO, toMinutes, todayISO, weekdayOf } from '../lib/dates'

export const STEP = 30
const ALMOCO = [12 * 60, 13 * 60] // descanso padrao: 12:00 as 13:00

export const profissionaisById = (ids) => getDb().profissionais.filter((p) => ids.includes(p.id))
const salaoPorId = (id) => getDb().saloes.find((s) => s.id === id)

/** Considera "finalizado" todo agendamento confirmado cujo horario ja terminou. */
export function statusEfetivo(a) {
  if (a.status !== 'confirmado') return a.status
  const fim = startOf(a.data, a.hora).getTime() + a.duracao * 60000
  return fim < Date.now() ? 'finalizado' : 'confirmado'
}

/** Intervalos (em minutos) em que o profissional nao pode atender naquele dia. */
export function intervalosOcupados(profId, data) {
  const db = getDb()
  const out = [ALMOCO]
  db.agendamentos.forEach((a) => {
    if (a.data !== data || a.status === 'cancelado') return
    if (!a.itens.some((i) => i.profissionalId === profId)) return
    const ini = toMinutes(a.hora)
    out.push([ini, ini + a.duracao])
  })
  db.descansos.forEach((d) => {
    if (d.profissionalId === profId && d.data === data) {
      const ini = toMinutes(d.hora)
      out.push([ini, ini + 60])
    }
  })
  return out
}

const conflita = (ini, fim, intervalos) => intervalos.some(([a, b]) => ini < b && fim > a)

export const bloqueioDoDia = (salaoId, data) =>
  getDb().bloqueios.find((b) => b.salaoId === salaoId && b.data === data)
export const folgaDoDia = (profId, data) =>
  getDb().folgas.find((f) => f.profissionalId === profId && f.data === data)

/** Horario do dia da semana (ou null se nao atende). Vale para salao e para profissional. */
const horarioDe = (entidade, data) => {
  const h = entidade?.horarios?.[weekdayOf(data)]
  return h?.ativo ? h : null
}
export const salaoAbre = (salao, data) => !!horarioDe(salao, data)

/**
 * Janela efetiva de trabalho do profissional na data, em minutos [inicio, fim]:
 * interseccao entre o horario do salao e o do profissional, sem bloqueio geral nem folga.
 */
export function janelaDoProfissional(salao, prof, data) {
  if (bloqueioDoDia(salao.id, data)) return null
  const hs = horarioDe(salao, data)
  const hp = horarioDe(prof, data)
  if (!hs || !hp || folgaDoDia(prof.id, data)) return null
  const ini = Math.max(toMinutes(hs.inicio), toMinutes(hp.inicio))
  const fim = Math.min(toMinutes(hs.fim), toMinutes(hp.fim))
  return ini < fim ? [ini, fim] : null
}

/** Horarios de inicio possiveis para o bloco `duracao` com todos os profissionais. */
export function horariosDoDia(salaoId, profIds, data, duracao) {
  const salao = salaoPorId(salaoId)
  const profs = profissionaisById(profIds)
  if (!salao || !profs.length) return []
  const janelas = profs.map((p) => janelaDoProfissional(salao, p, data))
  if (janelas.some((j) => !j)) return []
  const abre = Math.max(...janelas.map((j) => j[0]))
  const fecha = Math.min(...janelas.map((j) => j[1]))
  const hoje = data === todayISO()
  const agora = new Date().getHours() * 60 + new Date().getMinutes()
  const ocupados = profs.map((p) => intervalosOcupados(p.id, data))
  const out = []
  for (let ini = abre; ini + duracao <= fecha; ini += STEP) {
    if (ini >= ALMOCO[0] && ini < ALMOCO[1]) continue
    const livre = !(hoje && ini <= agora + 30) && ocupados.every((iv) => !conflita(ini, ini + duracao, iv))
    out.push({ hora: toHHMM(ini), livre })
  }
  return out
}

/**
 * Estado de cada dia do mes para o calendario do cliente:
 * passado | bloqueado (bloqueio geral, com motivo) | fechado (salao nao abre nesse dia da semana)
 * | folga (algum profissional nao atende) | lotado | ok
 */
export function diasDoMes(salaoId, profIds, year, month, duracao) {
  const salao = salaoPorId(salaoId)
  const profs = profissionaisById(profIds)
  const hoje = todayISO()
  const out = []
  for (let d = 1; d <= daysInMonth(year, month); d++) {
    const data = toISO(year, month, d)
    if (data < hoje) { out.push({ data, estado: 'passado' }); continue }
    const bloqueio = bloqueioDoDia(salaoId, data)
    if (bloqueio) { out.push({ data, estado: 'bloqueado', motivo: bloqueio.motivo }); continue }
    if (!salaoAbre(salao, data)) { out.push({ data, estado: 'fechado' }); continue }
    if (!profs.length || profs.some((p) => !janelaDoProfissional(salao, p, data))) { out.push({ data, estado: 'folga' }); continue }
    const livres = horariosDoDia(salaoId, profIds, data, duracao).some((h) => h.livre)
    out.push({ data, estado: livres ? 'ok' : 'lotado' })
  }
  return out
}

/** Duracao e valor de uma lista de itens ({servicoId|comboId, profissionalId}). */
export function calcularItens(itens) {
  const db = getDb()
  let duracao = 0
  let valor = 0
  const nomes = []
  for (const it of itens) {
    if (it.comboId) {
      const c = db.combos.find((x) => x.id === it.comboId)
      if (!c) continue
      c.servicoIds.forEach((sid) => { duracao += db.servicos.find((s) => s.id === sid)?.duracao || 0 })
      valor += c.preco
      nomes.push(c.nome)
    } else {
      const s = db.servicos.find((x) => x.id === it.servicoId)
      if (!s) continue
      duracao += s.duracao
      valor += s.preco
      nomes.push(s.nome)
    }
  }
  return { duracao, valor, nomes }
}

/** Avisos de conflito entre o horario do salao e o dos profissionais (mostrados ao salvar horarios). */
export function avisosDeConflito(salaoId) {
  const salao = salaoPorId(salaoId)
  const nomes = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado']
  const out = []
  getDb().profissionais.filter((p) => p.salaoId === salaoId).forEach((p) => {
    for (let d = 0; d < 7; d++) {
      const hp = p.horarios?.[d]
      if (!hp?.ativo) continue
      const hs = salao.horarios?.[d]
      if (!hs?.ativo) { out.push(`${p.nome} trabalha na ${nomes[d]}, mas o salão fica fechado nesse dia. Esse dia não terá horários.`); continue }
      if (hp.inicio < hs.inicio || hp.fim > hs.fim) out.push(`${p.nome} na ${nomes[d]}: o horário passa do funcionamento do salão (${hs.inicio} às ${hs.fim}). O atendimento fica limitado ao horário do salão.`)
    }
  })
  return out
}
