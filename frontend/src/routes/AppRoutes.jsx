import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { RequireCliente, RequireStaff } from './guards'
import NotFoundPage from './NotFoundPage'

import LoginPage from '../features/auth/LoginPage'
import RecuperarSenhaPage from '../features/auth/RecuperarSenhaPage'
import AcessoPage from '../features/auth/AcessoPage'

import ClienteLayout from '../components/layout/ClienteLayout'
import BuscarPage from '../features/cliente/BuscarPage'
import HistoricoPage from '../features/cliente/HistoricoPage'
import AvaliarPage from '../features/cliente/AvaliarPage'

import PortalLayout from '../components/layout/PortalLayout'
import PortalHome from '../features/portal/PortalHome'
import PortalSaibaMais from '../features/portal/PortalSaibaMais'
import AgendarPage from '../features/portal/AgendarPage'

import PainelLayout from '../components/layout/PainelLayout'
import PortalPage from '../features/painel/PortalPage'
import AgendamentosPage from '../features/painel/AgendamentosPage'
import ServicosPage from '../features/painel/ServicosPage'
import ProfissionaisPage from '../features/painel/ProfissionaisPage'
import ConfigPage from '../features/painel/ConfigPage'

import AgendaPage from '../features/agenda/AgendaPage'

function ScrollToTop() {
  const { pathname } = useLocation()
  useEffect(() => { window.scrollTo(0, 0) }, [pathname])
  return null
}

/**
 * Mapa de rotas do front.
 * Rotas fixas vem antes de /:slug (o react-router da prioridade a rota mais especifica).
 * Slugs reservados (nao podem ser nome de salao): login, recuperar-senha, acesso, buscar,
 * meus-agendamentos, avaliar, painel, agenda, api.
 */
export default function AppRoutes() {
  return (
    <>
      <ScrollToTop />
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />

        {/* entrada e autenticacao */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/recuperar-senha" element={<RecuperarSenhaPage />} />
        <Route path="/acesso" element={<AcessoPage />} />

        {/* cliente com acesso temporario */}
        <Route element={<RequireCliente />}>
          <Route path="/buscar" element={<BuscarPage />} />
          <Route element={<ClienteLayout />}>
            <Route path="/meus-agendamentos" element={<HistoricoPage />} />
          </Route>
          <Route path="/avaliar/:id" element={<AvaliarPage />} />
        </Route>

        {/* responsavel */}
        <Route element={<RequireStaff role="administrador" />}>
          <Route path="/painel" element={<PainelLayout />}>
            <Route index element={<PortalPage />} />
            <Route path="agendamentos" element={<AgendamentosPage />} />
            <Route path="servicos" element={<ServicosPage />} />
            <Route path="profissionais" element={<ProfissionaisPage />} />
            <Route path="configuracoes" element={<ConfigPage />} />
          </Route>
        </Route>

        {/* funcionario */}
        <Route element={<RequireStaff role="funcionario" />}>
          <Route path="/agenda" element={<AgendaPage />} />
        </Route>

        {/* portal publico do salao (tema de cor do salao) */}
        <Route path="/:slug" element={<PortalLayout />}>
          <Route index element={<PortalHome />} />
          <Route path="agendar" element={<AgendarPage />} />
          <Route path="saiba-mais" element={<PortalSaibaMais />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </>
  )
}
