import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { Menu, X } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync, useDocumentTitle } from '../../lib/hooks'
import { initials } from '../../lib/format'
import { useAuth } from '../../context/AuthContext'
import { ConfirmDialog } from '../ui/Modal'
import { Loading, ErrorState } from '../ui/States'
import StatusChip from '../ui/StatusChip'
import NotificationBell from './NotificationBell'
import Frame from './Frame'
import ThemeScope from '../../theme/ThemeScope'

const PainelCtx = createContext(null)
export const usePainel = () => useContext(PainelCtx)

const ITENS = [
  { to: '/painel', label: 'Meu portal', menu: 'meu portal', end: true },
  { to: '/painel/agendamentos', label: 'Agendamentos', menu: 'agendamentos' },
  { to: '/painel/servicos', label: 'Serviços', menu: 'serviços' },
  { to: '/painel/profissionais', label: 'Profissionais', menu: 'profissionais' },
  { to: '/painel/configuracoes', label: 'Configurações', menu: 'configurações' },
]

export default function PainelLayout() {
  const { usuario, logout } = useAuth()
  const { pathname } = useLocation()
  const [menuAberto, setMenuAberto] = useState(false)
  const [sair, setSair] = useState(false)
  const [previaTema, setPreviaTema] = useState(null) // cor em teste na tela Meu portal, ainda nao salva
  const { data: salao, loading, error, reload, setData } = useAsync(() => api.get('/painel/portal', { as: 'staff' }), [])

  const atual = ITENS.find((i) => (i.end ? pathname === i.to : pathname.startsWith(i.to)))
  useDocumentTitle(atual?.label)
  useEffect(() => { setMenuAberto(false) }, [pathname])

  // gaveta no celular: foco vai para o botao de fechar e Esc fecha
  const fecharRef = useRef(null)
  useEffect(() => {
    if (!menuAberto) return undefined
    fecharRef.current?.focus()
    const onKey = (e) => { if (e.key === 'Escape') setMenuAberto(false) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [menuAberto])

  return (
    <PainelCtx.Provider value={{ salao, reload, setSalao: setData, setPreviaTema }}>
    <ThemeScope themeId={previaTema || salao?.temaId}>
      <Frame>
      <div className="admin-layout">
        <div className={`side-backdrop${menuAberto ? ' open' : ''}`} onClick={() => setMenuAberto(false)} />
        <aside className={`sidebar${menuAberto ? ' open' : ''}`} aria-label="Menu do painel">
          <button type="button" className="side-close" ref={fecharRef} onClick={() => setMenuAberto(false)} aria-label="Fechar menu"><X size={20} aria-hidden="true" /></button>
          <div className="side-logo">{salao?.nome || 'Cadeira Livre'}</div>
          <nav className="side-nav">
            {ITENS.map(({ to, menu, end }) => (
              <NavLink key={to} to={to} end={end} className="side-item">{menu}</NavLink>
            ))}
            <button type="button" className="side-item" onClick={() => setSair(true)}>sair</button>
          </nav>
        </aside>

        <div className="admin-main">
          <header className="topbar-admin">
            <h1 className="topbar-title">
              <button type="button" className="menu-btn admin" onClick={() => setMenuAberto(true)} aria-label="Abrir menu"><Menu size={22} aria-hidden="true" /></button>
              <span>{atual?.label}</span>
            </h1>
            <div className="topbar-right">
              {salao && <span className="hide-sm"><StatusChip status={salao.publico ? 'publico' : 'oculto'} /></span>}
              <NotificationBell />
              <div className="avatar-round hide-sm" title={usuario?.nome} aria-hidden="true">{initials(usuario?.nome)}</div>
            </div>
          </header>
          <main className="panel" id="conteudo">
            {loading && <Loading />}
            {error && <ErrorState error={error} onRetry={reload} />}
            {salao && <Outlet />}
          </main>
        </div>
      </div>
      </Frame>

      {sair && (
        <ConfirmDialog
          title="Sair da conta?" message="Tem certeza que deseja sair da sua conta?" confirmLabel="Sair"
          onClose={() => setSair(false)} onConfirm={() => logout()}
        />
      )}
    </ThemeScope>
    </PainelCtx.Provider>
  )
}

