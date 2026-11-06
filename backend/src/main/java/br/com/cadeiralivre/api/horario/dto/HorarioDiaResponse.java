package br.com.cadeiralivre.api.horario.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import br.com.cadeiralivre.api.horario.HorarioFuncionamento;

public record HorarioDiaResponse(DayOfWeek diaSemana, boolean aberto, LocalTime abertura, LocalTime fechamento) {

    public static HorarioDiaResponse de(HorarioFuncionamento h) {
        return new HorarioDiaResponse(h.getDiaSemana(), h.isAtivo(), h.getHoraAbertura(), h.getHoraFechamento());
    }

    // dia que o dono ainda não configurou aparece como fechado
    public static HorarioDiaResponse fechado(DayOfWeek dia) {
        return new HorarioDiaResponse(dia, false, null, null);
    }
}
