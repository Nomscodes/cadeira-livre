import { useState } from 'react'
import { api } from '../../lib/http'
import { useAsync } from '../../lib/hooks'
import { formatDateBR, formatDataHora } from '../../lib/dates'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import { Modal } from '../ui/Modal'
import Button from '../ui/Button'
import { Field } from '../ui/Field'

export const TIPOS = {
  agendamento: 'novo agendamento',
  lembrete: 'lembrete',
  cancelamento: 'cancelamento',
  avaliacao: 'avaliação',
  alerta: 'alerta da equipe',
  aviso: 'aviso',
}

/** Formulario do aviso a equipe (acao do responsavel). So lista quem pode receber: a equipe, sem o proprio responsavel. */
function FormAviso({ notificacao, onEnviado, onVoltar }) {
  const toast = useToast()
  const { usuario } = useAuth()
  const { data, loading } = useAsync(() => api.get('/painel/profissionais', { as: 'staff' }), [])
  const [mensagem, setMensagem] = useState(notificacao.texto)
  const [ids, setIds] = useState(null)
  const [busy, setBusy] = useState(false)
  const equipe = (data?.profissionais || []).filter((p) => p.id !== usuario?.profissionalId)
  const marcados = ids ?? equipe.map((p) => p.id)
  const alternar = (id) => setIds(marcados.includes(id) ? marcados.filter((x) => x !== id) : [...marcados, id])

  const enviar = async () => {
    setBusy(true)
    try {
      const r = await api.post(`/notificacoes/${notificacao.id}/acao`, { acao: 'aviso', mensagem, profissionalIds: marcados }, { as: 'staff' })
      toast.success(`Aviso enviado a ${r.enviados} ${r.enviados === 1 ? 'profissional' : 'profissionais'}.`)
      onEnviado()
    } catch (e) { toast.error(e.message) } finally { setBusy(false) }
  }

  return (
    <div>
      <Field label="mensagem do aviso"><textarea rows={4} value={mensagem} onChange={(e) => setMensagem(e.target.value)} maxLength={400} /></Field>
      <fieldset className="field" style={{ border: 0, padding: 0, margin: '0 0 14px' }}>
        <legend className="field-label" style={{ padding: 0 }}>enviar para</legend>
        {loading && <div className="hint">Carregando equipe...</div>}
        {!loading && equipe.length === 0 && <div className="hint">Você ainda não tem outros profissionais cadastrados para receber avisos.</div>}
        {equipe.map((p) => <label className="check-row" key={p.id}><input type="checkbox" checked={marcados.includes(p.id)} onChange={() => alternar(p.id)} /> {p.nome}</label>)}
      </fieldset>
      <div className="modal-actions">
        <Button variant="ghost" onClick={onVoltar}>Voltar</Button>
        <Button loading={busy} disabled={equipe.length === 0 || marcados.length === 0 || !mensagem.trim()} onClick={enviar}>Enviar aviso</Button>
      </div>
    </div>
  )
}

/** Modal de uma notificacao especifica: o que aconteceu, dados do atendimento e (so para o responsavel) as acoes cabiveis ao tipo. */
export default function NotificationModal({ notificacao: n, onClose, onAtualizar }) {
  const toast = useToast()
  const [modo, setModo] = useState('detalhe') // detalhe | aviso
  const [busy, setBusy] = useState(false)

  const executar = async (acao) => {
    if (acao.id === 'aviso') { setModo('aviso'); return }
    setBusy(true)
    try {
      await api.post(`/notificacoes/${n.id}/acao`, { acao: acao.id }, { as: 'staff' })
      toast.success('Link de avaliação enviado ao cliente.')
      onAtualizar()
    } catch (e) { toast.error(e.message) } finally { setBusy(false) }
  }

  return (
    <Modal title={n.titulo} onClose={onClose}>
      {modo === 'aviso' ? (
        <FormAviso notificacao={n} onVoltar={() => setModo('detalhe')} onEnviado={() => { onAtualizar(); onClose() }} />
      ) : (
        <>
          <div className="modal-row"><span>tipo</span><span className="status-chip warn">{TIPOS[n.tipo] || n.tipo}</span></div>
          <div className="modal-row"><span>recebida em</span><b>{formatDataHora(n.criadaEm)}</b></div>
          {n.remetente && <div className="modal-row"><span>enviada por</span><b>{n.remetente}</b></div>}
          <p style={{ margin: '14px 0', fontSize: 13 }}>{n.texto}</p>
          {n.detalhes && (
            <div className="box info" style={{ marginTop: 0 }}>
              <div className="modal-row"><span>cliente</span><b>{n.detalhes.cliente}</b></div>
              <div className="modal-row"><span>serviço(s)</span><b>{n.detalhes.servicos}</b></div>
              <div className="modal-row"><span>profissional(is)</span><b>{n.detalhes.profissionais}</b></div>
              <div className="modal-row" style={{ marginBottom: 0 }}><span>data e hora</span><b>{formatDateBR(n.detalhes.data)} às {n.detalhes.hora}</b></div>
            </div>
          )}
          <div className="modal-actions">
            <Button variant="ghost" onClick={onClose}>Fechar</Button>
            {n.acoes.map((a) => (
              <Button key={a.id} loading={busy} disabled={a.usada} onClick={() => executar(a)}>{a.usada ? 'Avaliação já solicitada' : a.rotulo}</Button>
            ))}
          </div>
        </>
      )}
    </Modal>
  )
}
