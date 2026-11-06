package br.com.cadeiralivre.api.horario.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BloqueioRequest(
        @NotNull LocalDate data,
        // as duas horas vazias = bloqueia o dia inteiro
        LocalTime horaInicio,
        LocalTime horaFim,
        @Size(max = 160) String motivo,
        // vazio = vale para a barbearia toda; preenchido = folga só desse profissional
        UUID profissionalId) {
}
