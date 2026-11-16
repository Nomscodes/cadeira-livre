import { useState } from 'react'
import { Modal } from '../../components/ui/Modal'
import Button from '../../components/ui/Button'
import { Field } from '../../components/ui/Field'
import { digits, maskPhone } from '../../lib/format'

/** Passo final: dados do cliente + conferencia do resumo. */
export default function ConfirmarAgendamentoModal({ resumo, horasCancelamento, onClose, onDescartar, onConfirmar }) {
  const [nome, setNome] = useState('')
  const [whats, setWhats] = useState('')
  const [ok, setOk] = useState(false)
  const [erros, setErros] = useState({})
  const [busy, setBusy] = useState(false)

  const confirmar = async () => {
    const e = {}
    if (nome.trim().split(/\s+/).length < 2) e.nome = 'Informe nome e sobrenome.'
    if (digits(whats).length < 10) e.whats = 'Informe o WhatsApp com DDD.'
    setErros(e)
    if (Object.keys(e).length) return
    setBusy(true)
    try { await onConfirmar({ clienteNome: nome.trim(), clienteWhats: whats }) } finally { setBusy(false) }
  }

  return (
    <Modal title="Resumo do agendamento" onClose={onClose}>
      <div className="modal-row"><span>profissional(is)</span><b>{resumo.profissionais}</b></div>
      <div className="modal-row"><span>serviço(s)</span><b>{resumo.servicos}</b></div>
      <div className="modal-row"><span>data e hora</span><b>{resumo.quando}</b></div>
      <div className="modal-row"><span>tempo médio</span><b>{resumo.tempo}</b></div>
      <div className="modal-row"><span>valor total</span><b>{resumo.valor}</b></div>
      <div className="box warn">Você pode cancelar este agendamento em até <b>{horasCancelamento} horas</b> antes do horário marcado (prazo definido pelo salão).</div>

      <div style={{ marginTop: 16 }}>
        <Field label="seu nome completo" error={erros.nome} required><input type="text" autoComplete="name" value={nome} onChange={(e) => setNome(e.target.value)} /></Field>
        <Field label="seu WhatsApp" error={erros.whats} required><input type="tel" inputMode="tel" autoComplete="tel" placeholder="(62) 90000-0000" value={whats} onChange={(e) => setWhats(maskPhone(e.target.value))} /></Field>
      </div>
      <label className="check-row"><input type="checkbox" checked={ok} onChange={(e) => setOk(e.target.checked)} /> todas as informações estão corretas</label>

      <div className="modal-actions">
        <Button variant="ghost" onClick={onClose}>Editar informações</Button>
        <Button variant="danger-outline" onClick={onDescartar}>Cancelar agendamento</Button>
        <Button disabled={!ok} loading={busy} onClick={confirmar}>Confirmar</Button>
      </div>
    </Modal>
  )
}
