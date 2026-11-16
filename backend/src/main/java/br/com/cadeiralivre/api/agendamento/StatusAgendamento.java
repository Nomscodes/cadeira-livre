package br.com.cadeiralivre.api.agendamento;

/** Estados do agendamento conforme o documento v7: nasce PENDENTE e o dono aprova ou recusa. */
public enum StatusAgendamento {
    PENDENTE,
    APROVADO,
    RECUSADO,
    CANCELADO,
    CONCLUIDO
}
