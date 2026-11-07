package br.com.cadeiralivre.api.agendamento.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;

/** Agendamento visto pelo cliente: mostra a barbearia (ele pode agendar em várias) e não repete os dados dele. */
public record MeuAgendamentoResponse(UUID id, StatusAgendamento status, String barbearia, String slug,
                                     String servico, String profissional, LocalDate data,
                                     LocalTime horaInicio, LocalTime horaFim) {

    /** Deve ser chamado dentro de transação (barbearia, servico e profissional são LAZY). */
    public static MeuAgendamentoResponse de(Agendamento a) {
        return new MeuAgendamentoResponse(a.getId(), a.getStatus(), a.getBarbearia().getNomeFantasia(),
                a.getBarbearia().getSlug(), a.getServico().getNome(), a.getProfissional().getNome(),
                a.getData(), a.getHoraInicio(), a.getHoraFim());
    }
}
