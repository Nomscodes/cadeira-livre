import { forwardRef, useState } from 'react'
import { api } from '../../lib/http'
import { useAuth } from '../../context/AuthContext'
import { useToast } from '../../context/ToastContext'
import { downloadFile, toCSV } from '../../lib/csv'
import { formatDateBR } from '../../lib/dates'
import { usePainel } from '../../components/layout/PainelLayout'
import Button from '../../components/ui/Button'
import { Field, FieldGroup, PasswordInput } from '../../components/ui/Field'
import { ConfirmDialog } from '../../components/ui/Modal'
import Toggle from '../../components/ui/Toggle'

/** Campo numerico curto com a unidade ao lado. Repassa id e aria-* ao <input> para o rotulo continuar ligado ao campo. */
const CampoComSufixo = forwardRef(function CampoComSufixo({ sufixo, ...props }, ref) {
  return (
    <div className="inline-field">
      <input ref={ref} className="input-xs" {...props} />
      <span>{sufixo}</span>
    </div>
  )
})

export default function ConfigPage() {
  const { salao, setSalao } = usePainel()
  const { usuario, logout } = useAuth()
  const toast = useToast()

  const [cfg, setCfg] = useState({ atendimento: salao.atendimento, cancelamentoHoras: salao.cancelamentoHoras, funcionariosEditamHorario: salao.funcionariosEditamHorario })
  const [salvando, setSalvando] = useState(false)
  const [transfer, setTransfer] = useState('')
  const [dialogo, setDialogo] = useState(null) // 'transferir' | 'dados' | 'conta'
  const [senha, setSenha] = useState({ atual: '', nova: '' })
  const [erroSenha, setErroSenha] = useState('')
  const [problemas, setProblemas] = useState([])
  const outros = salao.profissionais.filter((p) => p.id !== usuario.profissionalId)

  const salvarCfg = async () => {
    setSalvando(true)
    try {
      await api.put('/painel/configuracoes', cfg, { as: 'staff' })
      setSalao((s) => ({ ...s, ...cfg, cancelamentoHoras: Number(cfg.cancelamentoHoras) }))
      toast.success('Configurações salvas.')
    } catch (e) { toast.error(e.message) } finally { setSalvando(false) }
  }

  const trocarSenha = async () => {
    setErroSenha('')
    if (!senha.atual || !senha.nova) return setErroSenha('Preencha a senha atual e a nova senha.')
    try {
      await api.post('/painel/senha', senha, { as: 'staff' })
      setSenha({ atual: '', nova: '' })
      toast.success('Senha redefinida.')
    } catch (e) { setErroSenha(e.message) }
  }

  const alternarPublico = async (v) => {
    setProblemas([])
    try {
      await api.put('/painel/portal/publicacao', { publico: v }, { as: 'staff' })
      setSalao((s) => ({ ...s, publico: v }))
      toast.success(v ? 'Portal público.' : 'Portal oculto.')
    } catch (e) {
      setProblemas(e.details?.problemas || [])
      toast.error(e.message)
    }
  }

  const exportar = async () => {
    try {
      const lista = await api.get('/painel/agendamentos', { as: 'staff' })
      const csv = toCSV(
        ['cliente', 'whatsapp', 'serviços', 'profissionais', 'data', 'hora', 'valor', 'status'],
        lista.map((a) => [a.cliente, a.whatsapp, a.servicos, a.profissionais, formatDateBR(a.data), a.hora, a.valor.toFixed(2).replace('.', ','), a.status]),
      )
      downloadFile(`${salao.slug}-agendamentos.csv`, csv)
      toast.success('Arquivo gerado.')
    } catch (e) { toast.error(e.message) }
  }

  const suporte = () => {
    const url = import.meta.env.VITE_SUPPORT_URL
    if (url) window.open(url, '_blank', 'noopener,noreferrer')
    else toast.info('O canal de suporte ainda não foi configurado neste ambiente.')
  }

  const transferir = async () => {
    try {
      await api.post('/painel/configuracoes/transferir-admin', { profissionalId: transfer }, { as: 'staff' })
      toast.success('Cargo transferido. Entre novamente para continuar.')
      logout()
    } catch (e) { toast.error(e.message); setDialogo(null) }
  }
  const apagarDados = async () => {
    try { await api.delete('/painel/dados', { as: 'staff' }); toast.success('Dados apagados. O portal foi ocultado.'); setSalao((s) => ({ ...s, publico: false })); setDialogo(null) }
    catch (e) { toast.error(e.message); setDialogo(null) }
  }
  const encerrar = async () => {
    try { await api.delete('/painel/conta', { as: 'staff' }); toast.success('Conta encerrada.'); logout() }
    catch (e) { toast.error(e.message); setDialogo(null) }
  }

  return (
    <div style={{ maxWidth: 640 }}>
      <Field label="transferir cargo de administrador para" hint={outros.length === 0 ? 'Cadastre outro profissional para poder transferir o cargo.' : undefined}>
        <select value={transfer} onChange={(e) => setTransfer(e.target.value)} disabled={outros.length === 0}>
          <option value="">selecione um profissional</option>
          {outros.map((p) => <option key={p.id} value={p.id}>{p.nome}</option>)}
        </select>
      </Field>
      {transfer && <Button small variant="ghost" onClick={() => setDialogo('transferir')} style={{ marginBottom: 20 }}>Transferir cargo</Button>}

      <FieldGroup label="tipo de atendimento">
        <div className="radio-row">
          <label><input type="radio" name="atendimento" checked={cfg.atendimento === 'local'} onChange={() => setCfg({ ...cfg, atendimento: 'local' })} /> local fixo</label>
          <label><input type="radio" name="atendimento" checked={cfg.atendimento === 'domicilio'} onChange={() => setCfg({ ...cfg, atendimento: 'domicilio' })} /> atendimento na casa do cliente</label>
        </div>
      </FieldGroup>
      <Field label="antecedência mínima para o cliente cancelar">
        <CampoComSufixo type="number" inputMode="numeric" min={0} max={72} sufixo="horas antes do horário marcado" value={cfg.cancelamentoHoras} onChange={(e) => setCfg({ ...cfg, cancelamentoHoras: e.target.value })} />
      </Field>
      <div className="field toggle-row">
        <label className="field-label" htmlFor="cfg-edit">funcionários podem editar os próprios horários</label>
        <Toggle id="cfg-edit" checked={cfg.funcionariosEditamHorario} onChange={(v) => setCfg({ ...cfg, funcionariosEditamHorario: v })} />
      </div>
      <Button loading={salvando} onClick={salvarCfg}>Salvar configurações</Button>

      <hr className="divider" />
      <h2 className="section-label">Senha</h2>
      <Field label="senha atual"><PasswordInput autoComplete="current-password" value={senha.atual} onChange={(e) => setSenha({ ...senha, atual: e.target.value })} /></Field>
      <Field label="nova senha" hint="Mínimo de 8 caracteres."><PasswordInput autoComplete="new-password" value={senha.nova} onChange={(e) => setSenha({ ...senha, nova: e.target.value })} /></Field>
      {erroSenha && <div className="box warn" role="alert" style={{ marginTop: 0, marginBottom: 12 }}>{erroSenha}</div>}
      <Button small variant="ghost" onClick={trocarSenha}>Redefinir senha</Button>

      <hr className="divider" />
      <div className="toggle-row">
        <label className="field-label" htmlFor="cfg-publico">portal público</label>
        <Toggle id="cfg-publico" checked={salao.publico} onChange={alternarPublico} />
        <span className="hint">{salao.publico ? 'Visível para os clientes.' : 'Oculto para os clientes.'}</span>
      </div>
      {!salao.publico && <div className="box warn">Ao ocultar o portal, os clientes não conseguem mais acessá-lo. No lugar aparece a página "Este salão vai voltar em breve".</div>}
      {problemas.length > 0 && <div className="box warn" role="alert">Não foi possível publicar:<ul>{problemas.map((p) => <li key={p}>{p}</li>)}</ul></div>}

      <hr className="divider" />
      <Button small variant="ghost" onClick={suporte}>Canal de suporte com os desenvolvedores</Button>
      <div className="danger-zone">
        <Button small variant="ghost" onClick={exportar}>Exportar dados do salão (CSV)</Button>
        <Button small variant="danger-outline" onClick={() => setDialogo('dados')}>Apagar todos os dados</Button>
        <Button small variant="danger-outline" onClick={() => setDialogo('conta')}>Encerrar conta do salão</Button>
      </div>

      {dialogo === 'transferir' && (
        <ConfirmDialog title="Transferir cargo de administrador?" confirmLabel="Transferir" onClose={() => setDialogo(null)} onConfirm={transferir}
          message="Você passará a ter acesso de funcionário e precisará entrar novamente. Esta ação só pode ser desfeita pelo novo administrador." />
      )}
      {dialogo === 'dados' && (
        <ConfirmDialog title="Apagar todos os dados?" danger requireText="APAGAR" confirmLabel="Apagar dados" onClose={() => setDialogo(null)} onConfirm={apagarDados}
          message="Serviços, combos, bloqueios e agendamentos serão apagados e o portal ficará oculto. Não dá para desfazer. Exporte o CSV antes se precisar guardar o histórico." />
      )}
      {dialogo === 'conta' && (
        <ConfirmDialog title="Encerrar conta do salão?" danger requireText="ENCERRAR" confirmLabel="Encerrar conta" onClose={() => setDialogo(null)} onConfirm={encerrar}
          message="O portal sai do ar e todos os dados do salão são removidos. Não dá para desfazer." />
      )}
    </div>
  )
}
