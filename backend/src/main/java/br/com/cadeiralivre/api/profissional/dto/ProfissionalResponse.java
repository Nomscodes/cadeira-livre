package br.com.cadeiralivre.api.profissional.dto;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalHorario;
import br.com.cadeiralivre.api.servico.Servico;

/**
 * Profissional no formato da tela de profissionais do front: nome social, WhatsApp, serviços, se atende,
 * se é administrador (ehAdmin) e o horário de trabalho da semana.
 */
public record ProfissionalResponse(UUID id, String nome, String nomeSocial, String whatsapp, boolean encaminhar,
                                   List<UUID> servicoIds, boolean atende, boolean ehAdmin, Map<String, Dia> horarios) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Horário de um dia. As chaves do mapa vão de "0" (domingo) a "6" (sábado), como no front. */
    public record Dia(boolean ativo, String inicio, String fim) {
    }

    /** Resposta do cadastro e da edição: o profissional e o código de convite (só no cadastro). */
    public record ComConvite(ProfissionalResponse profissional, String convite) {
    }

    public static ProfissionalResponse de(Profissional p, List<ProfissionalHorario> horarios) {
        List<UUID> servicoIds = p.getServicos().stream()
                .sorted(Comparator.comparing(Servico::getNome)).map(Servico::getId).toList();
        return new ProfissionalResponse(p.getId(), p.getNome(), p.getNomeSocial(), p.getTelefone(), p.isEncaminhar(),
                servicoIds, p.isAtende(), p.isAdministrador(), semana(horarios));
    }

    // os 7 dias sempre aparecem; dia sem horário vem como folga, com 09:00–18:00 sugerido (igual ao front)
    private static Map<String, Dia> semana(List<ProfissionalHorario> horarios) {
        Map<String, Dia> semana = new LinkedHashMap<>();
        for (int dia = 0; dia < 7; dia++) {
            semana.put(String.valueOf(dia), new Dia(false, "09:00", "18:00"));
        }
        for (ProfissionalHorario h : horarios) {
            // DayOfWeek vai de 1 (segunda) a 7 (domingo); no front o domingo é 0
            semana.put(String.valueOf(h.getDiaSemana().getValue() % 7),
                    new Dia(h.isAtivo(), hora(h.getHoraInicio()), hora(h.getHoraFim())));
        }
        return semana;
    }

    private static String hora(LocalTime hora) {
        return hora.format(HORA);
    }
}
