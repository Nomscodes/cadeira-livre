import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync } from '../../lib/hooks'
import { tempoRelativo } from '../../lib/dates'

// etiqueta curta de cada tipo (os tipos vem do backend em GET /cliente/notificacoes)
const TIPOS_CLIENTE = {
  HORARIO_APROVADO: 'confirmado',
  HORARIO_RECUSADO: 'não aceito',
  ATENDIMENTO_CONCLUIDO: 'avaliação',
  LEMBRETE_CLIENTE: 'lembrete',
}

/** Sino da area do cliente: lista os avisos dele; clicar marca como lido e leva para a tela certa. */
export default function ClienteNotificationBell() {
  const navigate = useNavigate()
  const { data, setData } = useAsync(() => api.get('/cliente/notificacoes', { as: 'cliente' }), [])
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const lista = data || []
  const naoLidas = lista.filter((n) => !n.lida).length

  useEffect(() => {
    if (!open) return undefined
    const onDown = (e) => { if (!ref.current?.contains(e.target)) setOpen(false) }
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onDown); document.removeEventListener('keydown', onKey) }
  }, [open])

  // no celular o sino fica dentro do menu: abrir o sino nao pode fechar o menu
  const alternar = (e) => {
    e.stopPropagation()
    setOpen((o) => !o)
  }

  const abrir = async (n) => {
    setOpen(false)
    if (!n.lida) {
      setData((l) => (l || []).map((x) => (x.id === n.id ? { ...x, lida: true } : x)))
      try { await api.post(`/cliente/notificacoes/${n.id}/lida`, {}, { as: 'cliente' }) } catch { /* mantem como lida na tela */ }
    }
    // atendimento concluido leva direto para a avaliacao; os outros, para o historico
    navigate(n.tipo === 'ATENDIMENTO_CONCLUIDO' ? `/avaliar/${n.agendamentoId}` : '/meus-agendamentos')
  }

  return (
    <div className="bell-wrap" ref={ref}>
      <button type="button" className="bell-btn" aria-expanded={open} aria-haspopup="true" aria-label={`Notificações${naoLidas ? `, ${naoLidas} não lidas` : ''}`} onClick={alternar}>
        <Bell size={18} aria-hidden="true" />
        {naoLidas > 0 && <span className="bell-badge" aria-hidden="true">{naoLidas}</span>}
      </button>
      {open && (
        <div className="bell-panel" role="menu" aria-label="Notificações">
          {lista.length === 0 && <div className="bell-item muted">Nenhuma notificação por enquanto.</div>}
          {lista.map((n) => (
            <button key={n.id} type="button" role="menuitem" className={`bell-item as-button${n.lida ? '' : ' unread'}`} onClick={() => abrir(n)}>
              <span className="tag">{TIPOS_CLIENTE[n.tipo] || n.tipo}<em>{tempoRelativo(n.criadaEm)}</em></span>
              <strong>{n.titulo}</strong>
              <span className="preview">{n.texto}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
