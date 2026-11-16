export default function Toggle({ checked, onChange, label, disabled, id }) {
  return (
    <button
      type="button" role="switch" id={id} className="toggle" aria-checked={checked}
      aria-label={label} disabled={disabled} onClick={() => onChange(!checked)}
    >
      <span className="dot" />
    </button>
  )
}
