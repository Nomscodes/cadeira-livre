import { Link } from 'react-router-dom'
import { HelpCircle } from 'lucide-react'
import { useDocumentTitle } from '../lib/hooks'
import AuthShell from '../components/layout/AuthShell'

export default function NotFoundPage() {
  useDocumentTitle('Página não encontrada')
  return (
    <AuthShell bare cardClass="center">
      <HelpCircle className="state-icon" size={34} aria-hidden="true" />
      <h3>Esta página não existe</h3>
      <p className="hint">confira se o endereço digitado está correto</p>
      <Link to="/buscar" className="btn">Buscar salões</Link>
    </AuthShell>
  )
}
