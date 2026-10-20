import { useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'
import Button from './Button'

const FOCUSABLE = 'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'

/** Dialogo acessivel: foco preso, Esc fecha, devolve o foco ao elemento anterior, trava a rolagem do fundo. */
export function Modal({ title, onClose, children, narrow }) {
  const ref = useRef(null)
  const titleId = useId()
  const closeRef = useRef(onClose)
  closeRef.current = onClose

  useEffect(() => {
    const prev = document.activeElement
    const el = ref.current
    const first = el.querySelector('input:not([type=hidden]),select,textarea') || el
    first.focus({ preventScroll: true })

    const onKey = (e) => {
      if (e.key === 'Escape') { e.stopPropagation(); closeRef.current() }
      if (e.key !== 'Tab') return
      const items = [...el.querySelectorAll(FOCUSABLE)]
      if (!items.length) return
      const a = items[0]
      const z = items[items.length - 1]
      if (e.shiftKey && document.activeElement === a) { e.preventDefault(); z.focus() }
      else if (!e.shiftKey && document.activeElement === z) { e.preventDefault(); a.focus() }
    }
    document.addEventListener('keydown', onKey)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
      if (prev && prev.focus) prev.focus({ preventScroll: true })
    }
  }, [])

  return createPortal(
    <div className="overlay" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className={`modal${narrow ? ' narrow' : ''}`} role="dialog" aria-modal="true" aria-labelledby={titleId} ref={ref} tabIndex={-1}>
        <button type="button" className="modal-close" onClick={onClose} aria-label="Fechar janela"><X size={20} aria-hidden="true" /></button>
        <h3 className="modal-title" id={titleId}>{title}</h3>
        {children}
      </div>
    </div>,
    document.body,
  )
}

/** Confirmacao simples. Com `requireText`, a pessoa precisa digitar a palavra para liberar o botao. */
export function ConfirmDialog({ title, message, confirmLabel = 'Confirmar', danger, requireText, onConfirm, onClose }) {
  const [typed, setTyped] = useState('')
  const [busy, setBusy] = useState(false)
  const ok = !requireText || typed.trim().toUpperCase() === requireText.toUpperCase()

  const run = async () => {
    setBusy(true)
    try { await onConfirm() } finally { setBusy(false) }
  }

  return (
    <Modal title={title} onClose={onClose} narrow>
      <div className="stack">
        <p style={{ margin: 0 }}>{message}</p>
        {requireText && (
          <div className="field" style={{ marginBottom: 0 }}>
            <label className="field-label" htmlFor="confirm-typed">Para continuar, digite {requireText}</label>
            <input id="confirm-typed" type="text" value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" />
          </div>
        )}
      </div>
      <div className="modal-actions">
        <Button variant="ghost" onClick={onClose}>Cancelar</Button>
        <Button variant={danger ? 'danger-outline' : undefined} disabled={!ok} loading={busy} onClick={run}>{confirmLabel}</Button>
      </div>
    </Modal>
  )
}
