import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { homeDoPapel, useAuth } from '../context/AuthContext'
import { useCliente } from '../context/ClienteContext'

/** Exige login de responsavel/funcionario. Com `role`, redireciona quem tem outro papel para a propria home. */
export function RequireStaff({ role }) {
  const { usuario } = useAuth()
  if (!usuario) return <Navigate to="/login" replace />
  if (role && usuario.papel !== role) return <Navigate to={homeDoPapel(usuario)} replace />
  return <Outlet />
}

/** Exige o acesso temporario do cliente. Guarda a pagina de origem para voltar depois do codigo. */
export function RequireCliente() {
  const { autenticado } = useCliente()
  const { pathname } = useLocation()
  if (!autenticado) return <Navigate to="/acesso" replace state={{ voltar: pathname }} />
  return <Outlet />
}
