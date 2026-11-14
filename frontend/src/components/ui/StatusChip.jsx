const KIND = {
  // status do agendamento no backend
  PENDENTE: 'warn', APROVADO: 'ok', RECUSADO: 'bad', CANCELADO: 'bad', CONCLUIDO: 'ok',
  // usados pelas telas que ainda leem a API simulada
  confirmado: 'warn', finalizado: 'ok', cancelado: 'bad', embreve: 'warn', concluido: 'ok', publico: 'ok', oculto: 'warn',
}
const LABEL = {
  PENDENTE: 'pendente', APROVADO: 'aprovado', RECUSADO: 'recusado', CANCELADO: 'cancelado', CONCLUIDO: 'concluído',
  confirmado: 'confirmado', finalizado: 'finalizado', cancelado: 'cancelado', embreve: 'em breve', concluido: 'concluído', publico: 'público', oculto: 'oculto',
}

export default function StatusChip({ status }) {
  return <span className={`status-chip ${KIND[status] || 'warn'}`}>{LABEL[status] || status}</span>
}
