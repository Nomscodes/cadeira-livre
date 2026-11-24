import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ClienteProvider } from './context/ClienteContext'
import { ToastProvider } from './context/ToastContext'
import AppRoutes from './routes/AppRoutes'

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <ClienteProvider>
            <a href="#conteudo" className="skip-link">Pular para o conteúdo</a>
            <AppRoutes />
          </ClienteProvider>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
