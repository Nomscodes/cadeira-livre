package br.com.cadeiralivre.api.avaliacao.dto;

import java.time.LocalDate;
import java.util.List;

import br.com.cadeiralivre.api.avaliacao.Avaliacao;

/**
 * Avaliações mostradas no portal público: média, total e as mais recentes.
 * Não tem nome nem telefone do cliente (LGPD).
 */
public record AvaliacoesDoPortalResponse(Double media, long total, List<AvaliacaoDoPortal> avaliacoes) {

    public record AvaliacaoDoPortal(int nota, String comentario, String servico, String profissional, LocalDate data) {

        /** Deve ser chamado dentro de transação (agendamento, servico e profissional são LAZY). */
        public static AvaliacaoDoPortal de(Avaliacao a) {
            return new AvaliacaoDoPortal(a.getNota(), a.getComentario(), a.getAgendamento().getServico().getNome(),
                    a.getAgendamento().getProfissional().getNome(), a.getAgendamento().getData());
        }
    }
}
