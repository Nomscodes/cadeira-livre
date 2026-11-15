package br.com.cadeiralivre.api.notificacao.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.cadeiralivre.api.agendamento.dto.AgendamentoResponse;
import br.com.cadeiralivre.api.notificacao.Notificacao;
import br.com.cadeiralivre.api.notificacao.TipoNotificacao;

/**
 * Notificação no sino do painel, no formato do front: tipo curto ("agendamento", "aviso"...), quem mandou,
 * os dados do atendimento e as ações que o responsável pode fazer.
 */
public record NotificacaoDaEquipeResponse(UUID id, String tipo, String titulo, String texto, Instant criadaEm,
                                          boolean lida, String remetente, Detalhes detalhes, List<Acao> acoes) {

    /** Dados do atendimento mostrados no modal da notificação. */
    public record Detalhes(String cliente, String servicos, String profissionais, LocalDate data, String hora) {
    }

    /** Botão do modal (ex.: "Enviar aviso à equipe"). usada = já foi feito (o botão aparece desligado). */
    public record Acao(String id, String rotulo, boolean usada) {
    }

    /**
     * doResponsavel = quem está vendo é o dono (só ele tem ações).
     * avaliacaoPedida = o dono já pediu a avaliação desse atendimento (ou o cliente já avaliou).
     * Deve ser chamado dentro de transação (o agendamento e os itens são LAZY).
     */
    public static NotificacaoDaEquipeResponse de(Notificacao n, boolean doResponsavel, boolean avaliacaoPedida) {
        // título e texto são os mesmos do sino antigo; os detalhes vêm no formato da tela de agendamentos
        NotificacaoResponse base = NotificacaoResponse.de(n);
        AgendamentoResponse a = AgendamentoResponse.de(n.getAgendamento());
        Detalhes detalhes = new Detalhes(a.cliente(), a.servicos(), a.profissionais(), a.data(), a.hora());

        List<Acao> acoes = new ArrayList<>();
        if (doResponsavel && n.getTipo() == TipoNotificacao.CONCLUIDO_PARA_DONO) {
            // atendimento concluído: o dono pode pedir a avaliação ao cliente (uma vez só)
            acoes.add(new Acao("avaliacao", "Pedir avaliação ao cliente", avaliacaoPedida));
        } else if (doResponsavel && n.getTipo() != TipoNotificacao.AVISO_EQUIPE) {
            // as outras ele pode repassar para a equipe, menos um aviso (que ele mesmo mandou)
            acoes.add(new Acao("aviso", "Enviar aviso à equipe", false));
        }

        return new NotificacaoDaEquipeResponse(n.getId(), tipoDoFront(n.getTipo()), base.titulo(), base.texto(),
                n.getCriadaEm(), n.getLidaEm() != null, n.getRemetente(), detalhes, acoes);
    }

    // nomes curtos que o front usa para a etiqueta de cada notificação
    private static String tipoDoFront(TipoNotificacao tipo) {
        return switch (tipo) {
            case NOVO_AGENDAMENTO -> "agendamento";
            case CANCELAMENTO -> "cancelamento";
            case LEMBRETE_DONO -> "lembrete";
            case CONCLUIDO_PARA_DONO -> "avaliacao";
            case ALERTA_FUNCIONARIO -> "alerta";
            case AVISO_EQUIPE -> "aviso";
            default -> tipo.name().toLowerCase();
        };
    }
}
