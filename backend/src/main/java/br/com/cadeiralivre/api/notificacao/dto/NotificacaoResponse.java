package br.com.cadeiralivre.api.notificacao.dto;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.notificacao.Notificacao;
import br.com.cadeiralivre.api.notificacao.TipoNotificacao;

/** Notificação do jeito que aparece no sino: título, texto e se já foi lida. */
public record NotificacaoResponse(UUID id, TipoNotificacao tipo, String titulo, String texto,
                                  UUID agendamentoId, Instant criadaEm, boolean lida) {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * O texto é montado na hora, com os dados atuais do agendamento. Se o cliente excluiu a conta,
     * aparece "Cliente removido" (LGPD). Deve ser chamado dentro de transação (as relações são LAZY).
     */
    public static NotificacaoResponse de(Notificacao n) {
        Agendamento a = n.getAgendamento();
        String cliente = a.getClienteNome();
        String servico = a.getServico().getNome();
        String profissional = a.getProfissional().getNome();
        String dia = a.getData().format(DIA);
        String hora = a.getHoraInicio().format(HORA);

        String texto = switch (n.getTipo()) {
            case NOVO_AGENDAMENTO -> cliente + " pediu " + servico + " com " + profissional
                    + " em " + dia + " às " + hora + ". Aprove ou recuse no painel.";
            case CANCELAMENTO -> cliente + " cancelou " + servico + " com " + profissional
                    + " de " + dia + " às " + hora + ".";
            case LEMBRETE_DONO -> cliente + " tem horário às " + hora + " com " + profissional + " (" + servico + ").";
            case HORARIO_APROVADO -> "A barbearia confirmou seu " + servico + " com " + profissional
                    + " em " + dia + " às " + hora + ".";
            case HORARIO_RECUSADO -> "A barbearia não pôde aceitar seu " + servico
                    + " em " + dia + " às " + hora + ". Escolha outro horário.";
            case ATENDIMENTO_CONCLUIDO -> "Seu " + servico + " com " + profissional + " foi concluído. Toque para avaliar.";
            case LEMBRETE_CLIENTE -> servico + " com " + profissional + " em " + dia + " às " + hora
                    + ", na " + a.getBarbearia().getNomeFantasia() + ".";
        };

        return new NotificacaoResponse(n.getId(), n.getTipo(), n.getTipo().getTitulo(), texto,
                a.getId(), n.getCriadaEm(), n.getLidaEm() != null);
    }
}
