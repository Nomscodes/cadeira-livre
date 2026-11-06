package br.com.cadeiralivre.api.horario.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

// mesmo no dia fechado o horário é guardado, para quando o dono reabrir
public record HorarioDiaRequest(
        @NotNull DayOfWeek diaSemana,
        @NotNull Boolean aberto,
        @NotNull LocalTime abertura,
        @NotNull LocalTime fechamento) {
}
