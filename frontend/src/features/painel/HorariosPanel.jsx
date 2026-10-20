import { useEffect, useMemo, useState } from 'react'
import { api } from '../../lib/http'
import { useAsync } from '../../lib/hooks'
import { MESES, formatDateBR, monthKey, resumoHorarios, shiftMonth, todayISO, weekdayOf } from '../../lib/dates'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import Calendar from '../../components/ui/Calendar'
import Button from '../../components/ui/Button'
import Toggle from '../../components/ui/Toggle'
import { Field } from '../../components/ui/Field'
import { Modal } from '../../components/ui/Modal'
import { ErrorState, Loading } from '../../components/ui/States'

const ORDEM = [1, 2, 3, 4, 5, 6, 0] // segunda a domingo
const NOME_DIA = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado']

/** Fechar um dia do salao inteiro ou marcar a folga de um profissional num dia. */
function DiaModal({ titulo, data, atual, qtdAgendamentos, rotuloSalvar, onClose, onSalvar, onRemover }) {
  const [motivo, setMotivo] = useState(atual?.motivo || '')
  const [busy, setBusy] = useState(false)
  const run = (fn) => async () => { setBusy(true); try { await fn() } finally { setBusy(false) } }
  return (
    <Modal title={titulo} onClose={onClose}>
      <p className="hint" style={{ marginTop: -8 }}>{formatDateBR(data)}</p>
      <Field label="motivo"><textarea rows={3} placeholder="ex: feriado municipal, consulta médica" value={motivo} onChange={(e) => setMotivo(e.target.value)} /></Field>
      {qtdAgendamentos > 0 && (
        <div className="box warn" role="status" style={{ marginTop: 0 }}>
          Já existe{qtdAgendamentos > 1 ? 'm' : ''} <b>{qtdAgendamentos}</b> agendamento{qtdAgendamentos > 1 ? 's' : ''} confirmado{qtdAgendamentos > 1 ? 's' : ''} neste dia. Eles não são cancelados automaticamente: avise os clientes.
        </div>
      )}
      <div className="modal-actions">
        {atual && <Button variant="danger-outline" loading={busy} onClick={run(onRemover)}>Remover</Button>}
        <Button variant="ghost" onClick={onClose}>Cancelar</Button>
        <Button loading={busy} onClick={run(() => onSalvar(motivo))}>{rotuloSalvar}</Button>
      </div>
    </Modal>
  )
}

/**
 * Calendario e horarios (aba da pagina Agendamentos):
 *  - escolhe de quem e a agenda: salao inteiro, cada profissional, ou o proprio responsavel (se ele atende)
 *  - edita o horario semanal (dia a dia) e as excecoes por data (fechar o salao / folga do profissional)
 */
export default function HorariosPanel({ agendamentos }) {
  const { usuario } = useAuth()
  const toast = useToast()
  const hoje = new Date()
  const horarios = useAsync(() => api.get('/painel/horarios', { as: 'staff' }), [])
  const [alvo, setAlvo] = useState('salao') // 'salao' | id do profissional
  const [mes, setMes] = useState({ year: hoje.getFullYear(), month: hoje.getMonth() })
  const [rascunho, setRascunho] = useState(null)
  const [avisos, setAvisos] = useState([])
  const [salvando, setSalvando] = useState(false)
  const [modalDia, setModalDia] = useState(null)

  const agenda = horarios.data && (alvo === 'salao' ? horarios.data.salao : horarios.data.profissionais.find((p) => p.id === alvo))
  useEffect(() => { if (agenda) setRascunho(structuredClone(agenda.horarios)) }, [agenda])
  useEffect(() => { if (horarios.data) setAvisos(horarios.data.avisos || []) }, [horarios.data])

  const chaveMes = monthKey(mes.year, mes.month)
  const excecoes = useAsync(() => (alvo === 'salao'
    ? api.get('/painel/bloqueios', { as: 'staff', query: { mes: chaveMes } })
    : api.get('/painel/folgas', { as: 'staff', query: { mes: chaveMes, profissionalId: alvo } })), [alvo, chaveMes])
  const bloqueiosSalao = useAsync(() => api.get('/painel/bloqueios', { as: 'staff', query: { mes: chaveMes } }), [chaveMes])
  const porData = useMemo(() => Object.fromEntries((excecoes.data || []).map((e) => [e.data, e])), [excecoes.data])
  const salaoPorData = useMemo(() => Object.fromEntries((bloqueiosSalao.data || []).map((e) => [e.data, e])), [bloqueiosSalao.data])

  if (horarios.loading && !horarios.data) return <Loading />
  if (horarios.error) return <ErrorState error={horarios.error} onRetry={horarios.reload} />
  if (!rascunho || !agenda) return <Loading />

  const ehSalao = alvo === 'salao'
  const nomeAlvo = ehSalao ? 'Salão' : agenda.nome
  const sujo = JSON.stringify(rascunho) !== JSON.stringify(agenda.horarios)
  const invalido = ORDEM.some((d) => rascunho[d].ativo && rascunho[d].inicio >= rascunho[d].fim)
  const salaoHorarios = horarios.data.salao.horarios

  const mudar = (d, campo, valor) => setRascunho((r) => ({ ...r, [d]: { ...r[d], [campo]: valor } }))
  const copiarSegunda = () => setRascunho((r) => Object.fromEntries(Object.entries(r).map(([d, h]) => [d, h.ativo ? { ...h, inicio: r[1].inicio, fim: r[1].fim } : h])))

  const salvar = async () => {
    setSalvando(true)
    try {
      const url = ehSalao ? '/painel/horarios/salao' : `/painel/horarios/profissionais/${alvo}`
      const r = await api.put(url, { horarios: rascunho }, { as: 'staff' })
      setAvisos(r.avisos || [])
      toast.success(ehSalao ? 'Horário de funcionamento salvo.' : `Horário de ${agenda.nome} salvo.`)
      horarios.reload()
    } catch (e) { toast.error(e.message) } finally { setSalvando(false) }
  }

  const contarAgendamentos = (iso) => (agendamentos || []).filter((a) => a.data === iso && a.status === 'confirmado' && (ehSalao || a.profissionalIds?.includes(alvo))).length

  const getDay = (iso) => {
    if (iso < todayISO()) return { state: 'disabled' }
    const dow = weekdayOf(iso)
    if (porData[iso]) return { state: 'blocked', title: porData[iso].motivo, clickable: true }
    if (!ehSalao) {
      if (salaoPorData[iso]) return { state: 'disabled', title: `Salão fechado: ${salaoPorData[iso].motivo}` }
      if (!salaoHorarios[dow].ativo) return { state: 'off', title: 'Salão fechado neste dia da semana' }
    }
    return rascunho[dow].ativo ? { state: 'ok' } : { state: 'off', title: ehSalao ? 'Salão fechado neste dia da semana' : 'Não trabalha neste dia da semana' }
  }

  const salvarDia = async (motivo) => {
    try {
      const url = ehSalao ? `/painel/bloqueios/${modalDia}` : `/painel/folgas/${alvo}/${modalDia}`
      await api.put(url, { motivo }, { as: 'staff' })
      toast.success(ehSalao ? 'Dia fechado. Ele aparece em vermelho para os clientes.' : `Folga de ${agenda.nome} registrada.`)
      setModalDia(null); excecoes.reload(); bloqueiosSalao.reload()
    } catch (e) { toast.error(e.message) }
  }
  const removerDia = async () => {
    try {
      const url = ehSalao ? `/painel/bloqueios/${modalDia}` : `/painel/folgas/${alvo}/${modalDia}`
      await api.delete(url, { as: 'staff' })
      toast.success(ehSalao ? 'Bloqueio removido.' : 'Folga removida.')
      setModalDia(null); excecoes.reload(); bloqueiosSalao.reload()
    } catch (e) { toast.error(e.message) }
  }

  return (
    <div>
      <Field label="agenda que você quer editar" hint={ehSalao ? 'Horário de funcionamento do salão: vale como limite para toda a equipe.' : `Horário de trabalho de ${agenda.nome}: fica limitado ao funcionamento do salão (${resumoHorarios(salaoHorarios)}).`}>
        <select value={alvo} onChange={(e) => { setAlvo(e.target.value); setAvisos(horarios.data.avisos || []) }}>
          <option value="salao">Salão inteiro (horário de funcionamento)</option>
          {horarios.data.profissionais.map((p) => (
            <option key={p.id} value={p.id}>{p.nome}{p.id === usuario?.profissionalId ? ' (você)' : ''}{p.ehAdmin && p.id !== usuario?.profissionalId ? ' (administrador)' : ''}</option>
          ))}
        </select>
      </Field>

      {usuario?.profissionalId && !horarios.data.profissionais.some((p) => p.id === usuario.profissionalId) && (
        <p className="hint" role="status">Sua agenda não aparece na lista porque você está marcado como quem não atende clientes. Para incluí-la, marque "atende clientes" em Profissionais.</p>
      )}

      <div className="horarios-grid">
        <section aria-labelledby="h-cal">
          <h2 className="subhead" id="h-cal">{ehSalao ? 'Fechar dias do salão' : `Folgas de ${agenda.nome}`}</h2>
          <p className="hint">Toque em um dia para {ehSalao ? 'fechar o salão inteiro (feriados, folgas coletivas)' : 'registrar uma folga'}. Dias em vermelho já têm {ehSalao ? 'bloqueio' : 'folga'}.</p>
          <Calendar
            year={mes.year} month={mes.month} label={`Calendário de ${nomeAlvo}`}
            onMonthChange={(d) => setMes((m) => shiftMonth(m.year, m.month, d))}
            canGoPrev={mes.year > hoje.getFullYear() || mes.month > hoje.getMonth()}
            onSelect={setModalDia} getDay={getDay}
            legend={
              <div className="cal-legend" aria-hidden="true">
                <span><i className="legend-dot" />atende</span>
                <span><i className="legend-dot off" />não atende</span>
                <span><i className="legend-dot blocked" />{ehSalao ? 'bloqueado' : 'folga'}</span>
              </div>
            }
          />
          {(excecoes.data || []).length > 0 && (
            <ul className="hint" style={{ paddingLeft: 18 }}>
              {excecoes.data.map((e) => <li key={e.data}>{formatDateBR(e.data).slice(0, 5)} de {MESES[mes.month]}: {e.motivo}</li>)}
            </ul>
          )}
        </section>

        <section aria-labelledby="h-sem">
          <h2 className="subhead" id="h-sem">Horário semanal{ehSalao ? ' de funcionamento' : ` de ${agenda.nome}`}</h2>
          <div className="week-editor">
            {ORDEM.map((d) => {
              const h = rascunho[d]
              const erro = h.ativo && h.inicio >= h.fim
              return (
                <div className="week-row" key={d}>
                  <Toggle checked={h.ativo} onChange={(v) => mudar(d, 'ativo', v)} label={`${NOME_DIA[d]}: ${h.ativo ? 'atende' : 'não atende'}`} />
                  <span className="week-day">{NOME_DIA[d]}</span>
                  {h.ativo ? (
                    <span className="week-times">
                      <input type="time" aria-label={`${NOME_DIA[d]}, início`} value={h.inicio} onChange={(e) => mudar(d, 'inicio', e.target.value)} aria-invalid={erro || undefined} />
                      <span>às</span>
                      <input type="time" aria-label={`${NOME_DIA[d]}, término`} value={h.fim} onChange={(e) => mudar(d, 'fim', e.target.value)} aria-invalid={erro || undefined} />
                    </span>
                  ) : <span className="week-off">{ehSalao ? 'fechado' : 'folga semanal'}</span>}
                  {erro && <span className="field-error week-err">término antes do início</span>}
                </div>
              )
            })}
          </div>
          <div className="row" style={{ marginTop: 14 }}>
            <Button small variant="ghost" onClick={copiarSegunda}>Copiar horário de segunda para os outros dias</Button>
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <Button loading={salvando} disabled={!sujo || invalido} onClick={salvar}>Salvar horários</Button>
            {sujo && <Button variant="ghost" onClick={() => setRascunho(structuredClone(agenda.horarios))}>Descartar alterações</Button>}
            {sujo && <span className="hint" role="status">Alterações ainda não salvas.</span>}
          </div>
          {avisos.length > 0 && (
            <div className="box warn" role="status">
              Atenção aos horários:
              <ul>{avisos.map((a) => <li key={a}>{a}</li>)}</ul>
            </div>
          )}
        </section>
      </div>

      {modalDia && (
        <DiaModal
          titulo={ehSalao ? 'Fechar o salão neste dia' : `Folga de ${agenda.nome}`}
          data={modalDia} atual={porData[modalDia]} qtdAgendamentos={contarAgendamentos(modalDia)}
          rotuloSalvar={ehSalao ? 'Salvar bloqueio' : 'Salvar folga'} onClose={() => setModalDia(null)}
          onSalvar={salvarDia} onRemover={removerDia}
        />
      )}
    </div>
  )
}
