export const MESES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro']
export const DIAS_CAL = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S']
export const DIAS_SEMANA = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb']

export const pad = (n) => String(n).padStart(2, '0')

/** month é zero-based (igual ao Date). */
export const toISO = (year, month, day) => `${year}-${pad(month + 1)}-${pad(day)}`
export const dateToISO = (d) => toISO(d.getFullYear(), d.getMonth(), d.getDate())
export const todayISO = () => dateToISO(new Date())
export const parseISO = (iso) => {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}
export const monthKey = (year, month) => `${year}-${pad(month + 1)}`
export const daysInMonth = (year, month) => new Date(year, month + 1, 0).getDate()
export const firstWeekday = (year, month) => new Date(year, month, 1).getDay()
export const shiftMonth = (year, month, delta) => {
  const d = new Date(year, month + delta, 1)
  return { year: d.getFullYear(), month: d.getMonth() }
}
export const addDaysISO = (iso, n) => {
  const d = parseISO(iso)
  d.setDate(d.getDate() + n)
  return dateToISO(d)
}
export const weekdayOf = (iso) => parseISO(iso).getDay()

export const formatDateBR = (iso) => {
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}
export const formatDateTimeBR = (iso, hora) => `${formatDateBR(iso)} às ${hora}`
export const formatLongDate = (iso) => {
  const d = parseISO(iso)
  return `${DIAS_SEMANA[d.getDay()]}, ${d.getDate()} de ${MESES[d.getMonth()]}`
}

export const toMinutes = (hhmm) => {
  const [h, m] = hhmm.split(':').map(Number)
  return h * 60 + m
}
export const toHHMM = (min) => `${pad(Math.floor(min / 60))}:${pad(min % 60)}`

/** Ex.: 40 -> "40 min", 70 -> "1h10" */
export const formatDuration = (min) => {
  if (min < 60) return `${min} min`
  const h = Math.floor(min / 60)
  const r = min % 60
  return r ? `${h}h${pad(r)}` : `${h}h`
}

/** Data e hora do inicio do agendamento como Date local. */
export const startOf = (iso, hhmm) => {
  const d = parseISO(iso)
  const min = toMinutes(hhmm)
  d.setHours(Math.floor(min / 60), min % 60, 0, 0)
  return d
}

/** [1,2,3,4,5,6] -> "Seg a Sáb"; [1,3,5] -> "Seg, Qua, Sex" */
export const resumoDias = (dias) => {
  const d = [...dias].sort((a, b) => a - b)
  if (!d.length) return 'nenhum dia'
  const contiguo = d.every((v, i) => i === 0 || v === d[i - 1] + 1)
  if (d.length >= 3 && contiguo) return `${DIAS_SEMANA[d[0]]} a ${DIAS_SEMANA[d[d.length - 1]]}`
  return d.map((v) => DIAS_SEMANA[v]).join(', ')
}

const horaCurta = (hhmm) => {
  const [h, m] = hhmm.split(':')
  return m === '00' ? `${h}h` : `${h}h${m}`
}

/**
 * Resume horarios por dia da semana ({ 0..6: { ativo, inicio, fim } }) agrupando dias seguidos com o mesmo horario.
 * Ex.: "Seg a Sex, 09h às 18h · Sáb, 09h às 13h". Devolve "" se nenhum dia estiver ativo.
 */
export const resumoHorarios = (horarios) => {
  if (!horarios) return ''
  const ordem = [1, 2, 3, 4, 5, 6, 0] // segunda a domingo
  const grupos = []
  ordem.forEach((d, i) => {
    const h = horarios[d]
    if (!h?.ativo) return
    const chave = `${h.inicio}-${h.fim}`
    const ultimo = grupos[grupos.length - 1]
    if (ultimo && ultimo.chave === chave && ultimo.fimIdx === i - 1) { ultimo.dias.push(d); ultimo.fimIdx = i }
    else grupos.push({ chave, dias: [d], fimIdx: i, h })
  })
  return grupos.map((g) => {
    const n = g.dias.length
    const dias = n >= 3 ? `${DIAS_SEMANA[g.dias[0]]} a ${DIAS_SEMANA[g.dias[n - 1]]}` : g.dias.map((d) => DIAS_SEMANA[d]).join(' e ')
    return `${dias}, ${horaCurta(g.h.inicio)} às ${horaCurta(g.h.fim)}`
  }).join(' · ')
}

/** "há 5 min", "há 3 h", "ontem" ou a data. */
export const tempoRelativo = (iso) => {
  const min = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (min < 1) return 'agora'
  if (min < 60) return `há ${min} min`
  const h = Math.round(min / 60)
  if (h < 24) return `há ${h} h`
  if (h < 48) return 'ontem'
  return formatDateBR(dateToISO(new Date(iso)))
}
export const formatDataHora = (iso) => {
  const d = new Date(iso)
  return `${formatDateBR(dateToISO(d))} às ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
