/** Status do agendamento, com os mesmos nomes do backend (enum StatusAgendamento). */
export const STATUS = {
  PENDENTE: 'PENDENTE', // pedido feito, esperando o dono aprovar ou recusar
  APROVADO: 'APROVADO',
  RECUSADO: 'RECUSADO',
  CANCELADO: 'CANCELADO', // cancelado pelo cliente
  CONCLUIDO: 'CONCLUIDO', // atendimento feito; libera a avaliação
}

// grupo de cada status nas telas do cliente; também aceita os nomes da API simulada (confirmado, finalizado, cancelado)
const GRUPO = {
  PENDENTE: 'confirmado', APROVADO: 'confirmado', CONCLUIDO: 'finalizado', CANCELADO: 'cancelado', RECUSADO: 'cancelado',
  confirmado: 'confirmado', finalizado: 'finalizado', cancelado: 'cancelado',
}

/**
 * Agrupa o status para a tela do cliente: 'confirmado' (ainda vai acontecer: pode cancelar),
 * 'finalizado' (já aconteceu: pode avaliar) ou 'cancelado' (cancelado ou recusado pelo salão).
 */
export const grupoDoStatus = (status) => GRUPO[status] || 'confirmado'
