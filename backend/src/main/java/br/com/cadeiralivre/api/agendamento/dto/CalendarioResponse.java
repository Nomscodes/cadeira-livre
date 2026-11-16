package br.com.cadeiralivre.api.agendamento.dto;

import java.time.LocalDate;

/** Respostas do calendário de agendamento do cliente (RF13), no formato que o front usa. */
public final class CalendarioResponse {

    private CalendarioResponse() {
    }

    /**
     * Estado de um dia do mês: passado | bloqueado (o salão fechou o dia, com o motivo) | fechado (o salão não abre
     * nesse dia da semana) | folga (algum profissional escolhido não trabalha) | lotado | ok.
     */
    public record Dia(LocalDate data, String estado, String motivo) {
    }

    /** Horário de início possível no dia ("09:30") e se ainda está livre. */
    public record Horario(String hora, boolean livre) {
    }
}
