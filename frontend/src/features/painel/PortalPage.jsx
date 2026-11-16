import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Check, Copy, ExternalLink } from 'lucide-react'
import { api } from '../../lib/http'
import { maskPhone } from '../../lib/format'
import { resumoHorarios } from '../../lib/dates'
import { useToast } from '../../context/ToastContext'
import { usePainel } from '../../components/layout/PainelLayout'
import Button from '../../components/ui/Button'
import { Field, FieldGroup } from '../../components/ui/Field'
import ImagePicker from '../../components/ui/ImagePicker'
import ThemePicker from './ThemePicker'

export default function PortalPage() {
  const { salao, setSalao, setPreviaTema } = usePainel()
  const toast = useToast()
  const [f, setF] = useState(() => ({
    nome: salao.nome, temaId: salao.temaId, sobre: salao.sobre, whatsapp: salao.whatsapp, categoria: salao.categoria,
    cidade: salao.cidade, endereco: salao.endereco, redes: salao.redes, capa: salao.capa,
    adminIds: salao.adminIds, logo: salao.logo, galeria: salao.galeria || [],
  }))
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState(false)
  const [teste, setTeste] = useState(null)
  const [copiado, setCopiado] = useState(false)
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }))
  const naoSalvo = f.temaId !== salao.temaId

  // a cor escolhida vale na hora para toda a tela (previa); ao sair sem salvar, volta a cor salva
  useEffect(() => {
    setPreviaTema(f.temaId)
    return () => setPreviaTema(null)
  }, [f.temaId, setPreviaTema])
  const url = `${window.location.origin}/${salao.slug}`

  const salvar = async () => {
    if (!f.nome.trim()) return setErro('Informe o nome do salão.')
    setErro('')
    setBusy(true)
    try {
      const novo = await api.put('/painel/portal', f, { as: 'staff' })
      setSalao((atual) => ({ ...atual, ...novo }))
      toast.success('Alterações salvas. A nova cor já vale para o portal, o painel e a agenda da equipe.')
    } catch (e) {
      toast.error(e.message)
    } finally {
      setBusy(false)
    }
  }

  const testar = async () => {
    try { setTeste(await api.post('/painel/portal/validar-publicacao', {}, { as: 'staff' })) } catch (e) { toast.error(e.message) }
  }
  const copiar = async () => {
    try { await navigator.clipboard.writeText(url); setCopiado(true); setTimeout(() => setCopiado(false), 2000) } catch { toast.error('Não foi possível copiar. Selecione o endereço e copie manualmente.') }
  }
  const alternarAdmin = (id) => setF((s) => ({ ...s, adminIds: s.adminIds.includes(id) ? s.adminIds.filter((x) => x !== id) : [...s.adminIds, id] }))

  return (
    <div>
      <div className="field">
        <span className="field-label">endereço do seu portal</span>
        <div className="portal-url">
          <code>{url}</code>
          <Button small variant="ghost" onClick={copiar}>{copiado ? <Check size={15} aria-hidden="true" /> : <Copy size={15} aria-hidden="true" />}{copiado ? 'copiado' : 'copiar'}</Button>
          <Link to={`/${salao.slug}`} className="btn small ghost" target="_blank" rel="noopener noreferrer"><ExternalLink size={15} aria-hidden="true" />ver portal</Link>
        </div>
        {!salao.publico && <div className="field-hint">Seu portal está oculto. Os clientes só conseguem acessá-lo depois que você o tornar público em Configurações.</div>}
      </div>

      <div className="identity-grid">
        <div>
          <span className="field-label">logo do salão</span>
          <ImagePicker value={f.logo} onChange={(v) => setF((s) => ({ ...s, logo: v }))} label="Clique para enviar a logo" max={400} alt="Logo atual do salão" removeLabel="Remover logo" />
        </div>
        <div>
          <Field label="nome fantasia / razão social" required><input type="text" value={f.nome} onChange={set('nome')} /></Field>
          <FieldGroup label="cor do portal (7 escuras e 7 pastéis)">
            <ThemePicker value={f.temaId} onChange={(id) => setF((s) => ({ ...s, temaId: id }))} nomeSalao={f.nome} logo={f.logo} naoSalvo={naoSalvo} />
          </FieldGroup>
        </div>
      </div>

      <div className="field">
        <span className="field-label">imagem de capa do portal (aparece ao redor do botão "Agendar horário")</span>
        <ImagePicker wide value={f.capa} onChange={(v) => setF((s) => ({ ...s, capa: v }))} label="Clique para enviar a imagem de capa" max={1600} quality={0.8} alt="Capa atual do portal" removeLabel="Remover capa" />
        <div className="field-hint">Use uma imagem horizontal, de preferência com 1400 x 500 px ou mais. Evite textos pequenos na imagem, pois o botão fica por cima dela.</div>
      </div>

      <Field label="sobre nós"><textarea rows={3} value={f.sobre} onChange={set('sobre')} maxLength={600} /></Field>

      <div className="form-grid">
        <Field label="WhatsApp público (suporte ao cliente)"><input type="tel" inputMode="tel" value={f.whatsapp} onChange={(e) => setF((s) => ({ ...s, whatsapp: maskPhone(e.target.value) }))} /></Field>
        <Field label="categoria / especialidade">
          <select value={f.categoria} onChange={set('categoria')}>
            <option value="barbearia">barbearia</option>
            <option value="salão de beleza">salão de beleza</option>
            <option value="unhas">unhas</option>
          </select>
        </Field>
        <Field label="cidade" hint="Usada na busca de salões. Ex.: Goiânia, GO"><input type="text" value={f.cidade} onChange={set('cidade')} /></Field>
        <Field label="endereço"><input type="text" autoComplete="street-address" value={f.endereco} onChange={set('endereco')} /></Field>
        <Field label="horário de funcionamento" hint="Definido em Agendamentos, na aba Calendário e horários.">
          <input type="text" readOnly value={resumoHorarios(salao.horarios) || 'não definido'} aria-readonly="true" />
        </Field>
        <Field label="link de redes sociais"><input type="text" placeholder="instagram.com/..." value={f.redes} onChange={set('redes')} /></Field>
        <FieldGroup label="administradores do salão" hint="Selecione um ou mais profissionais.">
          <div className="admins-list">
            {salao.profissionais.map((p) => (
              <label className="check-row" key={p.id}><input type="checkbox" checked={f.adminIds.includes(p.id)} onChange={() => alternarAdmin(p.id)} /> {p.nome}</label>
            ))}
          </div>
        </FieldGroup>
      </div>

      <div className="field">
        <span className="field-label">galeria de portfólio</span>
        <ImagePicker multiple value={f.galeria} onChange={(v) => setF((s) => ({ ...s, galeria: v }))} label="Clique para enviar fotos" />
      </div>

      {erro && <div className="box warn" role="alert">{erro}</div>}
      <Button loading={busy} onClick={salvar}>Salvar alterações</Button>

      <hr className="divider" />
      <h2 className="section-label">Regra de publicação</h2>
      <p className="hint">O portal só pode ficar público se houver serviço, houver profissional e todo serviço estiver vinculado a um profissional.</p>
      <Button small variant="ghost" onClick={testar}>Testar publicação</Button>
      {teste && (teste.ok
        ? <div className="box ok" role="status">Tudo certo: existem serviços e profissionais, e todos os serviços têm ao menos um profissional vinculado. O portal pode ficar público.</div>
        : <div className="box warn" role="status">O portal ainda não pode ficar público:<ul>{teste.problemas.map((p) => <li key={p}>{p}</li>)}</ul></div>)}
    </div>
  )
}
