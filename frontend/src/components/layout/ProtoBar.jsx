import { Fragment, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useCliente } from '../../context/ClienteContext'

/**
 * Barra de navegacao do prototipo (equivalente a barra preta do mockup.html).
 * Permite abrir QUALQUER tela com um clique, ja entrando com o perfil certo.
 * Aparece so com VITE_USE_MOCK=true e VITE_PROTO_BAR diferente de "false".
 */
const SALAO = '/barbearia-do-rafael'
const TELAS = {
  login: { to: '/login', sair: 'staff' },
  recuperar: { to: '/recuperar-senha', sair: 'staff' },
  painel: { to: '/painel', staff: 'administrador' },
  agendamentos: { to: '/painel/agendamentos', staff: 'administrador' },
  servicos: { to: '/painel/servicos', staff: 'administrador' },
  funcionarios: { to: '/painel/profissionais', staff: 'administrador' },
  config: { to: '/painel/configuracoes', staff: 'administrador' },
  agenda: { to: '/agenda', staff: 'funcionario' },
  token: { to: '/acesso', sair: 'cliente' },
  buscar: { to: '/buscar', cliente: true },
  portal: { to: SALAO },
  saiba: { to: `${SALAO}/saiba-mais` },
  agendar: { to: `${SALAO}/agendar` },
  historico: { to: '/meus-agendamentos', cliente: true },
  avaliacao: { to: '/avaliar/h2', cliente: true },
  oculto: { to: '/espaco-aurora' },
  inexistente: { to: '/nao-existe-mesmo' },
}
const GRUPOS = [
  { rotulo: 'RESPONSÁVEL:', itens: [['Login/Cadastro', 'login'], ['Recuperar senha', 'recuperar'], ['Meu portal', 'painel'], ['Agendamentos', 'agendamentos'], ['Serviços', 'servicos'], ['Funcionários', 'funcionarios'], ['Configuração', 'config']] },
  { rotulo: 'FUNCIONÁRIO:', itens: [['Minha agenda', 'agenda']] },
  { rotulo: 'CLIENTE:', itens: [['Acesso temporário', 'token'], ['Buscar salão', 'buscar'], ['Portal público', 'portal'], ['Saiba mais', 'saiba'], ['Agendamento', 'agendar'], ['Histórico', 'historico'], ['Avaliação', 'avaliacao']] },
  { rotulo: 'FALLBACK:', itens: [['Portal oculto', 'oculto'], ['Portal inexistente', 'inexistente']] },
]

function useDesktop() {
  const q = '(min-width: 900px)'
  const mq = () => (typeof window.matchMedia === 'function' ? window.matchMedia(q) : null)
  const [v, setV] = useState(() => mq()?.matches ?? true)
  useEffect(() => {
    const m = mq()
    if (!m) return undefined
    const on = () => setV(m.matches)
    m.addEventListener('change', on)
    return () => m.removeEventListener('change', on)
  }, [])
  return v
}

export default function ProtoBar() {
  const { logout, demoLogin } = useAuth()
  const { sair, demoEntrar } = useCliente()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const desktop = useDesktop()
  const [aberto, setAberto] = useState(false)
  const ref = useRef(null)

  const atual = Object.entries(TELAS).find(([, t]) => t.to === pathname)?.[0]

  // empurra a pagina para baixo na altura da barra (no desktop)
  useLayoutEffect(() => {
    const root = document.documentElement
    if (!desktop || !ref.current) { root.style.setProperty('--proto-h', '0px'); return undefined }
    const set = () => root.style.setProperty('--proto-h', `${ref.current.offsetHeight}px`)
    set()
    const ro = typeof ResizeObserver === 'function' ? new ResizeObserver(set) : null
    ro?.observe(ref.current)
    return () => { ro?.disconnect(); root.style.setProperty('--proto-h', '0px') }
  }, [desktop])

  const ir = (chave) => {
    const t = TELAS[chave]
    if (t.staff) demoLogin(t.staff)
    if (t.sair === 'staff') logout()
    if (t.cliente) demoEntrar()
    if (t.sair === 'cliente') sair()
    setAberto(false)
    navigate(t.to)
  }
  const reiniciar = () => window.__resetMock?.()

  const botoes = (g) => g.itens.map(([rotulo, chave]) => (
    <button key={chave} type="button" aria-current={atual === chave ? 'page' : undefined} onClick={() => ir(chave)}>{rotulo}</button>
  ))

  if (desktop) {
    return (
      <nav className="proto-bar" ref={ref} aria-label="Navegação do protótipo">
        {GRUPOS.map((g) => (
          <Fragment key={g.rotulo}>
            <span className="grp-label">{g.rotulo}</span>
            {botoes(g)}
          </Fragment>
        ))}
        <button type="button" className="reset" onClick={reiniciar} title="Volta os dados de exemplo ao estado inicial">reiniciar dados</button>
      </nav>
    )
  }

  return (
    <>
      <button type="button" className="proto-fab" onClick={() => setAberto(true)} aria-haspopup="dialog">telas</button>
      {aberto && (
        <div className="proto-sheet" role="dialog" aria-modal="true" aria-label="Navegação do protótipo" onClick={(e) => { if (e.target === e.currentTarget) setAberto(false) }}>
          <div className="sheet">
            <div className="sheet-head"><span>Ir para a tela</span><button type="button" onClick={() => setAberto(false)}>fechar</button></div>
            {GRUPOS.map((g) => (
              <div key={g.rotulo}>
                <span className="grp-label">{g.rotulo}</span>
                <div className="items">{botoes(g)}</div>
              </div>
            ))}
            <div>
              <span className="grp-label">DADOS:</span>
              <div className="items"><button type="button" onClick={reiniciar}>reiniciar dados de exemplo</button></div>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
