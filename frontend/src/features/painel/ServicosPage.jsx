import { useState } from 'react'
import { Plus } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync } from '../../lib/hooks'
import { formatDuration } from '../../lib/dates'
import { money, parseMoney } from '../../lib/format'
import { useToast } from '../../context/ToastContext'
import Button from '../../components/ui/Button'
import { Field, FieldGroup } from '../../components/ui/Field'
import { ConfirmDialog, Modal } from '../../components/ui/Modal'
import { ErrorState, Loading } from '../../components/ui/States'

const fmtInput = (v) => (v === '' || v == null ? '' : String(v).replace('.', ','))

function ProfissionaisCheck({ profissionais, value, onChange }) {
  const alternar = (id) => onChange(value.includes(id) ? value.filter((x) => x !== id) : [...value, id])
  return profissionais.map((p) => (
    <label className="check-row" key={p.id}><input type="checkbox" checked={value.includes(p.id)} onChange={() => alternar(p.id)} /> {p.nome}</label>
  ))
}

function ServicoModal({ servico, profissionais, onClose, onSaved, onDelete }) {
  const toast = useToast()
  const novo = !servico
  const [f, setF] = useState({ nome: servico?.nome || '', preco: fmtInput(servico?.preco), duracao: servico?.duracao ?? '', profissionalIds: servico?.profissionalIds || [] })
  const [erros, setErros] = useState({})
  const [busy, setBusy] = useState(false)

  const salvar = async () => {
    const e = {}
    if (!f.nome.trim()) e.nome = 'Informe o nome do serviço.'
    if (!(parseMoney(f.preco) >= 0)) e.preco = 'Informe um valor válido, como 45,00.'
    if (!(Number(f.duracao) >= 5)) e.duracao = 'O tempo médio precisa ser de pelo menos 5 minutos.'
    setErros(e)
    if (Object.keys(e).length) return
    setBusy(true)
    try {
      const body = { nome: f.nome, preco: parseMoney(f.preco), duracao: Number(f.duracao), profissionalIds: f.profissionalIds }
      if (novo) await api.post('/painel/servicos', body, { as: 'staff' })
      else await api.put(`/painel/servicos/${servico.id}`, body, { as: 'staff' })
      toast.success(novo ? 'Serviço cadastrado.' : 'Serviço atualizado.')
      onSaved()
    } catch (err) {
      toast.error(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={novo ? 'Cadastro de serviço' : 'Editar serviço'} onClose={onClose}>
      <Field label="nome do serviço" error={erros.nome} required><input type="text" value={f.nome} onChange={(e) => setF({ ...f, nome: e.target.value })} /></Field>
      <div className="form-grid">
        <Field label="valor (R$)" error={erros.preco} required><input type="text" inputMode="decimal" placeholder="45,00" value={f.preco} onChange={(e) => setF({ ...f, preco: e.target.value })} /></Field>
        <Field label="tempo médio (min)" error={erros.duracao} required><input type="number" inputMode="numeric" min={5} step={5} value={f.duracao} onChange={(e) => setF({ ...f, duracao: e.target.value })} /></Field>
      </div>
      <FieldGroup label="profissional(is) que realizam">
        {profissionais.length === 0 ? <p className="hint">Cadastre profissionais para vinculá-los aos serviços.</p>
          : <ProfissionaisCheck profissionais={profissionais} value={f.profissionalIds} onChange={(v) => setF({ ...f, profissionalIds: v })} />}
      </FieldGroup>
      <div className="modal-actions">
        {!novo && <Button variant="danger-outline" onClick={onDelete}>Excluir</Button>}
        <Button variant="ghost" onClick={onClose}>Cancelar</Button>
        <Button loading={busy} onClick={salvar}>Salvar</Button>
      </div>
    </Modal>
  )
}

function ComboModal({ combo, servicos, profissionais, onClose, onSaved, onDelete }) {
  const toast = useToast()
  const novo = !combo
  const [f, setF] = useState({ nome: combo?.nome || '', servicoIds: combo?.servicoIds || [], preco: fmtInput(combo?.preco), profissionalIds: combo?.profissionalIds || [] })
  const [erros, setErros] = useState({})
  const [busy, setBusy] = useState(false)
  const soma = servicos.filter((s) => f.servicoIds.includes(s.id)).reduce((t, s) => t + s.preco, 0)
  const final = parseMoney(f.preco)
  const alternar = (id) => setF((s) => ({ ...s, servicoIds: s.servicoIds.includes(id) ? s.servicoIds.filter((x) => x !== id) : [...s.servicoIds, id] }))

  const salvar = async () => {
    const e = {}
    if (!f.nome.trim()) e.nome = 'Informe o nome do combo.'
    if (f.servicoIds.length < 2) e.servicos = 'Escolha pelo menos dois serviços.'
    if (!(final > 0)) e.preco = 'Informe o valor final do combo.'
    setErros(e)
    if (Object.keys(e).length) return
    setBusy(true)
    try {
      const body = { nome: f.nome, servicoIds: f.servicoIds, preco: final, profissionalIds: f.profissionalIds }
      if (novo) await api.post('/painel/combos', body, { as: 'staff' })
      else await api.put(`/painel/combos/${combo.id}`, body, { as: 'staff' })
      toast.success(novo ? 'Combo cadastrado.' : 'Combo atualizado.')
      onSaved()
    } catch (err) {
      toast.error(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title={novo ? 'Novo combo de serviços' : 'Editar combo'} onClose={onClose}>
      <Field label="nome do combo" error={erros.nome} required><input type="text" value={f.nome} onChange={(e) => setF({ ...f, nome: e.target.value })} /></Field>
      <FieldGroup label="serviços inclusos" error={erros.servicos}>
        {servicos.map((s) => <label className="check-row" key={s.id}><input type="checkbox" checked={f.servicoIds.includes(s.id)} onChange={() => alternar(s.id)} /> {s.nome}, {money(s.preco)}</label>)}
      </FieldGroup>
      <p className="hint">Soma dos serviços individuais: {money(soma)}</p>
      <Field label="valor final do combo (R$)" error={erros.preco} required><input type="text" inputMode="decimal" value={f.preco} onChange={(e) => setF({ ...f, preco: e.target.value })} /></Field>
      {final > 0 && soma > 0 && final >= soma && <div className="box warn" role="status" style={{ marginTop: 0, marginBottom: 14 }}>O valor final está igual ou acima da soma dos serviços individuais. O combo normalmente deveria ser mais vantajoso.</div>}
      <FieldGroup label="profissional(is) que podem realizar este combo">
        <ProfissionaisCheck profissionais={profissionais} value={f.profissionalIds} onChange={(v) => setF({ ...f, profissionalIds: v })} />
      </FieldGroup>
      <div className="modal-actions">
        {!novo && <Button variant="danger-outline" onClick={onDelete}>Excluir</Button>}
        <Button variant="ghost" onClick={onClose}>Cancelar</Button>
        <Button loading={busy} onClick={salvar}>Salvar combo</Button>
      </div>
    </Modal>
  )
}

export default function ServicosPage() {
  const toast = useToast()
  const { data, loading, error, reload } = useAsync(() => api.get('/painel/servicos', { as: 'staff' }), [])
  const [modal, setModal] = useState(null) // { tipo: 'servico'|'combo', item?, excluir? }

  const fechar = () => setModal(null)
  const salvo = () => { fechar(); reload() }
  const excluir = async () => {
    const { tipo, item } = modal
    try {
      await api.delete(`/painel/${tipo === 'servico' ? 'servicos' : 'combos'}/${item.id}`, { as: 'staff' })
      toast.success(tipo === 'servico' ? 'Serviço excluído.' : 'Combo excluído.')
      salvo()
    } catch (e) { toast.error(e.message); fechar() }
  }

  if (loading && !data) return <Loading />
  if (error) return <ErrorState error={error} onRetry={reload} />
  const { servicos, combos, profissionais } = data
  const nomeProfs = (ids) => ids.map((id) => profissionais.find((p) => p.id === id)?.nome).filter(Boolean).join(', ')

  return (
    <div>
      <div className="grid-cards">
        {servicos.map((s) => (
          <button key={s.id} type="button" className="card-box" onClick={() => setModal({ tipo: 'servico', item: s })}>
            <h2 style={{ fontSize: '1rem', margin: '0 0 4px', color: 'var(--c-ink-strong)' }}>{s.nome}</h2>
            <div className="meta" style={{ marginBottom: 0 }}>{money(s.preco)} · {formatDuration(s.duracao)}{s.profissionalIds.length > 0 && <><br />{nomeProfs(s.profissionalIds)}</>}</div>
            {s.profissionalIds.length === 0 && <div className="field-error">Sem profissional vinculado</div>}
          </button>
        ))}
        <button type="button" className="card-box card-add" onClick={() => setModal({ tipo: 'servico' })}><Plus size={18} aria-hidden="true" />novo serviço</button>
      </div>

      <hr className="divider" />
      <h2 className="subhead">Combos de serviços</h2>
      <div className="grid-cards">
        {combos.map((c) => {
          const soma = servicos.filter((s) => c.servicoIds.includes(s.id)).reduce((t, s) => t + s.preco, 0)
          return (
            <button key={c.id} type="button" className="card-box" onClick={() => setModal({ tipo: 'combo', item: c })}>
              <h3 style={{ fontSize: '1rem', margin: '0 0 4px', color: 'var(--c-ink-strong)' }}>{c.nome}</h3>
              <div className="meta" style={{ marginBottom: 0 }}>{money(c.preco)} · soma individual: {money(soma)}</div>
            </button>
          )
        })}
        <button type="button" className="card-box card-add" disabled={servicos.length < 2} onClick={() => setModal({ tipo: 'combo' })}><Plus size={18} aria-hidden="true" />novo combo</button>
      </div>
      {servicos.length < 2 && <p className="hint" style={{ marginTop: 10 }}>Cadastre pelo menos dois serviços para criar um combo.</p>}

      {modal?.tipo === 'servico' && !modal.excluir && (
        <ServicoModal servico={modal.item} profissionais={profissionais} onClose={fechar} onSaved={salvo} onDelete={() => setModal({ ...modal, excluir: true })} />
      )}
      {modal?.tipo === 'combo' && !modal.excluir && (
        <ComboModal combo={modal.item} servicos={servicos} profissionais={profissionais} onClose={fechar} onSaved={salvo} onDelete={() => setModal({ ...modal, excluir: true })} />
      )}
      {modal?.excluir && (
        <ConfirmDialog
          title={modal.tipo === 'servico' ? 'Excluir serviço?' : 'Excluir combo?'} danger confirmLabel="Excluir"
          message={`"${modal.item.nome}" será removido do portal. Agendamentos já feitos não são afetados.`}
          onClose={fechar} onConfirm={excluir}
        />
      )}
    </div>
  )
}
