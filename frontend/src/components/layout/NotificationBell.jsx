import { useEffect, useRef, useState } from 'react'
import { Bell } from 'lucide-react'
import { api } from '../../lib/http'
import { useAsync } from '../../lib/hooks'
import { tempoRelativo } from '../../lib/dates'
import NotificationModal, { TIPOS } from './NotificationModal'

/** Sino: lista as notificacoes (sem botoes de acao) e abre um modal ao clicar em qualquer uma. */
export default function NotificationBell() {
  const { data, setData, reload } = useAsync(() => api.get('/notificacoes', { as: 'staff' }), [])
  const [open, setOpen] = useState(false)
  const [aberta, setAberta] = useState(null)
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

  const abrir = async (n) => {
    setOpen(false)
    setAberta(n)
    if (!n.lida) {
      setData((l) => (l || []).map((x) => (x.id === n.id ? { ...x, lida: true } : x)))
      try { await api.post(`/notificacoes/${n.id}/lida`, {}, { as: 'staff' }) } catch { /* mantem como lida na tela */ }
    }
  }

  // depois de uma acao (aviso/avaliacao), recarrega para refletir o estado e atualiza a aberta
  const atualizar = async () => {
    await reload()
    setAberta(null)
  }

  return (
    <div className="bell-wrap" ref={ref}>
      <button type="button" className="bell-btn" aria-expanded={open} aria-haspopup="true" aria-label={`Notificações${naoLidas ? `, ${naoLidas} não lidas` : ''}`} onClick={() => setOpen((o) => !o)}>
        <Bell size={18} aria-hidden="true" />
        {naoLidas > 0 && <span className="bell-badge" aria-hidden="true">{naoLidas}</span>}
      </button>
      {open && (
        <div className="bell-panel" role="menu" aria-label="Notificações">
          {lista.length === 0 && <div className="bell-item muted">Nenhuma notificação por enquanto.</div>}
          {lista.map((n) => (
            <button key={n.id} type="button" role="menuitem" className={`bell-item as-button${n.lida ? '' : ' unread'}`} onClick={() => abrir(n)}>
              <span className="tag">{TIPOS[n.tipo] || n.tipo}<em>{tempoRelativo(n.criadaEm)}</em></span>
              <strong>{n.titulo}</strong>
              <span className="preview">{n.texto}</span>
            </button>
          ))}
        </div>
      )}
      {aberta && <NotificationModal notificacao={aberta} onClose={() => setAberta(null)} onAtualizar={atualizar} />}
    </div>
  )
}
