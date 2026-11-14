/** Status do agendamento, com os mesmos nomes do backend (enum StatusAgendamento). */
export const STATUS = {
  PENDENTE: 'PENDENTE', // pedido feito, esperando o dono aprovar ou recusar
  APROVADO: 'APROVADO',
  RECUSADO: 'RECUSADO',
  CANCELADO: 'CANCELADO', // cancelado pelo cliente
  CONCLUIDO: 'CONCLUIDO', // atendimento feito; libera a avaliação
}
