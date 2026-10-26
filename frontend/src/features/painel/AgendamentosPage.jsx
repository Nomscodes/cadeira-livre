import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ClipboardList } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync } from '../../lib/hooks'
import { formatDateBR } from '../../lib/dates'
import { digits, money } from '../../lib/format'
import { Modal } from '../../components/ui/Modal'
import StatusChip from '../../components/ui/StatusChip'
import { EmptyState, ErrorState, Loading } from '../../components/ui/States'
import HorariosPanel from './HorariosPanel'

const FILTROS = [['confirmado', 'confirmados'], ['finalizado', 'finalizados'], ['cancelado', 'cancelados']]

function Detalhe({ a, onClose }) {
  const msg = (nome) => `Olá ${nome}, o cliente ${a.cliente} marcou um horário em ${formatDateBR(a.data)} às ${a.hora} para ${a.servicos}.`
  return (
    <Modal title="Detalhe do agendamento" onClose={onClose}>
      <div className="modal-row"><span>cliente</span><b>{a.cliente}</b></div>
      <div className="modal-row"><span>whatsapp</span><b>{a.whatsapp}</b></div>
      <div className="modal-row"><span>serviço(s)</span><b>{a.servicos}</b></div>
      <div className="modal-row"><span>profissional(is)</span><b>{a.profissionais}</b></div>
      <div className="modal-row"><span>data e hora</span><b>{formatDateBR(a.data)} às {a.hora}</b></div>
      <div className="modal-row"><span>valor</span><b>{money(a.valor)}</b></div>
      <div className="modal-row"><span>status</span><StatusChip status={a.status} /></div>
      <div className="modal-actions" style={{ flexDirection: 'column' }}>
        {a.encaminhar.length === 0 && <p className="hint" style={{ margin: 0 }}>Nenhum dos profissionais deste atendimento está com o encaminhamento por WhatsApp ativado.</p>}
        {a.encaminhar.map((p) => (
          <a key={p.nome} className="btn" target="_blank" rel="noopener noreferrer"
            href={`https://wa.me/55${digits(p.whatsapp)}?text=${encodeURIComponent(msg(p.nome))}`}>
            Encaminhar para o WhatsApp de {p.nome}
          </a>
        ))}
      </div>
    </Modal>
  )
}

function Lista({ data }) {
  const [ativos, setAtivos] = useState(new Set(['confirmado', 'finalizado', 'cancelado']))
  const [aberto, setAberto] = useState(null)
  // proximos atendimentos primeiro (do mais proximo ao mais distante); depois finalizados e cancelados, do mais recente ao mais antigo
  const ordem = { confirmado: 0, finalizado: 1, cancelado: 2 }
  const itens = data.filter((a) => ativos.has(a.status)).sort((a, b) => {
    if (a.status !== b.status) return ordem[a.status] - ordem[b.status]
    const cmp = (a.data + a.hora).localeCompare(b.data + b.hora)
    return a.status === 'confirmado' ? cmp : -cmp
  })
  const alternar = (k) => setAtivos((s) => { const n = new Set(s); if (n.has(k)) n.delete(k); else n.add(k); return n })

  return (
    <div>
      <p className="hint">Clique para ligar ou desligar cada categoria. Mais de uma pode ficar visível ao mesmo tempo.</p>
      <div className="filter-tabs" role="group" aria-label="Filtrar por status">
        {FILTROS.map(([k, l]) => <button key={k} type="button" aria-pressed={ativos.has(k)} onClick={() => alternar(k)}>{l}</button>)}
      </div>
      {itens.length === 0 && <EmptyState icon={ClipboardList}>Nenhuma categoria selecionada, ou nenhum agendamento nela.</EmptyState>}
      {itens.length > 0 && (
        <div className="table-wrap">
          <table className="data stackable">
            <thead><tr><th>cliente</th><th>serviço(s)</th><th>profissional(is)</th><th>data e hora</th><th>valor</th><th>status</th></tr></thead>
            <tbody>
              {itens.map((a) => (
                <tr key={a.id} className="clickable" tabIndex={0} onClick={() => setAberto(a)}
                  onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setAberto(a) } }}>
                  <td data-label="cliente">{a.cliente}</td>
                  <td data-label="serviço(s)">{a.servicos}</td>
                  <td data-label="profissional(is)">{a.profissionais}</td>
                  <td data-label="data e hora">{formatDateBR(a.data).slice(0, 5)} às {a.hora}</td>
                  <td data-label="valor">{money(a.valor)}</td>
                  <td data-label="status"><StatusChip status={a.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="hint" style={{ marginTop: 18 }}>O Cadeira Livre não é responsável por transações financeiras entre cliente e salão. O sistema não processa pagamentos.</p>
      {aberto && <Detalhe a={aberto} onClose={() => setAberto(null)} />}
    </div>
  )
}

export default function AgendamentosPage() {
  const [params, setParams] = useSearchParams()
  const aba = params.get('aba') === 'horarios' ? 'horarios' : 'lista'
  const { data, loading, error, reload } = useAsync(() => api.get('/painel/agendamentos', { as: 'staff' }), [])
  const trocar = (a) => setParams(a === 'horarios' ? { aba: 'horarios' } : {}, { replace: true })

  return (
    <div>
      <div className="tabs2" role="tablist" aria-label="Agendamentos">
        <button type="button" role="tab" aria-selected={aba === 'lista'} onClick={() => trocar('lista')}>Agendamentos</button>
        <button type="button" role="tab" aria-selected={aba === 'horarios'} onClick={() => trocar('horarios')}>Calendário e horários</button>
      </div>
      {loading && !data && <Loading />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && (aba === 'lista' ? <Lista data={data} /> : <HorariosPanel agendamentos={data} />)}
    </div>
  )
}
