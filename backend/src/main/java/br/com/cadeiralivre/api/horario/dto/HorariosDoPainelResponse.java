package br.com.cadeiralivre.api.horario.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.cadeiralivre.api.horario.HorarioFuncionamento;
import br.com.cadeiralivre.api.profissional.ProfissionalHorario;

/**
 * O que a tela "Calendário e horários" do painel mostra, no formato do front: o funcionamento do salão, o horário
 * de cada profissional que atende e os avisos de conflito entre os dois.
 * As semanas usam as chaves "0" (domingo) a "6" (sábado).
 */
public record HorariosDoPainelResponse(Salao salao, List<ProfissionalComHorario> profissionais, List<String> avisos) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public record Dia(boolean ativo, String inicio, String fim) {
    }

    public record Salao(Map<String, Dia> horarios) {
    }

    public record ProfissionalComHorario(UUID id, String nome, boolean ehAdmin, Map<String, Dia> horarios) {
    }

    /** Resposta do PUT: só os avisos de conflito depois de salvar. */
    public record Avisos(List<String> avisos) {
    }

    public static Map<String, Dia> semanaDoSalao(List<HorarioFuncionamento> horarios) {
        Map<String, Dia> semana = semanaVazia();
        horarios.forEach(h -> semana.put(chave(h.getDiaSemana()),
                new Dia(h.isAtivo(), hora(h.getHoraAbertura()), hora(h.getHoraFechamento()))));
        return semana;
    }

    public static Map<String, Dia> semanaDoProfissional(List<ProfissionalHorario> horarios) {
        Map<String, Dia> semana = semanaVazia();
        horarios.forEach(h -> semana.put(chave(h.getDiaSemana()),
                new Dia(h.isAtivo(), hora(h.getHoraInicio()), hora(h.getHoraFim()))));
        return semana;
    }

    /** DayOfWeek vai de 1 (segunda) a 7 (domingo); no front o domingo é "0". */
    public static String chave(DayOfWeek dia) {
        return String.valueOf(dia.getValue() % 7);
    }

    // os 7 dias sempre aparecem; dia sem horário vem fechado, com 09:00–18:00 sugerido (igual ao front)
    private static Map<String, Dia> semanaVazia() {
        Map<String, Dia> semana = new LinkedHashMap<>();
        for (int dia = 0; dia < 7; dia++) {
            semana.put(String.valueOf(dia), new Dia(false, "09:00", "18:00"));
        }
        return semana;
    }

    private static String hora(LocalTime hora) {
        return hora.format(HORA);
    }
}
