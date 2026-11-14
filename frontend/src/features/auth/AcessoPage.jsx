import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useDocumentTitle } from '../../lib/hooks'
import { USE_MOCK } from '../../lib/http'
import { digits, isEmail, maskPhone } from '../../lib/format'
import { useCliente } from '../../context/ClienteContext'
import AuthShell from '../../components/layout/AuthShell'
import Button from '../../components/ui/Button'
import { Field, PasswordInput } from '../../components/ui/Field'

/** Login do cliente com e-mail e senha (RF04). */
function EntrarForm({ onEntrou }) {
  const { entrar } = useCliente()
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    if (!email.trim() || !senha) return setErro('Preencha e-mail e senha.')
    setErro('')
    setBusy(true)
    try {
      await entrar({ email: email.trim(), senha })
      onEntrou()
    } catch (err) {
      setErro(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      <Field label="e-mail">
        <input type="text" inputMode="email" autoComplete="username" placeholder="seuemail@exemplo.com" value={email} onChange={(e) => setEmail(e.target.value)} />
      </Field>
      <Field label="senha">
        <PasswordInput value={senha} onChange={(e) => setSenha(e.target.value)} />
      </Field>
      {erro && <div className="box warn" role="alert">{erro}</div>}
      <Button type="submit" block loading={busy} style={{ marginTop: 14 }}>Entrar</Button>
      {USE_MOCK && <p className="hint" style={{ marginTop: 10 }}>fase de testes: cliente@teste.com / 12345678</p>}
    </form>
  )
}

/** Cadastro do cliente (RF01): nome, e-mail, telefone e senha, com aceite dos termos (RNF02). */
function CadastroForm({ onEntrou }) {
  const { cadastrar } = useCliente()
  const [v, setV] = useState({ nome: '', email: '', telefone: '', senha: '', confirma: '', termos: false })
  const [erros, setErros] = useState({})
  const [erroGeral, setErroGeral] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => {
    const valor = e.target.type === 'checkbox' ? e.target.checked : e.target.value
    setV((s) => ({ ...s, [k]: k === 'telefone' ? maskPhone(valor) : valor }))
    setErros((s) => ({ ...s, [k]: undefined }))
  }

  const validar = () => {
    const e = {}
    if (v.nome.trim().length < 2) e.nome = 'Informe seu nome.'
    if (!isEmail(v.email.trim())) e.email = 'Informe um e-mail válido.'
    if (![10, 11].includes(digits(v.telefone).length)) e.telefone = 'Informe o telefone com DDD.'
    if (v.senha.length < 8) e.senha = 'A senha precisa ter ao menos 8 caracteres.'
    if (v.confirma !== v.senha) e.confirma = 'A confirmação não é igual à senha.'
    if (!v.termos) e.termos = 'Aceite os termos para criar a conta.'
    return e
  }

  const submit = async (ev) => {
    ev.preventDefault()
    const e = validar()
    setErros(e)
    if (Object.keys(e).length) return
    setErroGeral('')
    setBusy(true)
    try {
      // mesmo formato do CadastroClienteRequest do backend
      await cadastrar({ nome: v.nome.trim(), email: v.email.trim(), telefone: digits(v.telefone), senha: v.senha, aceitouTermos: v.termos })
      onEntrou()
    } catch (err) {
      setErroGeral(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      <Field label="nome" error={erros.nome} required><input type="text" autoComplete="name" value={v.nome} onChange={set('nome')} /></Field>
      <Field label="e-mail" error={erros.email} required><input type="text" inputMode="email" autoComplete="email" value={v.email} onChange={set('email')} /></Field>
      <Field label="telefone" error={erros.telefone} required><input type="tel" inputMode="tel" autoComplete="tel" placeholder="(62) 90000-0000" value={v.telefone} onChange={set('telefone')} /></Field>
      <Field label="senha" error={erros.senha} hint="Mínimo de 8 caracteres." required><PasswordInput autoComplete="new-password" value={v.senha} onChange={set('senha')} /></Field>
      <Field label="confirmação de senha" error={erros.confirma} required><PasswordInput autoComplete="new-password" value={v.confirma} onChange={set('confirma')} /></Field>
      <label className="check-row"><input type="checkbox" checked={v.termos} onChange={set('termos')} aria-invalid={erros.termos ? true : undefined} /> li e aceito os termos de uso e a política de privacidade</label>
      {erros.termos && <div className="field-error" role="alert">{erros.termos}</div>}
      {erroGeral && <div className="box warn" role="alert">{erroGeral}</div>}
      <Button type="submit" block loading={busy} style={{ marginTop: 16 }}>Criar conta</Button>
    </form>
  )
}

/** Area do cliente: entrar ou criar conta. Depois volta para a pagina de onde veio. */
export default function AcessoPage() {
  const { autenticado } = useCliente()
  const navigate = useNavigate()
  const { state } = useLocation()
  const destino = state?.voltar || '/buscar'
  const [aba, setAba] = useState('entrar')
  useDocumentTitle(aba === 'cadastro' ? 'Criar conta de cliente' : 'Área do cliente')

  if (autenticado) return <Navigate to={destino} replace />

  const entrou = () => navigate(destino, { replace: true })

  return (
    <AuthShell back={{ to: '/login', label: 'voltar' }}>
      <h3 style={{ marginTop: 0 }}>Área do cliente</h3>
      <p className="hint">entre para agendar, ver seus horários e avaliar o atendimento</p>
      <div className="tabs2" role="tablist" aria-label="Área do cliente">
        <button type="button" role="tab" id="tab-entrar" aria-selected={aba === 'entrar'} aria-controls="painel-cliente" onClick={() => setAba('entrar')}>Entrar</button>
        <button type="button" role="tab" id="tab-cadastro" aria-selected={aba === 'cadastro'} aria-controls="painel-cliente" onClick={() => setAba('cadastro')}>Criar conta</button>
      </div>
      <div id="painel-cliente" role="tabpanel" aria-labelledby={`tab-${aba}`}>
        {aba === 'entrar' ? <EntrarForm onEntrou={entrou} /> : <CadastroForm onEntrou={entrou} />}
      </div>
      <p className="text-center" style={{ marginTop: 14, marginBottom: 0 }}>
        <Link to="/login" className="link">sou dono de barbearia</Link>
      </p>
    </AuthShell>
  )
}
