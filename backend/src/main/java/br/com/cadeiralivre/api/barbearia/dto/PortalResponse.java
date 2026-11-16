package br.com.cadeiralivre.api.barbearia.dto;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaFoto;
import br.com.cadeiralivre.api.horario.HorarioFuncionamento;
import br.com.cadeiralivre.api.profissional.Profissional;

/**
 * Portal completo da barbearia, só para o dono (painel), com os nomes que o front usa:
 * nome, logo, capa, galeria, whatsapp, temaId, horarios, adminIds e a lista de profissionais.
 */
public record PortalResponse(
        UUID id,
        String slug,
        String nome,
        String temaId,
        boolean publico,
        String logo,
        String capa,
        List<String> galeria,
        String sobre,
        String whatsapp,
        String cidade,
        String endereco,
        String categoria,
        String redes,
        Map<String, Dia> horarios,
        String atendimento,
        int cancelamentoHoras,
        boolean funcionariosEditamHorario,
        String atuacao,
        List<UUID> adminIds,
        List<ProfissionalResumo> profissionais) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Horário de um dia. No mapa, as chaves vão de "0" (domingo) a "6" (sábado), como no front. */
    public record Dia(boolean ativo, String inicio, String fim) {
    }

    public record ProfissionalResumo(UUID id, String nome) {
    }

    public static PortalResponse de(Barbearia b, List<BarbeariaFoto> fotos, List<HorarioFuncionamento> horarios,
                                    List<Profissional> profissionais) {
        return new PortalResponse(
                b.getId(),
                b.getSlug(),
                b.getNomeFantasia(),
                b.getTemaId(),
                b.isPublico(),
                b.getLogoUrl(),
                b.getCapaUrl(),
                fotos.stream().map(BarbeariaFoto::getImagem).toList(),
                b.getSobre(),
                b.getTelefone(),
                b.getCidade(),
                b.getEndereco(),
                b.getCategoria(),
                b.getRedes(),
                semana(horarios),
                b.getAtendimento(),
                b.getCancelamentoHoras(),
                b.isFuncionariosEditamHorario(),
                b.getAtuacao(),
                profissionais.stream().filter(Profissional::isAdministrador).map(Profissional::getId).toList(),
                profissionais.stream().map(p -> new ProfissionalResumo(p.getId(), p.getNome())).toList());
    }

    // os 7 dias sempre aparecem; dia sem horário cadastrado vem fechado, com 09:00–18:00 sugerido (igual ao front)
    private static Map<String, Dia> semana(List<HorarioFuncionamento> horarios) {
        Map<String, Dia> semana = new LinkedHashMap<>();
        for (int dia = 0; dia < 7; dia++) {
            semana.put(String.valueOf(dia), new Dia(false, "09:00", "18:00"));
        }
        for (HorarioFuncionamento h : horarios) {
            // DayOfWeek vai de 1 (segunda) a 7 (domingo); no front o domingo é 0
            String chave = String.valueOf(h.getDiaSemana().getValue() % 7);
            semana.put(chave, new Dia(h.isAtivo(), hora(h.getHoraAbertura()), hora(h.getHoraFechamento())));
        }
        return semana;
    }

    private static String hora(LocalTime hora) {
        return hora.format(HORA);
    }
}
