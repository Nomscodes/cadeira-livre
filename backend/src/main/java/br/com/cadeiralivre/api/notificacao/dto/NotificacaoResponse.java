package br.com.cadeiralivre.api.notificacao.dto;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.notificacao.Notificacao;
import br.com.cadeiralivre.api.notificacao.TipoNotificacao;
import br.com.cadeiralivre.api.profissional.Profissional;

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
        // aviso do salão de um horário que já foi apagado (V21): só existe o texto guardado
        if (a == null) {
            return new NotificacaoResponse(n.getId(), n.getTipo(), n.getTipo().getTitulo(), n.getTexto(),
                    null, n.getCriadaEm(), n.getLidaEm() != null);
        }
        String cliente = a.getClienteNome();
        // todos os itens do atendimento (combo + serviço avulso, um ou mais profissionais), como nos detalhes do modal;
        // agendamento antigo sem itens usa o serviço e o profissional principais
        String servico = a.getItens().isEmpty() ? a.getServico().getNome()
                : a.getItens().stream().map(AgendamentoItem::getNome).collect(Collectors.joining(" + "));
        String profissional = a.getItens().isEmpty() ? nome(a.getProfissional())
                : a.getItens().stream().map(i -> nome(i.getProfissional())).distinct().collect(Collectors.joining(", "));
        String dia = a.getData().format(DIA);
        String hora = a.getHoraInicio().format(HORA);

        String texto = switch (n.getTipo()) {
            case NOVO_AGENDAMENTO -> cliente + " pediu " + servico + " com " + profissional
                    + " em " + dia + " às " + hora + ". Aprove ou recuse no painel.";
            case CANCELAMENTO -> cliente + " cancelou " + servico + " com " + profissional
                    + " de " + dia + " às " + hora + ".";
            case LEMBRETE_DONO -> cliente + " tem horário às " + hora + " com " + profissional + " (" + servico + ").";
            case CONCLUIDO_PARA_DONO -> "O atendimento de " + cliente + " (" + servico + ") foi concluído."
                    + " O cliente já pode avaliar o serviço.";
            case HORARIO_APROVADO -> "A barbearia confirmou seu " + servico + " com " + profissional
                    + " em " + dia + " às " + hora + ".";
            case HORARIO_RECUSADO -> "A barbearia não pôde aceitar seu " + servico
                    + " em " + dia + " às " + hora + ". Escolha outro horário.";
            case ATENDIMENTO_CONCLUIDO -> "Seu " + servico + " com " + profissional + " foi concluído. Toque para avaliar.";
            case PEDIDO_AVALIACAO -> "A " + a.getBarbearia().getNomeFantasia() + " quer saber como foi seu " + servico
                    + " com " + profissional + ". Toque para avaliar.";
            // aviso do dono, alerta do funcionário e avisos do salão ao cliente: o texto ficou guardado
            case AVISO_EQUIPE, ALERTA_FUNCIONARIO, HORARIO_CANCELADO_PELO_SALAO, AVISO_DO_SALAO -> n.getTexto();
            case ATENDIMENTO_NA_AGENDA -> cliente + " marcou " + servico + " com você em " + dia + " às " + hora + ".";
            case LEMBRETE_CLIENTE -> servico + " com " + profissional + " em " + dia + " às " + hora
                    + ", na " + a.getBarbearia().getNomeFantasia() + ".";
        };

        return new NotificacaoResponse(n.getId(), n.getTipo(), n.getTipo().getTitulo(), texto,
                a.getId(), n.getCriadaEm(), n.getLidaEm() != null);
    }

    // nome social quando tiver: é o nome que o cliente vê no portal e o mesmo dos detalhes do modal
    private static String nome(Profissional p) {
        return p.getNomeSocial() != null ? p.getNomeSocial() : p.getNome();
    }
}
