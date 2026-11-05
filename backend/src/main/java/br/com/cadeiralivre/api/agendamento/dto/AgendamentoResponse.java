package br.com.cadeiralivre.api.agendamento.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;

public record AgendamentoResponse(UUID id, StatusAgendamento status, String servico, LocalDate data,
                                  LocalTime horaInicio, LocalTime horaFim,
                                  String clienteNome, String clienteTelefone) {

    /** Deve ser chamado dentro de transação (servico é LAZY). */
    public static AgendamentoResponse de(Agendamento a) {
        return new AgendamentoResponse(a.getId(), a.getStatus(), a.getServico().getNome(), a.getData(),
                a.getHoraInicio(), a.getHoraFim(), a.getClienteNome(), a.getClienteTelefone());
    }
}
