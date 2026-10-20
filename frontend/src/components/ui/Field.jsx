import { Children, cloneElement, forwardRef, useId, useState } from 'react'

/** Rotulo + controle + dica + erro, com todos os vinculos de acessibilidade (for, aria-invalid, aria-describedby). */
export function Field({ label, error, hint, required, children, className = '' }) {
  const id = useId()
  const child = Children.only(children)
  const describedBy = [error && `${id}-err`, hint && `${id}-hint`].filter(Boolean).join(' ') || undefined
  return (
    <div className={`field ${className}`}>
      <label className="field-label" htmlFor={id}>
        {label}
        {required && <span className="req" aria-hidden="true">*</span>}
      </label>
      {cloneElement(child, { id, 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {hint && !error && <div className="field-hint" id={`${id}-hint`}>{hint}</div>}
      {error && <div className="field-error" id={`${id}-err`} role="alert">{error}</div>}
    </div>
  )
}

/** Grupo de opcoes (radio / checkbox) com legenda. */
export function FieldGroup({ label, error, hint, children, className = '' }) {
  return (
    <fieldset className={`field ${className}`} style={{ border: 0, padding: 0, margin: '0 0 16px', minWidth: 0 }}>
      <legend className="field-label" style={{ padding: 0 }}>{label}</legend>
      {children}
      {hint && !error && <div className="field-hint">{hint}</div>}
      {error && <div className="field-error" role="alert">{error}</div>}
    </fieldset>
  )
}

/** Campo de senha com botao mostrar/ocultar. Repassa id e aria-* para o <input>. */
export const PasswordInput = forwardRef(function PasswordInput({ autoComplete = 'current-password', ...props }, ref) {
  const [visible, setVisible] = useState(false)
  return (
    <div className="input-row">
      <input ref={ref} type={visible ? 'text' : 'password'} autoComplete={autoComplete} {...props} />
      <button
        type="button" className="btn small ghost" onClick={() => setVisible((v) => !v)}
        aria-pressed={visible} aria-label={visible ? 'Ocultar senha' : 'Mostrar senha'}
      >
        {visible ? 'ocultar' : 'mostrar'}
      </button>
    </div>
  )
})
