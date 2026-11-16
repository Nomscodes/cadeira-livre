import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Scissors } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync, useDocumentTitle } from '../../lib/hooks'
import { formatDateBR } from '../../lib/dates'
import { useToast } from '../../context/ToastContext'
import ThemeScope from '../../theme/ThemeScope'
import AuthShell from '../../components/layout/AuthShell'
import Button from '../../components/ui/Button'
import { Field, FieldGroup } from '../../components/ui/Field'
import { ErrorState, Loading } from '../../components/ui/States'
import ImagePicker from '../../components/ui/ImagePicker'

const ROTULOS = ['Ruim', 'Regular', 'Bom', 'Muito bom', 'Excelente']

export default function AvaliarPage() {
  useDocumentTitle('Avaliar atendimento')
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const { data: a, loading, error, reload } = useAsync(() => api.get(`/cliente/agendamentos/${id}`, { as: 'cliente' }), [id])
  const [nota, setNota] = useState(0)
  const [hover, setHover] = useState(0)
  const [comentario, setComentario] = useState('')
  const [fotos, setFotos] = useState([])
  const [erro, setErro] = useState('')
  const [busy, setBusy] = useState(false)

  const enviar = async (e) => {
    e.preventDefault()
    if (!nota) return setErro('Escolha uma nota de 1 a 5 tesouras.')
    setBusy(true)
    try {
      await api.post(`/cliente/agendamentos/${id}/avaliacao`, { nota, comentario, fotos }, { as: 'cliente' })
      toast.success('Avaliação enviada. Obrigado!')
      navigate('/meus-agendamentos', { replace: true })
    } catch (err) {
      setErro(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Loading />
  if (error) return <div className="container" style={{ paddingTop: 40 }}><ErrorState error={error} onRetry={reload} /></div>

  const ativo = hover || nota

  return (
    <ThemeScope themeId={a.salao.temaId}>
      <AuthShell back={{ to: '/meus-agendamentos', label: 'voltar ao histórico' }}>
        <h3 style={{ marginTop: 0 }}>Avaliar atendimento</h3>
        <div className="hint" style={{ marginBottom: 16 }}>{a.servicos} com {a.profissionais} &middot; {a.salao.nome} &middot; {formatDateBR(a.data)}</div>
        <form onSubmit={enviar} noValidate>
          <FieldGroup label="sua nota" error={erro}>
            <div className="scissors" role="radiogroup" aria-label="Nota de 1 a 5 tesouras" onMouseLeave={() => setHover(0)}>
              {[1, 2, 3, 4, 5].map((n) => (
                <button
                  key={n} type="button" role="radio" aria-checked={nota === n} aria-label={`${n} ${n === 1 ? 'tesoura' : 'tesouras'}, ${ROTULOS[n - 1]}`}
                  className={n <= ativo ? 'on' : ''} onMouseEnter={() => setHover(n)} onFocus={() => setHover(n)} onBlur={() => setHover(0)}
                  onClick={() => { setNota(n); setErro('') }}
                >
                  <Scissors size={26} aria-hidden="true" />
                </button>
              ))}
            </div>
            <div className="hint" aria-live="polite" style={{ marginTop: -8 }}>{ativo ? ROTULOS[ativo - 1] : ' '}</div>
          </FieldGroup>
          <Field label="comentário (opcional)">
            <textarea rows={4} placeholder="Como foi sua experiência?" value={comentario} onChange={(e) => setComentario(e.target.value)} maxLength={500} />
          </Field>
          <div className="field">
            <span className="field-label">fotos do resultado (opcional)</span>
            <ImagePicker multiple limit={3} value={fotos} onChange={setFotos} label="clique para anexar fotos" max={800} />
          </div>
          <Button type="submit" block loading={busy}>Enviar avaliação</Button>
        </form>
      </AuthShell>
    </ThemeScope>
  )
}
