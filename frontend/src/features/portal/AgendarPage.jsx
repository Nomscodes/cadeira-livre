import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Check } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync, useDocumentTitle } from '../../lib/hooks'
import { MESES, formatDuration, formatLongDate, shiftMonth, monthKey } from '../../lib/dates'
import { initials, money } from '../../lib/format'
import { useToast } from '../../context/ToastContext'
import { usePortal } from '../../components/layout/PortalLayout'
import Calendar from '../../components/ui/Calendar'
import Button from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import ConfirmarAgendamentoModal from './ConfirmarAgendamentoModal'

const VAZIO = { itens: [], atribuicoes: {}, data: null, hora: null }

export default function AgendarPage() {
  const { salao, base } = usePortal()
  useDocumentTitle(`Agendar em ${salao.nome}`)
  const toast = useToast()
  const navigate = useNavigate()
  const [params] = useSearchParams()

  const catalogo = useMemo(() => [
    ...salao.servicos.map((s) => ({ ...s, tipo: 'servico' })),
    ...salao.combos.map((c) => ({ ...c, tipo: 'combo' })),
  ], [salao])
  const item = (id) => catalogo.find((c) => c.id === id)
  const profissional = (id) => salao.profissionais.find((p) => p.id === id)
  const aptos = (id) => (item(id)?.profissionalIds || []).map(profissional).filter(Boolean)

  const hoje = new Date()
  const [mes, setMes] = useState({ year: hoje.getFullYear(), month: hoje.getMonth() })
  const [s, setS] = useState(() => {
    const pre = params.get('servico')
    const it = catalogo.find((c) => c.id === pre)
    if (!it) return VAZIO
    const unico = it.profissionalIds.length === 1 ? { [it.id]: it.profissionalIds[0] } : {}
    return { ...VAZIO, itens: [it.id], atribuicoes: unico }
  })
  const [confirmando, setConfirmando] = useState(false)
  const [enviado, setEnviado] = useState(null)

  /* ---------- derivados ---------- */
  const coberto = s.itens.length > 0 && s.itens.every((id) => s.atribuicoes[id])
  const profIds = [...new Set(s.itens.map((id) => s.atribuicoes[id]).filter(Boolean))]
  const escolhidos = s.itens.map(item).filter(Boolean)
  const duracao = escolhidos.reduce((t, i) => t + i.duracao, 0)
  const valor = escolhidos.reduce((t, i) => t + i.preco, 0)
  const faltam = escolhidos.filter((i) => !s.atribuicoes[i.id])
  const chave = `${profIds.join(',')}|${duracao}|${monthKey(mes.year, mes.month)}`

  const dias = useAsync(
    () => (coberto ? api.get(`/saloes/${salao.slug}/disponibilidade/dias`, { query: { mes: monthKey(mes.year, mes.month), profissionais: profIds.join(','), duracao } }) : []),
    [coberto, chave, salao.slug],
  )
  const horarios = useAsync(
    () => (coberto && s.data ? api.get(`/saloes/${salao.slug}/disponibilidade/horarios`, { query: { data: s.data, profissionais: profIds.join(','), duracao } }) : []),
    [coberto, s.data, chave, salao.slug],
  )
  const diaInfo = useMemo(() => Object.fromEntries((dias.data || []).map((d) => [d.data, d])), [dias.data])

  /* ---------- acoes ---------- */
  const reset = (extra) => ({ data: null, hora: null, ...extra })
  const alternar = (id) => setS((cur) => {
    if (cur.itens.includes(id)) {
      const { [id]: _, ...resto } = cur.atribuicoes
      return { ...cur, ...reset({ itens: cur.itens.filter((x) => x !== id), atribuicoes: resto }) }
    }
    const unico = aptos(id).length === 1 ? { [id]: aptos(id)[0].id } : {}
    return { ...cur, ...reset({ itens: [...cur.itens, id], atribuicoes: { ...cur.atribuicoes, ...unico } }) }
  })
  const atribuir = (id, profId) => setS((cur) => ({ ...cur, ...reset({ atribuicoes: { ...cur.atribuicoes, [id]: profId } }) }))
  const mudarMes = (d) => { setMes((m) => shiftMonth(m.year, m.month, d)); setS((cur) => ({ ...cur, ...reset() })) }
  const pronto = coberto && s.data && s.hora

  const resumo = pronto && {
    profissionais: profIds.map((id) => profissional(id)?.nome).join(', '),
    servicos: escolhidos.map((i) => i.nome).join(', '),
    quando: `${formatLongDate(s.data)} às ${s.hora}`,
    tempo: formatDuration(duracao),
    valor: money(valor),
  }

  const enviar = async (cliente) => {
    try {
      const itens = s.itens.map((id) => ({ [item(id).tipo === 'combo' ? 'comboId' : 'servicoId']: id, profissionalId: s.atribuicoes[id] }))
      const r = await api.post(`/saloes/${salao.slug}/agendamentos`, { itens, data: s.data, hora: s.hora, ...cliente }, { as: 'cliente' })
      setConfirmando(false)
      setEnviado(r)
    } catch (err) {
      if (err.status === 409) { setConfirmando(false); setS((cur) => ({ ...cur, hora: null })); horarios.reload() }
      toast.error(err.message)
    }
  }

  const bloqueados = (dias.data || []).filter((d) => d.estado === 'bloqueado')
  const semHorario = s.data && !horarios.loading && horarios.data && !horarios.data.some((h) => h.livre)

  return (
    <>
      <header className="wizard-top">
        <Link to={base}><span aria-hidden="true">&larr;</span> voltar ao portal</Link>
      </header>
      <h1 className="sr-only">Agendar horário em {salao.nome}</h1>

      <div>
        {/* 1. servicos */}
        <section className="step" aria-labelledby="st1">
          <div className="step-head"><span className="step-num" aria-hidden="true">1</span><h2 className="step-title" id="st1">Selecione o(s) serviço(s)</h2><span className="step-hint">seleção múltipla</span></div>
          {catalogo.length === 0 ? <p className="muted">Este salão ainda não cadastrou serviços.</p> : (
            <div className="pick-grid">
              {catalogo.map((c) => {
                const on = s.itens.includes(c.id)
                return (
                  <button key={c.id} type="button" className="pick-card" aria-pressed={on} onClick={() => alternar(c.id)}>
                    <span className="chk" aria-hidden="true"><Check size={14} /></span>
                    <div className="nome">{c.nome}</div>
                    <div className="meta">{formatDuration(c.duracao)} · {money(c.preco)}</div>
                  </button>
                )
              })}
            </div>
          )}
        </section>

        {/* 2. profissionais */}
        <section className="step" aria-labelledby="st2" inert={s.itens.length === 0} style={{ opacity: s.itens.length === 0 ? 0.42 : 1 }}>
          <div className="step-head"><span className="step-num" aria-hidden="true">2</span><h2 className="step-title" id="st2">Escolha um profissional para cada serviço</h2><span className="step-hint">só aparecem os que atendem aquele serviço</span></div>
          {escolhidos.map((i) => (
            <div className="sag-group" key={i.id}>
              <div className="sag-title">Serviço: {i.nome}{!s.atribuicoes[i.id] && <span className="warn">selecione um profissional</span>}</div>
              {aptos(i.id).length === 0 ? <p className="muted">Nenhum profissional disponível para este serviço.</p> : (
                <div className="pick-grid">
                  {aptos(i.id).map((p) => (
                    <button key={p.id} type="button" className="pick-card prof" aria-pressed={s.atribuicoes[i.id] === p.id} onClick={() => atribuir(i.id, p.id)}>
                      <span className="chk" aria-hidden="true"><Check size={14} /></span>
                      <div className="prof-avatar" aria-hidden="true">{initials(p.nome)}</div>
                      <div className="nome" style={{ padding: 0 }}>{p.nome}</div>
                      <div className="prof-specs">
                        {salao.servicos.filter((x) => p.servicoIds.includes(x.id)).map((x) => <span key={x.id} className={`prof-chip${x.id === i.id ? ' match' : ''}`}>{x.nome}</span>)}
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}
          {faltam.length > 0 && s.itens.length > 0 && (
            <div className="coverage-warning" role="status">Ainda falta escolher um profissional para: {faltam.map((i) => i.nome).join(', ')}</div>
          )}
        </section>

        {/* 3. resumo */}
        <section className="step" aria-labelledby="st3" inert={!coberto} style={{ opacity: coberto ? 1 : 0.42 }}>
          <div className="step-head"><span className="step-num" aria-hidden="true">3</span><h2 className="step-title" id="st3">Resumo do atendimento</h2></div>
          <div className="resumo-box">
            <div className="col"><span>profissional(is)</span><b>{coberto ? profIds.map((id) => profissional(id)?.nome).join(', ') : '—'}</b></div>
            <div className="col"><span>serviço(s)</span><b>{coberto ? escolhidos.map((i) => i.nome).join(', ') : '—'}</b></div>
            <div className="col"><span>tempo médio total</span><b>{coberto ? formatDuration(duracao) : '—'}</b></div>
            <div className="col"><span>valor total</span><b>{coberto ? money(valor) : '—'}</b></div>
          </div>
          <div className="assignment-list">
            <div className="hint" style={{ marginBottom: 6 }}>detalhamento serviço &rarr; profissional</div>
            {coberto && escolhidos.map((i) => <div className="assignment-row" key={i.id}><span>{i.nome}</span><b>{profissional(s.atribuicoes[i.id])?.nome}</b></div>)}
          </div>
        </section>

        {/* 4. data e hora */}
        <section className="step" aria-labelledby="st4" inert={!coberto} style={{ opacity: coberto ? 1 : 0.42, borderBottom: 0 }}>
          <div className="step-head"><span className="step-num" aria-hidden="true">4</span><h2 className="step-title" id="st4">Escolha data e horário</h2><span className="step-hint">bloqueio geral do salão aparece em vermelho</span></div>
          <div className="cal-time-wrap">
            <div>
              <Calendar
                year={mes.year} month={mes.month} onMonthChange={mudarMes} label="Escolha o dia"
                canGoPrev={mes.year > hoje.getFullYear() || mes.month > hoje.getMonth()}
                selected={s.data}
                onSelect={(iso) => setS((cur) => ({ ...cur, data: iso, hora: null }))}
                getDay={(iso) => {
                  const d = diaInfo[iso]
                  if (!d || dias.loading) return { state: 'disabled' }
                  if (d.estado === 'ok') return { state: 'ok' }
                  if (d.estado === 'bloqueado') return { state: 'blocked', title: d.motivo }
                  if (d.estado === 'folga') return { state: 'off', title: 'Profissional não atende neste dia' }
                  if (d.estado === 'fechado') return { state: 'off', title: 'Salão fechado neste dia da semana' }
                  return { state: 'disabled', title: d.estado === 'lotado' ? 'Sem horários livres' : undefined }
                }}
                legend={
                  <div className="cal-legend" aria-hidden="true">
                    <span><i className="legend-dot" />disponível</span>
                    <span><i className="legend-dot blocked" />salão fechado</span>
                    <span><i className="legend-dot off" />sem atendimento</span>
                  </div>
                }
              />
              {bloqueados.length > 0 && (
                <div className="box info" style={{ maxWidth: 360 }}>
                  {bloqueados.map((d) => <div key={d.data}>Salão fechado em {Number(d.data.slice(8))} de {MESES[mes.month]}: {d.motivo}</div>)}
                </div>
              )}
            </div>
            <div className="slots-col">
              <div className="slots-title" aria-live="polite">{s.data ? `Horários disponíveis em ${formatLongDate(s.data)}` : 'Selecione uma data'}</div>
              {horarios.loading && s.data && <div className="hint">Buscando horários...</div>}
              {semHorario && <div className="box warn" style={{ marginTop: 0 }}>Sem horários livres neste dia. Escolha outra data.</div>}
              <div className="slots">
                {(horarios.data || []).map((h) => (
                  <button
                    key={h.hora} type="button" disabled={!h.livre}
                    className={`slot${h.livre ? '' : ' busy'}`} aria-pressed={s.hora === h.hora}
                    aria-label={h.livre ? h.hora : `${h.hora}, indisponível`}
                    onClick={() => setS((cur) => ({ ...cur, hora: h.hora }))}
                  >{h.hora}</button>
                ))}
              </div>
            </div>
          </div>
        </section>

        <div className="confirm-bar">
          <span className="hint" role="status">
            {pronto ? `${formatLongDate(s.data)} às ${s.hora}` : 'Escolha serviço, profissional, data e horário para continuar.'}
          </span>
          <Button disabled={!pronto} onClick={() => setConfirmando(true)}>Confirmar informações</Button>
        </div>
      </div>

      {confirmando && (
        <ConfirmarAgendamentoModal
          resumo={resumo} horasCancelamento={salao.cancelamentoHoras}
          onClose={() => setConfirmando(false)}
          onDescartar={() => { setConfirmando(false); setS(VAZIO); toast.info('Agendamento descartado antes do envio.') }}
          onConfirmar={enviar}
        />
      )}

      {enviado && (
        <Modal title="Agendamento enviado" onClose={() => { setEnviado(null); setS(VAZIO) }}>
          <p className="hint" style={{ marginTop: -6 }}>Endereço do estabelecimento</p>
          <p><b>{enviado.salao.endereco || salao.nome}</b></p>
          <div className="box ok">Em breve você receberá uma mensagem de confirmação pelo WhatsApp.</div>
          <div className="modal-actions">
            {enviado.salao.whatsapp && <a className="btn ghost" href={`https://wa.me/55${enviado.salao.whatsapp.replace(/\D/g, '')}`} target="_blank" rel="noopener noreferrer">Fale conosco</a>}
            <Button onClick={() => navigate('/meus-agendamentos')}>Ver meus agendamentos</Button>
          </div>
        </Modal>
      )}
    </>
  )
}
