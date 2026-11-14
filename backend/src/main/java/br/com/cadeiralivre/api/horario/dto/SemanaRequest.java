package br.com.cadeiralivre.api.horario.dto;

import java.time.LocalTime;
import java.util.Map;

import jakarta.validation.constraints.NotNull;

/**
 * Horário da semana enviado pela tela "Calendário e horários", no formato do front:
 * { horarios: { "0": { ativo, inicio, fim }, ..., "6": {...} } }, onde "0" é domingo e "6" é sábado.
 * Vale para o funcionamento do salão e para o horário de trabalho de cada profissional.
 */
public record SemanaRequest(@NotNull(message = "envie os horários da semana") Map<String, Dia> horarios) {

    // num dia de folga as horas podem vir vazias; nesse caso fica 09:00–18:00 guardado para quando reabrir
    public record Dia(Boolean ativo, LocalTime inicio, LocalTime fim) {
    }
}
