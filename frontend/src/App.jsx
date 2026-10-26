import { lazy, Suspense } from 'react'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ClienteProvider } from './context/ClienteContext'
import { ToastProvider } from './context/ToastContext'
import AppRoutes from './routes/AppRoutes'
// SOMENTE TESTE: carregado sob demanda e removido do build de deploy (ver lib/flags.js e vite.config.js)
const ProtoBar = __MOSTRAR_PROTO__ ? lazy(() => import('./components/layout/ProtoBar')) : null

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <ClienteProvider>
            <a href="#conteudo" className="skip-link">Pular para o conteúdo</a>
            {ProtoBar && <Suspense fallback={null}><ProtoBar /></Suspense>}
            <AppRoutes />
          </ClienteProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
