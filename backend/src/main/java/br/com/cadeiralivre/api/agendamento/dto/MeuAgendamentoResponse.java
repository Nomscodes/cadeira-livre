package br.com.cadeiralivre.api.agendamento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.profissional.Profissional;

/**
 * Agendamento visto pelo cliente em "meus agendamentos", no formato do front: os serviços e os profissionais em texto,
 * o salão (ele pode agendar em vários), se já avaliou e se ainda pode cancelar no prazo do salão.
 */
public record MeuAgendamentoResponse(UUID id, StatusAgendamento status, String servicos, String profissionais,
                                     LocalDate data, String hora, BigDecimal valor, boolean avaliado, Salao salao,
                                     boolean podeCancelar, int cancelamentoHoras) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public record Salao(String nome, String slug, String temaId, String whatsapp) {
    }

    /** Deve ser chamado dentro de transação (barbearia e itens são LAZY). */
    public static MeuAgendamentoResponse de(Agendamento a, boolean avaliado, LocalDateTime agora) {
        Barbearia b = a.getBarbearia();
        // ex.: "Corte + Barba" e "Gabriel, Naoki" (o cliente vê o nome social)
        String servicos = a.getItens().stream().map(AgendamentoItem::getNome).collect(Collectors.joining(" + "));
        String profissionais = a.getItens().stream().map(AgendamentoItem::getProfissional).distinct()
                .map(MeuAgendamentoResponse::nomeParaOCliente).collect(Collectors.joining(", "));
        return new MeuAgendamentoResponse(a.getId(), a.getStatus(), servicos, profissionais, a.getData(),
                a.getHoraInicio().format(HORA), a.getValor(), avaliado,
                new Salao(b.getNomeFantasia(), b.getSlug(), b.getTemaId(), b.getTelefone()),
                podeCancelar(a, agora), b.getCancelamentoHoras());
    }

    /** Pendente ou aprovado, e ainda dentro do prazo de cancelamento do salão. */
    public static boolean podeCancelar(Agendamento a, LocalDateTime agora) {
        boolean ativo = a.getStatus() == StatusAgendamento.PENDENTE || a.getStatus() == StatusAgendamento.APROVADO;
        LocalDateTime limite = LocalDateTime.of(a.getData(), a.getHoraInicio())
                .minusHours(a.getBarbearia().getCancelamentoHoras());
        return ativo && !agora.isAfter(limite);
    }

    private static String nomeParaOCliente(Profissional p) {
        return p.getNomeSocial() != null ? p.getNomeSocial() : p.getNome();
    }
}
