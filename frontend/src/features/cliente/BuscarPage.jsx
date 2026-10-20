import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Scissors } from 'lucide-react'
import logo from '../../assets/logo-wordmark.png'
import Frame from '../../components/layout/Frame'
import { api } from '../../lib/http'
import { useAsync, useDebounced, useDocumentTitle } from '../../lib/hooks'
import { initials } from '../../lib/format'
import { getTheme } from '../../theme/salonThemes'
import { Field } from '../../components/ui/Field'
import { EmptyState, ErrorState, Loading } from '../../components/ui/States'

export default function BuscarPage() {
  useDocumentTitle('Buscar salões')
  const [f, setF] = useState({ cidade: 'Goiânia, GO', categoria: 'todas', servico: '', nome: '' })
  const debounced = useDebounced(f, 250)
  const { data, loading, error, reload } = useAsync(() => api.get('/saloes', { query: debounced }), [debounced])
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }))

  return (
    <Frame>
      <header className="topbar-simple">
        <Link to="/buscar" className="logo-box" aria-label="Cadeira Livre"><img src={logo} alt="Cadeira Livre" style={{ height: 32 }} /></Link>
        <Link to="/meus-agendamentos" className="link-small">meus agendamentos</Link>
      </header>
      <main className="screen-pad lg" id="conteudo">
      <h1 className="sr-only">Encontre seu salão</h1>
      <form className="search-grid" onSubmit={(e) => e.preventDefault()} role="search">
        <Field label="localização detectada"><input type="text" value={f.cidade} onChange={set('cidade')} autoComplete="address-level2" /></Field>
        <Field label="categoria">
          <select value={f.categoria} onChange={set('categoria')}>
            <option value="todas">todas</option>
            <option value="barbearia">barbearia</option>
            <option value="salão de beleza">salão de beleza</option>
            <option value="unhas">unhas</option>
          </select>
        </Field>
        <Field label="serviço"><input type="search" placeholder="ex: corte, coloração" value={f.servico} onChange={set('servico')} /></Field>
        <Field label="nome do salão"><input type="search" placeholder="buscar por nome" value={f.nome} onChange={set('nome')} /></Field>
      </form>

      <div aria-live="polite">
        {loading && !data && <Loading label="Buscando salões..." />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {data && data.length === 0 && (
          <EmptyState icon={Scissors} action={<Link to="/login?aba=cadastro" className="btn small">é um profissional estético? venha registrar seu salão conosco</Link>}>
            não há salões registrados na sua região
          </EmptyState>
        )}
        {data && data.length > 0 && (
          <div className="grid-cards" style={{ opacity: loading ? 0.6 : 1 }}>
            {data.map((s) => (
              <Link key={s.id} to={`/${s.slug}`} className="card-box" style={{ '--c-primary': getTheme(s.temaId).tokens['--c-primary'], '--c-soft': getTheme(s.temaId).tokens['--c-soft'], '--c-ink': getTheme(s.temaId).tokens['--c-ink'] }}>
                <div className="accent-strip" />
                <div className="thumb">{s.logo ? <img src={s.logo} alt="" /> : initials(s.nome)}</div>
                <h2 className="card-title" style={{ color: 'var(--c-ink)' }}>{s.nome}</h2>
                <div className="meta" style={{ marginBottom: 0 }}>{s.cidade} &middot; {s.categoria}</div>
              </Link>
            ))}
          </div>
        )}
      </div>
      </main>
    </Frame>
  )
}
