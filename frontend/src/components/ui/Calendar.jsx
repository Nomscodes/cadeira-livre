import { ChevronLeft, ChevronRight } from 'lucide-react'
import { DIAS_CAL, MESES, daysInMonth, firstWeekday, toISO, todayISO } from '../../lib/dates'

/**
 * Calendario mensal acessivel (cada dia e um botao).
 *  - getDay(iso) -> { state: 'ok' | 'disabled' | 'off' | 'blocked', title? }
 *      ok        clicavel
 *      disabled  indisponivel (passado, lotado)   -> riscado
 *      off       dia em que o profissional nao trabalha -> cinza
 *      blocked   bloqueio geral do salao          -> vermelho (clicavel so com clickable: true)
 */
export default function Calendar({ year, month, onMonthChange, getDay, selected, onSelect, canGoPrev = true, legend, label = 'Calendário' }) {
  const total = daysInMonth(year, month)
  const blanks = firstWeekday(year, month)
  const hoje = todayISO()

  const cells = []
  for (let i = 0; i < blanks; i++) cells.push(<div key={`b${i}`} className="cal-cell empty" aria-hidden="true" />)
  for (let d = 1; d <= total; d++) {
    const iso = toISO(year, month, d)
    const info = getDay(iso) || { state: 'disabled' }
    const clickable = info.state === 'ok' || !!info.clickable
    const classes = ['cal-cell']
    if (info.state !== 'ok') classes.push(info.state)
    if (iso === selected) classes.push('selected')
    if (iso === hoje) classes.push('today')
    const desc = { ok: '', disabled: ', indisponível', off: ', não trabalha', blocked: `, salão fechado${info.title ? `: ${info.title}` : ''}` }[info.state] || ''
    cells.push(
      <button
        key={iso} type="button" className={classes.join(' ')} disabled={!clickable}
        aria-pressed={iso === selected} aria-label={`${d} de ${MESES[month]}${desc}`}
        title={info.title} onClick={() => onSelect?.(iso)}
      >
        {d}
      </button>,
    )
  }

  return (
    <div className="calendar" role="group" aria-label={label}>
      <div className="cal-head">
        <button type="button" className="cal-nav" onClick={() => onMonthChange(-1)} disabled={!canGoPrev} aria-label="Mês anterior"><ChevronLeft size={20} aria-hidden="true" /></button>
        <span aria-live="polite">{MESES[month]} de {year}</span>
        <button type="button" className="cal-nav" onClick={() => onMonthChange(1)} aria-label="Próximo mês"><ChevronRight size={20} aria-hidden="true" /></button>
      </div>
      <div className="cal-grid">
        {DIAS_CAL.map((d, i) => <div key={i} className="cal-dow" aria-hidden="true">{d}</div>)}
        {cells}
      </div>
      {legend}
    </div>
  )
}
