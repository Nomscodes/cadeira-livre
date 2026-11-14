package br.com.cadeiralivre.api.agendamento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.profissional.Profissional;

/**
 * Agendamento na tela "Agendamentos" do painel, no formato do front: cliente, serviços e profissionais em texto,
 * os ids dos profissionais (o calendário conta por profissional) e quem quer receber o aviso pelo WhatsApp.
 */
public record AgendamentoResponse(UUID id, String cliente, String whatsapp, String servicos, String profissionais,
                                  List<UUID> profissionalIds, LocalDate data, String hora, BigDecimal valor,
                                  StatusAgendamento status, List<Encaminhar> encaminhar) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /** Profissional do atendimento com o encaminhamento por WhatsApp ligado. */
    public record Encaminhar(String nome, String whatsapp) {
    }

    /** Deve ser chamado dentro de transação (os itens e os profissionais são LAZY). */
    public static AgendamentoResponse de(Agendamento a) {
        List<Profissional> profissionais = a.getItens().stream().map(AgendamentoItem::getProfissional).distinct().toList();
        return new AgendamentoResponse(
                a.getId(),
                a.getClienteNome(),
                a.getClienteTelefone(),
                a.getItens().stream().map(AgendamentoItem::getNome).collect(Collectors.joining(" + ")),
                profissionais.stream().map(AgendamentoResponse::nome).collect(Collectors.joining(", ")),
                profissionais.stream().map(Profissional::getId).toList(),
                a.getData(),
                a.getHoraInicio().format(HORA),
                a.getValor(),
                a.getStatus(),
                profissionais.stream()
                        .filter(p -> p.isEncaminhar() && p.getTelefone() != null)
                        .map(p -> new Encaminhar(nome(p), p.getTelefone()))
                        .toList());
    }

    private static String nome(Profissional p) {
        return p.getNomeSocial() != null ? p.getNomeSocial() : p.getNome();
    }
}
