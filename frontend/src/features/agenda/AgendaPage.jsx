import { lazy, Suspense, useMemo, useState } from 'react'
import { CalendarX, LogOut } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync, useDocumentTitle } from '../../lib/hooks'
import {
  formatDateBR, formatDuration, formatLongDate, monthKey, resumoHorarios, shiftMonth, toHHMM, toMinutes, weekdayOf,
} from '../../lib/dates'
import { initials, plural } from '../../lib/format'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import ThemeScope from '../../theme/ThemeScope'
import Calendar from '../../components/ui/Calendar'
import Button from '../../components/ui/Button'
import { ConfirmDialog, Modal } from '../../components/ui/Modal'
import StatusChip from '../../components/ui/StatusChip'
import { Field, FieldGroup } from '../../components/ui/Field'
import { EmptyState, ErrorState, Loading } from '../../components/ui/States'
import NotificationBell from '../../components/layout/NotificationBell'
import Frame from '../../components/layout/Frame'

// SOMENTE TESTE: fora do build de deploy (ver lib/flags.js)
const AtalhoTeste = __MODO_TESTE__ ? lazy(() => import('./AtalhoTeste')) : null

const MOTIVOS = ['Não pagou o último serviço', 'Não me sinto seguro(a) com esse cliente', 'Cliente agressivo', 'Outro (descrever abaixo)']
const ABAS = [['confirmado', 'próximos'], ['finalizado', 'realizados'], ['cancelado', 'cancelados']]
const POR_PAGINA = 8

function AlertaModal({ agendamento, onClose }) {
  const toast = useToast()
  const [motivo, setMotivo] = useState('')
  const [descricao, setDescricao] = useState('')
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState(false)
  const enviar = async () => {
    if (!motivo) return setErro('Escolha o motivo do alerta.')
    setBusy(true)
    try {
      await api.post('/agenda/alertas', { agendamentoId: agendamento.id, motivo, descricao }, { as: 'staff' })
      toast.success('Alerta enviado ao responsável.')
      onClose()
    } catch (e) { setErro(e.message) } finally { setBusy(false) }
  }
  return (
    <Modal title="Alertar o responsável sobre este cliente" onClose={onClose}>
      <FieldGroup label="motivo" error={erro}>
        {MOTIVOS.map((m) => <label className="check-row" key={m}><input type="radio" name="motivo" checked={motivo === m} onChange={() => { setMotivo(m); setErro('') }} /> {m}</label>)}
      </FieldGroup>
      <Field label="descreva com suas palavras (opcional)"><textarea rows={3} value={descricao} onChange={(e) => setDescricao(e.target.value)} maxLength={500} /></Field>
      <div className="modal-actions">
        <Button variant="ghost" onClick={onClose}>Cancelar</Button>
        <Button loading={busy} onClick={enviar}>Enviar alerta</Button>
      </div>
    </Modal>
  )
}

function DetalheModal({ ag, onClose, onAlertar }) {
  return (
    <Modal title="Detalhe do agendamento" onClose={onClose}>
      <div className="modal-row"><span>cliente</span><b>{ag.cliente}</b></div>
      <div className="modal-row"><span>serviço</span><b>{ag.servicos}</b></div>
      <div className="modal-row"><span>data e hora</span><b>{formatDateBR(ag.data)} às {ag.hora}</b></div>
      <div className="modal-row"><span>duração média</span><b>{formatDuration(ag.duracao)}</b></div>
      <div className="modal-row"><span>colegas trabalhando junto</span><b>{ag.colegas.length ? ag.colegas.join(', ') : 'nenhum'}</b></div>
      <div className="modal-row"><span>status</span><StatusChip status={ag.status} /></div>
      <div className="modal-actions">
        <Button variant="danger-outline" onClick={onAlertar}>Alertar sobre este cliente</Button>
      </div>
    </Modal>
  )
}

/** Lista de todos os agendamentos do profissional, com abas (próximos, realizados, cancelados). */
function MeusAgendamentos({ onAbrir }) {
  const [aba, setAba] = useState('confirmado')
  const [limite, setLimite] = useState(POR_PAGINA)
  const { data, loading, error, reload } = useAsync(() => api.get('/agenda/agendamentos', { as: 'staff' }), [])

  const grupos = useMemo(() => {
    const g = { confirmado: [], finalizado: [], cancelado: [] }
    ;(data || []).forEach((a) => g[a.status]?.push(a))
    const cmp = (a, b) => (a.data + a.hora).localeCompare(b.data + b.hora)
    g.confirmado.sort(cmp) // do mais proximo ao mais distante
    g.finalizado.sort((a, b) => cmp(b, a)) // do mais recente ao mais antigo
    g.cancelado.sort((a, b) => cmp(b, a))
    return g
  }, [data])

  const lista = grupos[aba]
  const visiveis = lista.slice(0, limite)

  return (
    <section className="agenda-section" aria-labelledby="titulo-lista">
      <h2 id="titulo-lista">Meus agendamentos</h2>
      <div className="filter-tabs" role="group" aria-label="Período">
        {ABAS.map(([k, rotulo]) => (
          <button key={k} type="button" aria-pressed={aba === k} onClick={() => { setAba(k); setLimite(POR_PAGINA) }}>
            {rotulo} ({grupos[k].length})
          </button>
        ))}
      </div>

      {loading && !data && <Loading />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && lista.length === 0 && (
        <EmptyState icon={CalendarX}>
          {aba === 'confirmado' ? 'Nenhum agendamento futuro por enquanto.' : aba === 'finalizado' ? 'Nenhum atendimento realizado ainda.' : 'Nenhum agendamento cancelado.'}
        </EmptyState>
      )}
      {visiveis.length > 0 && (
        <div className="table-wrap">
          <table className="data stackable">
            <thead><tr><th>data e hora</th><th>cliente</th><th>serviço(s)</th><th>duração</th><th>com</th><th>status</th></tr></thead>
            <tbody>
              {visiveis.map((a) => (
                <tr key={a.id} className="clickable" tabIndex={0} onClick={() => onAbrir(a)}
                  onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onAbrir(a) } }}>
                  <td data-label="data e hora">{formatDateBR(a.data).slice(0, 5)} às {a.hora}</td>
                  <td data-label="cliente">{a.cliente}</td>
                  <td data-label="serviço(s)">{a.servicos}</td>
                  <td data-label="duração">{formatDuration(a.duracao)}</td>
                  <td data-label="com">{a.colegas.length ? a.colegas.join(', ') : '-'}</td>
                  <td data-label="status"><StatusChip status={a.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {lista.length > limite && (
        <div className="more-row"><Button small variant="ghost" onClick={() => setLimite((l) => l + POR_PAGINA)}>mostrar mais ({lista.length - limite})</Button></div>
      )}
    </section>
  )
}

export default function AgendaPage() {
  const { logout } = useAuth()
  const toast = useToast()
  useDocumentTitle('Minha agenda')
  const hoje = new Date()
  const [mes, setMes] = useState({ year: hoje.getFullYear(), month: hoje.getMonth() })
  const [dia, setDia] = useState(null)
  const [detalhe, setDetalhe] = useState(null)
  const [alerta, setAlerta] = useState(null)
  const [sair, setSair] = useState(false)

  const resumo = useAsync(() => api.get('/agenda/resumo', { as: 'staff' }), [])
  const { data, error, reload, setData } = useAsync(() => api.get('/agenda', { as: 'staff', query: { mes: monthKey(mes.year, mes.month) } }), [mes])

  const bloqueios = useMemo(() => Object.fromEntries((data?.bloqueios || []).map((b) => [b.data, b.motivo])), [data])
  const descansos = useMemo(() => new Set((data?.descansos || []).map((d) => `${d.data}|${d.hora}`)), [data])
  const horas = useMemo(() => {
    if (!data || !dia) return []
    const dow = weekdayOf(dia)
    const meu = data.profissional.horarios[dow]
    const salao = data.salaoHorarios[dow]
    if (!meu?.ativo || !salao?.ativo) return []
    const ini = Math.max(toMinutes(meu.inicio), toMinutes(salao.inicio))
    const fim = Math.min(toMinutes(meu.fim), toMinutes(salao.fim))
    const out = []
    for (let m = ini; m + 60 <= fim; m += 60) out.push(toHHMM(m))
    return out
  }, [data, dia])
  const folgas = useMemo(() => Object.fromEntries((data?.folgas || []).map((f) => [f.data, f.motivo])), [data])

  const statusDoSlot = (hora) => {
    const ini = toMinutes(hora)
    const ag = data.agendamentos.find((a) => a.data === dia && toMinutes(a.hora) < ini + 60 && toMinutes(a.hora) + a.duracao > ini)
    if (ag) return { tipo: 'agendado', ag }
    if (ini >= 12 * 60 && ini < 13 * 60) return { tipo: 'almoco' }
    if (descansos.has(`${dia}|${hora}`)) return { tipo: 'descanso' }
    return { tipo: 'livre' }
  }

  const alternarDescanso = async (hora, descanso) => {
    const antes = data
    setData((d) => ({ ...d, descansos: descanso ? [...d.descansos, { data: dia, hora }] : d.descansos.filter((x) => !(x.data === dia && x.hora === hora)) }))
    try { await api.put('/agenda/descansos', { data: dia, hora, descanso }, { as: 'staff' }) }
    catch (e) { setData(antes); toast.error(e.message) }
  }

  // o agendamento do calendario vem em outro formato: completa para o detalhe
  const abrirDoCalendario = (ag) => setDetalhe({ ...ag, status: 'confirmado' })

  if (resumo.loading && !resumo.data) return <Frame><Loading /></Frame>
  if (resumo.error) return <Frame><div className="screen-pad"><ErrorState error={resumo.error} onRetry={resumo.reload} /></div></Frame>

  const { profissional: perfil, salao, indicadores: ind } = resumo.data
  const podeEditar = !!data?.podeEditar
  const modoEdicao = podeEditar // so o administrador libera, em Configuracoes

  return (
    <ThemeScope themeId={salao.temaId}>
      <Frame>
        <header className="topbar-admin">
          <h1 className="topbar-title"><span>Minha agenda<span className="hide-sm">, {perfil.nome}</span></span></h1>
          <div className="topbar-right">
            <NotificationBell />
            <Button small variant="ghost" onClick={() => setSair(true)}><LogOut size={16} aria-hidden="true" />sair</Button>
          </div>
        </header>

        <main className="panel" id="conteudo">
          {/* perfil */}
          <section className="profile-card" aria-label="Meu perfil">
            <div className="avatar-lg" aria-hidden="true">{initials(perfil.nome)}</div>
            <div className="grow">
              <h2 className="profile-name">{perfil.nome}</h2>
              <div className="hint">funcionário &middot; {salao.nome}</div>
              <dl className="profile-facts">
                <div><dt>horário de atendimento</dt><dd>{resumoHorarios(perfil.horarios) || 'sem horário definido'}</dd></div>
                <div><dt>whatsapp</dt><dd>{perfil.whatsapp || 'não informado'}</dd></div>
                <div><dt>serviços que realizo</dt><dd><div className="chips">{perfil.servicos.length ? perfil.servicos.map((s) => <span className="chip" key={s}>{s}</span>) : 'nenhum serviço vinculado'}</div></dd></div>
              </dl>
            </div>
          </section>

          {/* indicadores */}
          <div className="stat-grid" aria-label="Resumo">
            <div className="stat"><span>atendimentos hoje</span><b>{ind.hoje}</b></div>
            <div className="stat"><span>próximos 7 dias</span><b>{ind.proximos7}</b><small>{plural(ind.proximos7, 'agendamento', 'agendamentos')}</small></div>
            <div className="stat"><span>concluídos no mês</span><b>{ind.concluidosMes}</b><small>{plural(ind.concluidosMes, 'atendimento', 'atendimentos')}</small></div>
            <div className="stat">
              <span>próximo atendimento</span>
              {ind.proximo
                ? <><b className="small">{formatLongDate(ind.proximo.data)} às {ind.proximo.hora}</b><small>{ind.proximo.cliente}, {ind.proximo.servicos}</small></>
                : <b className="small">nenhum agendado</b>}
            </div>
          </div>

          {/* agenda do dia */}
          <section className="agenda-section" aria-labelledby="titulo-dia">
            <h2 id="titulo-dia">Agenda do dia</h2>
            {data && AtalhoTeste && <Suspense fallback={null}><AtalhoTeste podeEditar={podeEditar} onAlterado={reload} /></Suspense>}
            {data && <p className="hint" role="status">
              {podeEditar
                ? 'O responsável liberou a edição: toque em um horário livre para marcar descanso, ou em um descanso para liberar.'
                : 'A edição de horários é liberada pelo responsável, em Configurações.'}
            </p>}
            {error && !data && <ErrorState error={error} onRetry={reload} />}
            {data && (
              <div className="cal-time-wrap">
                <Calendar
                  year={mes.year} month={mes.month} label="Escolha o dia" selected={dia}
                  onMonthChange={(d) => { setMes((m) => shiftMonth(m.year, m.month, d)); setDia(null) }}
                  onSelect={setDia}
                  getDay={(iso) => {
                    if (bloqueios[iso]) return { state: 'blocked', title: bloqueios[iso] }
                    if (folgas[iso]) return { state: 'blocked', title: `Folga: ${folgas[iso]}` }
                    const dow = weekdayOf(iso)
                    return data.profissional.horarios[dow]?.ativo && data.salaoHorarios[dow]?.ativo ? { state: 'ok' } : { state: 'off' }
                  }}
                  legend={
                    <div className="cal-legend" aria-hidden="true">
                      <span><i className="legend-dot" />dia de trabalho</span>
                      <span><i className="legend-dot off" />não trabalha</span>
                      <span><i className="legend-dot blocked" />salão fechado ou folga</span>
                    </div>
                  }
                />
                <div className="slots-col">
                  <div className="slots-title" aria-live="polite">{dia ? `Horários de ${formatLongDate(dia)}` : 'Selecione um dia de trabalho para ver os horários'}</div>
                  {dia && (
                    <>
                      <div className="slots">
                        {horas.map((h) => {
                          const st = statusDoSlot(h)
                          if (st.tipo === 'agendado') return <button key={h} type="button" className="slot booked" onClick={() => abrirDoCalendario(st.ag)}>{h}<small>agendado</small></button>
                          if (st.tipo === 'almoco') return <div key={h} className="slot rest static" aria-label={`${h}, almoço`}>{h}<small>almoço</small></div>
                          if (st.tipo === 'descanso') {
                            return modoEdicao
                              ? <button key={h} type="button" className="slot rest" onClick={() => alternarDescanso(h, false)} aria-label={`${h}, descanso. Toque para liberar`}>{h}<small>descanso</small></button>
                              : <div key={h} className="slot rest static">{h}<small>descanso</small></div>
                          }
                          return modoEdicao
                            ? <button key={h} type="button" className="slot" onClick={() => alternarDescanso(h, true)} aria-label={`${h}, livre. Toque para marcar descanso`}>{h}<small>livre</small></button>
                            : <div key={h} className="slot static">{h}<small>livre</small></div>
                        })}
                      </div>
                      <div className="cal-legend" aria-hidden="true" style={{ padding: '12px 0 0', border: 0 }}>
                        <span><i className="legend-dot" />livre</span>
                        <span><i className="legend-dot busy" />agendado</span>
                        <span><i className="legend-dot off" />descanso</span>
                      </div>
                    </>
                  )}
                </div>
              </div>
            )}
          </section>

          {/* lista completa */}
          <MeusAgendamentos onAbrir={setDetalhe} />
        </main>

        {detalhe && <DetalheModal ag={detalhe} onClose={() => setDetalhe(null)} onAlertar={() => { setAlerta(detalhe); setDetalhe(null) }} />}
        {alerta && <AlertaModal agendamento={alerta} onClose={() => setAlerta(null)} />}
        {sair && <ConfirmDialog title="Sair da conta?" message="Tem certeza que deseja sair da sua conta?" confirmLabel="Sair" onClose={() => setSair(false)} onConfirm={() => logout()} />}
      </Frame>
    </ThemeScope>
  )
}
