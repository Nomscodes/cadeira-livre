import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CalendarX } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync, useDocumentTitle } from '../../lib/hooks'
import { formatDateTimeBR } from '../../lib/dates'
import { money } from '../../lib/format'
import { useToast } from '../../context/ToastContext'
import StatusChip from '../../components/ui/StatusChip'
import Button from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/Modal'
import { EmptyState, ErrorState, Loading } from '../../components/ui/States'

const ORDEM = ['confirmado', 'finalizado', 'cancelado']
const CHIP = { confirmado: 'embreve', finalizado: 'concluido', cancelado: 'cancelado' }

export default function HistoricoPage() {
  useDocumentTitle('Meus agendamentos')
  const toast = useToast()
  const [ocultar, setOcultar] = useState(false)
  const [cancelar, setCancelar] = useState(null)
  const { data, loading, error, reload } = useAsync(() => api.get('/cliente/agendamentos', { as: 'cliente' }), [])

  const itens = (data || [])
    .filter((a) => !(ocultar && a.status === 'cancelado'))
    .sort((a, b) => ORDEM.indexOf(a.status) - ORDEM.indexOf(b.status) || (b.data + b.hora).localeCompare(a.data + a.hora))

  const confirmarCancelamento = async () => {
    try {
      await api.post(`/cliente/agendamentos/${cancelar.id}/cancelar`, {}, { as: 'cliente' })
      toast.success('Agendamento cancelado.')
      setCancelar(null)
      reload()
    } catch (e) {
      toast.error(e.message)
      setCancelar(null)
    }
  }

  return (
    <div className="screen-pad">
      <h1 className="sr-only">Meus agendamentos</h1>
      <label className="check-row" style={{ marginBottom: 16 }}>
        <input type="checkbox" checked={ocultar} onChange={(e) => setOcultar(e.target.checked)} /> ocultar agendamentos cancelados
      </label>

      {loading && !data && <Loading />}
      {error && <ErrorState error={error} onRetry={reload} />}
      {data && itens.length === 0 && <EmptyState icon={CalendarX} action={<Link to="/buscar" className="btn small">Buscar salões</Link>}>Nenhum agendamento encontrado.</EmptyState>}

      <div className="grid-cards" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(min(100%, 290px), 1fr))' }}>
        {itens.map((a) => (
          <article className="card-box" key={a.id}>
            <div className="hist-head">
              <h2>{a.servicos}</h2>
              <StatusChip status={CHIP[a.status]} />
            </div>
            <div className="meta" style={{ marginBottom: 0 }}>
              {a.profissionais} · {a.salao.nome}<br />
              {formatDateTimeBR(a.data, a.hora)} · {money(a.valor)}
            </div>
            <div className="hist-actions">
              <Link to={`/${a.salao.slug}`} className="btn small ghost">ver salão</Link>
              {a.status === 'finalizado' && !a.avaliado && <Link to={`/avaliar/${a.id}`} className="btn small">avaliar</Link>}
              {a.status === 'confirmado' && (a.podeCancelar
                ? <Button small variant="danger-outline" onClick={() => setCancelar(a)}>cancelar</Button>
                : <span className="hint">Prazo de cancelamento encerrado ({a.cancelamentoHoras} h antes)</span>)}
            </div>
          </article>
        ))}
      </div>

      {cancelar && (
        <ConfirmDialog
          title="Cancelar agendamento?" danger confirmLabel="Cancelar agendamento"
          message={`${cancelar.servicos} em ${cancelar.salao.nome}, ${formatDateTimeBR(cancelar.data, cancelar.hora)}. O horário voltará a ficar livre para outros clientes.`}
          onClose={() => setCancelar(null)} onConfirm={confirmarCancelamento}
        />
      )}
    </div>
  )
}
