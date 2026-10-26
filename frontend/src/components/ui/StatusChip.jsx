const KIND = { confirmado: 'warn', finalizado: 'ok', cancelado: 'bad', embreve: 'warn', concluido: 'ok', publico: 'ok', oculto: 'warn' }
const LABEL = { confirmado: 'confirmado', finalizado: 'finalizado', cancelado: 'cancelado', embreve: 'em breve', concluido: 'concluído', publico: 'público', oculto: 'oculto' }

export default function StatusChip({ status }) {
  return <span className={`status-chip ${KIND[status] || 'warn'}`}>{LABEL[status] || status}</span>
}
