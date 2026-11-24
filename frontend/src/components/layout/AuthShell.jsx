import { Link } from 'react-router-dom'
import logo from '../../assets/logo-wordmark.png'
import Frame from './Frame'

/**
 * Moldura das telas soltas.
 *  back      link "← voltar" no topo (area do cliente, avaliacao)
 *  bare      sem barra superior (portal oculto / pagina inexistente)
 */
export default function AuthShell({ back, bare, children, cardClass = '' }) {
  return (
    <Frame>
      {!bare && (
        <header className="topbar-simple">
          {back ? (
            <Link to={back.to} className="back"><span aria-hidden="true">&larr;</span> {back.label}</Link>
          ) : (
            <Link to="/" className="logo-box" aria-label="Cadeira Livre, início"><img src={logo} alt="Cadeira Livre" /></Link>
          )}
        </header>
      )}
      <main className="center-col" id="conteudo">
        <div className={`auth-card ${cardClass}`}>{children}</div>
      </main>
    </Frame>
  )
}
