package br.com.cadeiralivre.api.agenda.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.horario.dto.HorariosDoPainelResponse.Dia;
import br.com.cadeiralivre.api.profissional.Profissional;

/** Respostas da tela "Minha agenda" do funcionário, no formato do front. */
public final class AgendaResponse {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private AgendaResponse() {
    }

    /** Um agendamento da lista: serviços em texto, duração total e os colegas que atendem junto. */
    public record Item(UUID id, LocalDate data, String hora, int duracao, String cliente, String servicos,
                       StatusAgendamento status, List<String> colegas) {

        /** profissionalId = o profissional de quem está vendo (não aparece em "colegas"). LAZY: chamar em transação. */
        public static Item de(Agendamento a, UUID profissionalId) {
            List<String> colegas = a.getItens().stream()
                    .map(AgendamentoItem::getProfissional)
                    .filter(p -> !p.getId().equals(profissionalId))
                    .map(AgendaResponse::nome)
                    .distinct()
                    .toList();
            return new Item(a.getId(), a.getData(), a.getHoraInicio().format(HORA), a.duracaoMinutos(),
                    a.getClienteNome(),
                    a.getItens().stream().map(AgendamentoItem::getNome).collect(Collectors.joining(" + ")),
                    a.getStatus(), colegas);
        }
    }

    /**
     * Calendário do mês: horário do profissional e do salão (chaves "0".."6"), os agendamentos do mês,
     * os descansos que ele marcou, os dias com o salão fechado e as folgas dele.
     * podeEditar = o responsável liberou o funcionário para marcar descansos.
     */
    public record Mes(ProfissionalDoMes profissional, boolean podeEditar, List<Item> agendamentos,
                      List<Descanso> descansos, List<DiaFechado> bloqueios, List<DiaFechado> folgas,
                      Map<String, Dia> salaoHorarios) {
    }

    public record ProfissionalDoMes(UUID id, String nome, Map<String, Dia> horarios) {
    }

    /** Uma hora de descanso marcada pelo funcionário (ex.: 15:00 até 16:00). */
    public record Descanso(LocalDate data, String hora) {
    }

    public record DiaFechado(LocalDate data, String motivo) {
    }

    /** Cabeçalho da tela: o perfil do profissional, o salão (para o tema) e os indicadores. */
    public record Resumo(Perfil profissional, Salao salao, Indicadores indicadores) {
    }

    public record Perfil(UUID id, String nome, String nomeSocial, String whatsapp, Map<String, Dia> horarios,
                         List<String> servicos) {
    }

    public record Salao(String nome, String temaId) {
    }

    /** hoje, próximos 7 dias, concluídos no mês e o próximo atendimento (ou null). */
    public record Indicadores(long hoje, long proximos7, long concluidosMes, Proximo proximo) {
    }

    public record Proximo(LocalDate data, String hora, String cliente, String servicos) {
    }

    // nome social quando tiver (é como o salão chama o profissional)
    static String nome(Profissional p) {
        return p.getNomeSocial() != null ? p.getNomeSocial() : p.getNome();
    }
}
