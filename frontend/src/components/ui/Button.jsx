export default function Button({ variant, small, block, className = '', type = 'button', loading, children, disabled, ...rest }) {
  const cls = ['btn', variant, small && 'small', block && 'block', className].filter(Boolean).join(' ')
  return (
    <button type={type} className={cls} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {loading ? 'Aguarde...' : children}
    </button>
  )
}
