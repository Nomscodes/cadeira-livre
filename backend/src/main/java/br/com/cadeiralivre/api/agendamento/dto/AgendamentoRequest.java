package br.com.cadeiralivre.api.agendamento.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dados que o cliente envia no formulário de agendamento (RF14). */
public record AgendamentoRequest(
        @NotNull UUID servicoId,
        @NotNull LocalDate data,
        @NotNull LocalTime hora,
        @NotBlank @Size(max = 120) String clienteNome,
        @NotBlank @Size(max = 20) String clienteTelefone) {
}
