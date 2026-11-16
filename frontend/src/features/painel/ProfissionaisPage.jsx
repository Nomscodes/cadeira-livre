import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync } from '../../lib/hooks'
import { resumoHorarios } from '../../lib/dates'
import { digits, maskPhone } from '../../lib/format'
import { useToast } from '../../context/ToastContext'
import Button from '../../components/ui/Button'
import { Field, FieldGroup } from '../../components/ui/Field'
import { ConfirmDialog, Modal } from '../../components/ui/Modal'
import { ErrorState, Loading } from '../../components/ui/States'

function ProfissionalModal({ prof, servicos, onClose, onSaved, onDelete }) {
  const toast = useToast()
  const novo = !prof
  const [f, setF] = useState({
    nome: prof?.nome || '', nomeSocial: prof?.nomeSocial || '', whatsapp: prof?.whatsapp || '', encaminhar: prof?.encaminhar ?? true,
    servicoIds: prof?.servicoIds || [], atende: prof?.atende ?? true,
  })
  const [erros, setErros] = useState({})
  const [busy, setBusy] = useState(false)
  const alt = (id) => setF((s) => ({ ...s, servicoIds: s.servicoIds.includes(id) ? s.servicoIds.filter((x) => x !== id) : [...s.servicoIds, id] }))

  const salvar = async () => {
    const e = {}
    if (!f.nome.trim()) e.nome = 'Informe o nome completo.'
    if (f.encaminhar && digits(f.whatsapp).length < 10) e.whatsapp = 'Para ativar o encaminhamento, informe o WhatsApp com DDD.'
    setErros(e)
    if (Object.keys(e).length) return
    setBusy(true)
    try {
      if (novo) {
        const r = await api.post('/painel/profissionais', f, { as: 'staff' })
        toast.success(`Profissional cadastrado. Código de convite: ${r.convite}`)
      } else {
        await api.put(`/painel/profissionais/${prof.id}`, f, { as: 'staff' })
        toast.success('Profissional atualizado.')
      }
      onSaved()
    } catch (err) {
      toast.error(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={novo ? 'Cadastro de profissional' : 'Editar profissional'} onClose={onClose}>
      <Field label="nome completo" error={erros.nome} required><input type="text" value={f.nome} onChange={(e) => setF({ ...f, nome: e.target.value })} /></Field>
      <Field label="nome social (exibido aos clientes)"><input type="text" value={f.nomeSocial} onChange={(e) => setF({ ...f, nomeSocial: e.target.value })} /></Field>
      <Field label="WhatsApp do profissional" error={erros.whatsapp}><input type="tel" inputMode="tel" value={f.whatsapp} onChange={(e) => setF({ ...f, whatsapp: maskPhone(e.target.value) })} /></Field>
      <label className="check-row"><input type="checkbox" checked={f.atende} onChange={(e) => setF({ ...f, atende: e.target.checked })} /> atende clientes (aparece no portal, nos agendamentos e nos horários)</label>
      {prof?.ehAdmin && <div className="field-hint" style={{ marginBottom: 14 }}>Você é o administrador. Se você só gerencia o salão e não atende clientes, desmarque: seu nome sai do portal e da agenda de horários, e você continua administrando.</div>}
      {!f.atende && <div className="box info" style={{ marginTop: 8 }}>Quem não atende clientes não realiza serviços. Os serviços vinculados a esta pessoa serão desvinculados ao salvar.</div>}
      <FieldGroup label="serviços prestados" className={f.atende ? '' : 'is-off'}>
        {!f.atende ? <p className="hint">Não se aplica a quem não atende clientes.</p> : servicos.length === 0 ? <p className="hint">Cadastre serviços para vinculá-los aqui.</p>
          : servicos.map((s) => <label className="check-row" key={s.id}><input type="checkbox" checked={f.servicoIds.includes(s.id)} onChange={() => alt(s.id)} /> {s.nome}</label>)}
      </FieldGroup>
      <label className="check-row" style={{ marginBottom: 14 }}><input type="checkbox" checked={f.encaminhar} onChange={(e) => setF({ ...f, encaminhar: e.target.checked })} /> ativar mensagens de encaminhamento de agendamento via WhatsApp</label>
      <div className="box info" style={{ marginTop: 0 }}>
        {novo ? 'O profissional começa com horário de segunda a sexta, das 09h às 18h. ' : ''}
        Dias e horários de trabalho, e folgas, são ajustados em <Link to="/painel/agendamentos?aba=horarios" className="link">Agendamentos &rarr; Calendário e horários</Link>.
      </div>
      <div className="modal-actions">
        {!novo && <Button variant="danger-outline" onClick={onDelete}>Remover</Button>}
        <Button variant="ghost" onClick={onClose}>Cancelar</Button>
        <Button loading={busy} onClick={salvar}>{novo ? 'Salvar e gerar convite' : 'Salvar'}</Button>
      </div>
    </Modal>
  )
}

export default function ProfissionaisPage() {
  const toast = useToast()
  const [modal, setModal] = useState(null) // { prof?, excluir? }
  const lista = useAsync(() => api.get('/painel/profissionais', { as: 'staff' }), [])

  const salvo = () => { setModal(null); lista.reload() }
  const remover = async () => {
    try { await api.delete(`/painel/profissionais/${modal.prof.id}`, { as: 'staff' }); toast.success('Profissional removido.'); salvo() }
    catch (e) { toast.error(e.message); setModal(null) }
  }

  if (lista.loading && !lista.data) return <Loading />
  if (lista.error) return <ErrorState error={lista.error} onRetry={lista.reload} />
  const { profissionais, servicos } = lista.data
  const nomeServicos = (ids) => servicos.filter((s) => ids.includes(s.id)).map((s) => s.nome).join(', ') || 'nenhum serviço'

  return (
    <div>
      <div className="page-actions">
        <h2 className="subhead" style={{ margin: 0 }}>Equipe</h2>
        <Button small onClick={() => setModal({})}><Plus size={16} aria-hidden="true" />novo profissional</Button>
      </div>
      <div className="table-wrap">
        <table className="data stackable">
          <thead><tr><th>nome</th><th>serviços</th><th>horário de trabalho</th><th>WhatsApp p/ encaminhamento</th></tr></thead>
          <tbody>
            {profissionais.length === 0 && <tr><td colSpan={4} className="text-center muted">Nenhum profissional cadastrado.</td></tr>}
            {profissionais.map((p) => (
              <tr key={p.id} className="clickable" tabIndex={0} onClick={() => setModal({ prof: p })}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setModal({ prof: p }) } }}>
                <td data-label="nome">{p.nome}{p.ehAdmin && <span className="tag-mini">administrador</span>}{!p.atende && <span className="tag-mini off">não atende clientes</span>}</td>
                <td data-label="serviços">{nomeServicos(p.servicoIds)}</td>
                <td data-label="horário">{p.atende ? resumoHorarios(p.horarios) || 'sem horário definido' : '-'}</td>
                <td data-label="WhatsApp">{p.encaminhar ? 'ativado' : 'desativado'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="hint" style={{ marginTop: 14 }}>
        Para ajustar horários de trabalho, folgas e o bloqueio geral do salão, abra <Link to="/painel/agendamentos?aba=horarios" className="link">Agendamentos &rarr; Calendário e horários</Link>.
      </p>

      {modal && !modal.excluir && <ProfissionalModal prof={modal.prof} servicos={servicos} onClose={() => setModal(null)} onSaved={salvo} onDelete={() => setModal({ ...modal, excluir: true })} />}
      {modal?.excluir && (
        <ConfirmDialog title="Remover profissional?" danger confirmLabel="Remover" onClose={() => setModal(null)} onConfirm={remover}
          message={`${modal.prof.nome} deixará de aparecer no portal. Agendamentos já feitos continuam no histórico.`} />
      )}
    </div>
  )
}
