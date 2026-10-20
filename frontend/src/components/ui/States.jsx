import Button from './Button'

export function Loading({ label = 'Carregando...' }) {
  return (
    <div className="loading" role="status">
      <div className="spinner" aria-hidden="true" />
      {label}
    </div>
  )
}

export function ErrorState({ error, onRetry }) {
  return (
    <div className="empty-state" role="alert">
      <p style={{ marginBottom: 12 }}>{error?.message || 'Não foi possível carregar. Tente novamente.'}</p>
      {onRetry && <Button small variant="ghost" onClick={onRetry}>Tentar de novo</Button>}
    </div>
  )
}

export function EmptyState({ icon: Icon, children, action }) {
  return (
    <div className="empty-state">
      {Icon && <Icon className="big-icon" size={34} aria-hidden="true" />}
      <div>{children}</div>
      {action && <div style={{ marginTop: 14 }}>{action}</div>}
    </div>
  )
}
