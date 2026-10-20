import { createContext, useContext, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useParams } from 'react-router-dom'
import { Clock3, HelpCircle, Menu, X } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync, useDocumentTitle } from '../../lib/hooks'
import { initials } from '../../lib/format'
import { resumoHorarios } from '../../lib/dates'
import { useCliente } from '../../context/ClienteContext'
import ThemeScope from '../../theme/ThemeScope'
import { Loading, ErrorState } from '../ui/States'
import Button from '../ui/Button'
import AuthShell from './AuthShell'
import Frame from './Frame'

const PortalCtx = createContext(null)
export const usePortal = () => useContext(PortalCtx)

function PortalVazio({ oculto }) {
  const Icon = oculto ? Clock3 : HelpCircle
  return (
    <AuthShell bare cardClass="center">
      <Icon className="state-icon" size={34} aria-hidden="true" />
      <h3>{oculto ? 'Este salão vai voltar em breve' : 'Esta página não existe'}</h3>
      <p className="hint">{oculto ? 'o responsável ocultou temporariamente esta página' : 'confira se o endereço digitado está correto'}</p>
      <Link to="/buscar" className="btn">Buscar salões</Link>
    </AuthShell>
  )
}

/** Moldura do portal publico de um salao. E a unica area em que o tema de cor do salao e aplicado. */
export default function PortalLayout() {
  const { slug } = useParams()
  const { pathname } = useLocation()
  const { autenticado } = useCliente()
  const [menu, setMenu] = useState(false)
  const [mapa, setMapa] = useState(false)
  const { data: salao, loading, error, reload } = useAsync(() => api.get(`/saloes/${slug}`), [slug])
  useDocumentTitle(salao?.nome)

  if (loading) return <Loading label="Abrindo o portal..." />
  if (error?.status === 404) return <PortalVazio />
  if (error) return <div className="container" style={{ paddingTop: 40 }}><ErrorState error={error} onRetry={reload} /></div>
  if (salao.publico === false) return <PortalVazio oculto />

  const base = `/${salao.slug}`
  const maps = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(salao.endereco || salao.nome)}`
  // no mockup a tela de agendamento nao tem cabecalho nem rodape do salao
  const solto = pathname.endsWith('/agendar')

  return (
    <ThemeScope themeId={salao.temaId}>
      <PortalCtx.Provider value={{ salao, base }}>
        <Frame>
          {!solto && (
            <header className="client-header">
              <div className="client-header-inner">
                <Link to={base} className="brand">
                  <span className="logo64">{salao.logo ? <img src={salao.logo} alt="" /> : initials(salao.nome)}</span>
                  <span className="brand-name">{salao.nome}</span>
                </Link>
                <button type="button" className="menu-btn" aria-expanded={menu} aria-controls="nav-portal" aria-label={menu ? 'Fechar menu' : 'Abrir menu'} onClick={() => setMenu((m) => !m)}>
                  {menu ? <X size={22} aria-hidden="true" /> : <Menu size={22} aria-hidden="true" />}
                </button>
                <nav id="nav-portal" className={`nav-client${menu ? ' open' : ''}`} aria-label="Portal do salão" onClick={() => setMenu(false)}>
                  <NavLink to={base} end>início</NavLink>
                  <NavLink to={`${base}/agendar`}>agendamento</NavLink>
                  <NavLink to={`${base}/saiba-mais`}>saiba mais</NavLink>
                  {autenticado
                    ? <NavLink to="/meus-agendamentos">meus agendamentos</NavLink>
                    : <Link to="/acesso" state={{ voltar: pathname }}>entrar</Link>}
                </nav>
              </div>
            </header>
          )}

          <main className="page-main" id="conteudo"><Outlet /></main>

          {!solto && (
            <>
              <footer className="portal-footer">
                <div>
                  {salao.endereco && <div>{salao.endereco}</div>}
                  <div>
                    {salao.whatsapp && <a href={`tel:${salao.whatsapp.replace(/\D/g, '')}`}>{salao.whatsapp}</a>}
                    {resumoHorarios(salao.horarios) && <> &middot; {resumoHorarios(salao.horarios)}</>}
                  </div>
                </div>
                {salao.endereco && (
                  <Button small variant="ghost" aria-expanded={mapa} onClick={() => setMapa((m) => !m)}>{mapa ? 'esconder mapa' : 'ver mapa'}</Button>
                )}
              </footer>
              {mapa && (
                <div className="map-box">
                  <p style={{ margin: '0 0 12px' }}>{salao.endereco}</p>
                  <a className="btn small" href={maps} target="_blank" rel="noopener noreferrer">Abrir no Google Maps</a>
                </div>
              )}
            </>
          )}
        </Frame>
      </PortalCtx.Provider>
    </ThemeScope>
  )
}
