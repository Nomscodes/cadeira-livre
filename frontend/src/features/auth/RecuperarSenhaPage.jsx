import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../../lib/http'
import { useDocumentTitle } from '../../lib/hooks'
import { USE_MOCK } from '../../lib/http'
import { useToast } from '../../context/ToastContext'
import AuthShell from '../../components/layout/AuthShell'
import Button from '../../components/ui/Button'
import { Field, PasswordInput } from '../../components/ui/Field'

export default function RecuperarSenhaPage() {
  useDocumentTitle('Recuperar senha')
  const navigate = useNavigate()
  const toast = useToast()
  const [etapa, setEtapa] = useState('email') // email | codigo | senha | naoencontrado
  const [email, setEmail] = useState('')
  const [codigo, setCodigo] = useState('')
  const [nova, setNova] = useState('')
  const [confirma, setConfirma] = useState('')
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState(false)

  const passo = (fn) => async (e) => {
    e.preventDefault()
    setErro('')
    setBusy(true)
    try { await fn() } catch (err) { setErro(err.message) } finally { setBusy(false) }
  }

  const enviarEmail = passo(async () => {
    if (!email.trim()) throw new Error('Informe o e-mail cadastrado.')
    try {
      await api.post('/auth/recuperar', { email })
      setEtapa('codigo')
    } catch (err) {
      if (err.status === 404) setEtapa('naoencontrado')
      else throw err
    }
  })
  const confirmarCodigo = passo(async () => {
    await api.post('/auth/recuperar/codigo', { email, codigo: codigo.trim() })
    setEtapa('senha')
  })
  const salvar = passo(async () => {
    if (nova.length < 8) throw new Error('A senha precisa ter ao menos 8 caracteres.')
    if (nova !== confirma) throw new Error('A confirmação não é igual à nova senha.')
    await api.post('/auth/recuperar/redefinir', { email, codigo: codigo.trim(), novaSenha: nova })
    toast.success('Senha alterada. Entre com a nova senha.')
    navigate('/login', { replace: true })
  })
  const recomecar = () => { setEtapa('email'); setCodigo(''); setErro('') }

  return (
    <AuthShell back={{ to: '/login', label: 'voltar ao login' }}>
      <h3 style={{ marginTop: 0 }}>Recuperar senha</h3>

      {etapa === 'email' && (
        <form onSubmit={enviarEmail} noValidate>
          <Field label="e-mail cadastrado" hint={USE_MOCK ? 'Para testar, use admin@teste.com.' : undefined}>
            <input type="text" inputMode="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          {erro && <div className="box warn" role="alert">{erro}</div>}
          <Button type="submit" block loading={busy} style={{ marginTop: 12 }}>Continuar</Button>
        </form>
      )}

      {etapa === 'codigo' && (
        <form onSubmit={confirmarCodigo} noValidate>
          <p className="hint">Enviamos um código de recuperação para {email}.</p>
          <Field label="código recebido" hint={USE_MOCK ? 'Código de teste: 123456' : undefined}>
            <input type="text" inputMode="numeric" autoComplete="one-time-code" value={codigo} onChange={(e) => setCodigo(e.target.value)} />
          </Field>
          {erro && <div className="box warn" role="alert">{erro}</div>}
          <Button type="submit" block loading={busy} style={{ marginTop: 12 }}>Confirmar código</Button>
          <p className="text-center" style={{ marginTop: 14, marginBottom: 0 }}><button type="button" className="link" onClick={recomecar}>recomeçar com outro e-mail</button></p>
        </form>
      )}

      {etapa === 'senha' && (
        <form onSubmit={salvar} noValidate>
          <Field label="nova senha" hint="Mínimo de 8 caracteres."><PasswordInput autoComplete="new-password" value={nova} onChange={(e) => setNova(e.target.value)} /></Field>
          <Field label="confirmação da nova senha"><PasswordInput autoComplete="new-password" value={confirma} onChange={(e) => setConfirma(e.target.value)} /></Field>
          {erro && <div className="box warn" role="alert">{erro}</div>}
          <Button type="submit" block loading={busy} style={{ marginTop: 12 }}>Salvar nova senha</Button>
        </form>
      )}

      {etapa === 'naoencontrado' && (
        <div>
          <div className="box warn" role="alert" style={{ marginTop: 0 }}>Este e-mail não está cadastrado.</div>
          <Button block onClick={recomecar} style={{ marginTop: 14 }}>Tentar outro e-mail</Button>
          <p className="text-center" style={{ marginTop: 14, marginBottom: 0 }}><Link to="/login?aba=cadastro" className="link">ou ir para o cadastro</Link></p>
        </div>
      )}
    </AuthShell>
  )
}
