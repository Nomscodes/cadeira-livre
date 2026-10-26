import { Link } from 'react-router-dom'
import logo from '../../assets/logo-wordmark.png'
import Frame from './Frame'

/**
 * Moldura das telas soltas.
 *  back      link "← voltar" no topo (recuperar senha, acesso, avaliacao)
 *  ambiente  mostra "Ambiente: Web · Desktop" a direita (somente no login)
 *  bare      sem barra superior (portal oculto / pagina inexistente)
 */
export default function AuthShell({ back, ambiente, bare, children, cardClass = '' }) {
  return (
    <Frame>
      {!bare && (
        <header className="topbar-simple">
          {back ? (
            <Link to={back.to} className="back"><span aria-hidden="true">&larr;</span> {back.label}</Link>
          ) : (
            <Link to="/" className="logo-box" aria-label="Cadeira Livre, início"><img src={logo} alt="Cadeira Livre" /></Link>
          )}
          {ambiente ? <span>Ambiente: Web &middot; Desktop</span> : <span />}
        </header>
      )}
      <main className="center-col" id="conteudo">
        <div className={`auth-card ${cardClass}`}>{children}</div>
      </main>
    </Frame>
  )
}
