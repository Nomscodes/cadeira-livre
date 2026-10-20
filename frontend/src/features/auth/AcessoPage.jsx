import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useDocumentTitle } from '../../lib/hooks'
import { USE_MOCK } from '../../lib/http'
import { digits, isEmail, maskPhone } from '../../lib/format'
import { useCliente } from '../../context/ClienteContext'
import AuthShell from '../../components/layout/AuthShell'
import Button from '../../components/ui/Button'
import { Field, FieldGroup } from '../../components/ui/Field'

/** Acesso temporario do cliente: sem senha, um codigo novo a cada acesso. */
export default function AcessoPage() {
  useDocumentTitle('Acesso temporário')
  const { autenticado, enviarCodigo, confirmar } = useCliente()
  const navigate = useNavigate()
  const { state } = useLocation()
  const destino = state?.voltar || '/buscar'

  const [etapa, setEtapa] = useState('contato')
  const [canal, setCanal] = useState('whatsapp')
  const [contato, setContato] = useState('')
  const [codigo, setCodigo] = useState('')
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState(false)

  if (autenticado && etapa === 'contato') return <Navigate to={destino} replace />

  const run = (fn) => async (e) => {
    e.preventDefault()
    setErro('')
    setBusy(true)
    try { await fn() } catch (err) { setErro(err.message) } finally { setBusy(false) }
  }

  const enviar = run(async () => {
    const ok = canal === 'whatsapp' ? digits(contato).length >= 10 : isEmail(contato.trim())
    if (!ok) throw new Error(canal === 'whatsapp' ? 'Informe o WhatsApp com DDD.' : 'Informe um e-mail válido.')
    await enviarCodigo(canal, contato)
    setEtapa('codigo')
  })
  const validar = run(async () => {
    await confirmar(contato, codigo.trim())
    navigate(destino, { replace: true })
  })

  return (
    <AuthShell back={{ to: '/login', label: 'voltar' }}>
      <h3 style={{ marginTop: 0 }}>Acesso temporário</h3>
      <p className="hint">sem senha permanente, um código novo é enviado a cada acesso</p>

      {etapa === 'contato' ? (
        <form onSubmit={enviar} noValidate>
          <FieldGroup label="receber código por">
            <div className="radio-row">
              <label><input type="radio" name="canal" checked={canal === 'whatsapp'} onChange={() => { setCanal('whatsapp'); setContato('') }} /> WhatsApp</label>
              <label><input type="radio" name="canal" checked={canal === 'email'} onChange={() => { setCanal('email'); setContato('') }} /> E-mail</label>
            </div>
          </FieldGroup>
          <Field label="seu contato">
            {canal === 'whatsapp'
              ? <input type="tel" inputMode="tel" autoComplete="tel" placeholder="(62) 90000-0000" value={contato} onChange={(e) => setContato(maskPhone(e.target.value))} />
              : <input type="text" inputMode="email" autoComplete="email" placeholder="seuemail@exemplo.com" value={contato} onChange={(e) => setContato(e.target.value)} />}
          </Field>
          {erro && <div className="box warn" role="alert">{erro}</div>}
          <Button type="submit" block loading={busy} style={{ marginTop: 12 }}>Enviar código</Button>
        </form>
      ) : (
        <form onSubmit={validar} noValidate>
          <Field label="código recebido" hint={USE_MOCK ? 'Código de teste: 123456' : undefined}>
            <input type="text" inputMode="numeric" autoComplete="one-time-code" value={codigo} onChange={(e) => setCodigo(e.target.value)} />
          </Field>
          {erro && <div className="box warn" role="alert">{erro}</div>}
          <Button type="submit" block loading={busy} style={{ marginTop: 12 }}>Confirmar código</Button>
          <p className="field-hint">Acesso liberado por tempo limitado, válido para busca de salões, histórico e avaliação.</p>
          <p className="text-center" style={{ marginTop: 10, marginBottom: 0 }}><button type="button" className="link" onClick={() => { setEtapa('contato'); setCodigo(''); setErro('') }}>usar outro contato</button></p>
        </form>
      )}
    </AuthShell>
  )
}
