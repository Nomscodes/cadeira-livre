import { useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useDocumentTitle } from '../../lib/hooks'
import { digits, isEmail } from '../../lib/format'
import { USE_MOCK } from '../../lib/http'
import { homeDoPapel, useAuth } from '../../context/AuthContext'
import AuthShell from '../../components/layout/AuthShell'
import Button from '../../components/ui/Button'
import { Field, FieldGroup, PasswordInput } from '../../components/ui/Field'

function LoginForm() {
  const { login } = useAuth()
  const navigate = useNavigate()
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
      const u = await login({ email, senha })
      navigate(homeDoPapel(u), { replace: true })
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
      {USE_MOCK && <p className="hint" style={{ marginTop: 10 }}>fase de testes: admin/admin (administrador) &middot; func/func (funcionário). O sistema reconhece o cargo pelo login.</p>}
      <hr className="divider" />
      <Link to="/acesso" className="btn ghost block">Sou cliente, quero agendar um horário</Link>
      <p className="text-center" style={{ marginTop: 16, marginBottom: 0 }}>
        <Link to="/recuperar-senha" className="link">esqueci minha senha</Link>
      </p>
    </form>
  )
}

function CadastroForm({ onLogin }) {
  const { cadastrar } = useAuth()
  const navigate = useNavigate()
  const [v, setV] = useState({ nome: '', razaoSocial: '', documento: '', email: '', atuacao: 'autonomo', senha: '', confirma: '', termos: false })
  const [erros, setErros] = useState({})
  const [erroGeral, setErroGeral] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => {
    setV((s) => ({ ...s, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }))
    setErros((s) => ({ ...s, [k]: undefined }))
  }

  const validar = () => {
    const e = {}
    if (v.nome.trim().split(/\s+/).length < 2) e.nome = 'Informe nome e sobrenome.'
    if (!v.razaoSocial.trim()) e.razaoSocial = 'Informe a razão social ou o nome do salão.'
    if (![11, 14].includes(digits(v.documento).length)) e.documento = 'Informe um CPF (11 números) ou CNPJ (14 números).'
    if (!isEmail(v.email)) e.email = 'Informe um e-mail válido.'
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
      await cadastrar({ nome: v.nome.trim(), razaoSocial: v.razaoSocial.trim(), documento: digits(v.documento), email: v.email.trim(), atuacao: v.atuacao, senha: v.senha })
      navigate('/painel', { replace: true })
    } catch (err) {
      setErroGeral(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      <Field label="nome completo" error={erros.nome} required><input type="text" autoComplete="name" value={v.nome} onChange={set('nome')} /></Field>
      <Field label="razão social" error={erros.razaoSocial} required><input type="text" autoComplete="organization" value={v.razaoSocial} onChange={set('razaoSocial')} /></Field>
      <Field label="CNPJ ou CPF" error={erros.documento} required><input type="text" inputMode="numeric" value={v.documento} onChange={set('documento')} /></Field>
      <Field label="e-mail" error={erros.email} required><input type="text" inputMode="email" autoComplete="email" value={v.email} onChange={set('email')} /></Field>
      <FieldGroup label="como você atua">
        <div className="radio-row">
          <label><input type="radio" name="atuacao" checked={v.atuacao === 'autonomo'} onChange={() => setV((s) => ({ ...s, atuacao: 'autonomo' }))} /> Profissional autônomo</label>
          <label><input type="radio" name="atuacao" checked={v.atuacao === 'equipe'} onChange={() => setV((s) => ({ ...s, atuacao: 'equipe' }))} /> Salão com equipe</label>
        </div>
      </FieldGroup>
      <Field label="senha" error={erros.senha} hint="Mínimo de 8 caracteres." required><PasswordInput autoComplete="new-password" value={v.senha} onChange={set('senha')} /></Field>
      <Field label="confirmação de senha" error={erros.confirma} required><PasswordInput autoComplete="new-password" value={v.confirma} onChange={set('confirma')} /></Field>
      <label className="check-row"><input type="checkbox" checked={v.termos} onChange={set('termos')} aria-invalid={erros.termos ? true : undefined} /> li e aceito os termos de uso e a política de privacidade</label>
      {erros.termos && <div className="field-error" role="alert">{erros.termos}</div>}
      {erroGeral && <div className="box warn" role="alert">{erroGeral}</div>}
      <Button type="submit" block loading={busy} style={{ marginTop: 16 }}>Criar conta</Button>
      <p className="text-center" style={{ marginTop: 14, marginBottom: 0 }}>
        <button type="button" className="link" onClick={onLogin}>já tem uma conta? entrar</button>
      </p>
    </form>
  )
}

export default function LoginPage() {
  const { usuario } = useAuth()
  const [params, setParams] = useSearchParams()
  const aba = params.get('aba') === 'cadastro' ? 'cadastro' : 'login'
  useDocumentTitle(aba === 'cadastro' ? 'Criar conta' : 'Entrar')

  if (usuario) return <Navigate to={homeDoPapel(usuario)} replace />

  const trocar = (a) => setParams(a === 'cadastro' ? { aba: 'cadastro' } : {}, { replace: true })

  return (
    <AuthShell ambiente>
      <div className="tabs2" role="tablist" aria-label="Acesso">
        <button type="button" role="tab" id="tab-login" aria-selected={aba === 'login'} aria-controls="painel-acesso" onClick={() => trocar('login')}>Entrar</button>
        <button type="button" role="tab" id="tab-cadastro" aria-selected={aba === 'cadastro'} aria-controls="painel-acesso" onClick={() => trocar('cadastro')}>Cadastrar (admin)</button>
      </div>
      <div id="painel-acesso" role="tabpanel" aria-labelledby={`tab-${aba}`}>
        {aba === 'login' ? <LoginForm /> : <CadastroForm onLogin={() => trocar('login')} />}
      </div>
    </AuthShell>
  )
}
