package br.com.cadeiralivre.api.agendamento.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Horários livres de um dia para um serviço, separados por profissional: o cliente escolhe com quem e quando. */
public record DisponibilidadeResponse(LocalDate data, UUID servicoId, int duracaoMinutos,
                                      List<ProfissionalDisponivel> profissionais) {

    // lista de horários vazia = o profissional não tem horário livre nesse dia
    public record ProfissionalDisponivel(UUID id, String nome, List<LocalTime> horarios) {
    }
}
