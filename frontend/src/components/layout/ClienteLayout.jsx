import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Menu, X } from 'lucide-react'
import logo from '../../assets/logo-mark.png'
import { useCliente } from '../../context/ClienteContext'
import Frame from './Frame'

/** Historico do cliente: cabecalho com a marca Cadeira Livre (header-client do mockup). */
export default function ClienteLayout() {
  const { sair } = useCliente()
  const navigate = useNavigate()
  const [aberto, setAberto] = useState(false)

  return (
    <Frame>
      <header className="client-header">
        <div className="client-header-inner">
          <Link to="/buscar" className="brand" aria-label="Cadeira Livre, buscar salões">
            <span className="logo64 plain"><img src={logo} alt="" /></span>
            <span className="brand-name">Cadeira Livre</span>
          </Link>
          <button type="button" className="menu-btn" aria-expanded={aberto} aria-controls="nav-cliente" aria-label={aberto ? 'Fechar menu' : 'Abrir menu'} onClick={() => setAberto((a) => !a)}>
            {aberto ? <X size={22} aria-hidden="true" /> : <Menu size={22} aria-hidden="true" />}
          </button>
          <nav id="nav-cliente" className={`nav-client${aberto ? ' open' : ''}`} aria-label="Principal" onClick={() => setAberto(false)}>
            <NavLink to="/buscar">buscar salões</NavLink>
            <NavLink to="/meus-agendamentos">meus agendamentos</NavLink>
            <button type="button" className="navlink" onClick={() => { sair(); navigate('/acesso') }}>sair</button>
          </nav>
        </div>
      </header>
      <main className="page-main" id="conteudo"><Outlet /></main>
    </Frame>
  )
}
