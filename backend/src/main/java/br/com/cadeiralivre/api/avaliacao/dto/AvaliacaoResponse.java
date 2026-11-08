package br.com.cadeiralivre.api.avaliacao.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.cadeiralivre.api.avaliacao.Avaliacao;

/** Resposta para o cliente que acabou de avaliar. */
public record AvaliacaoResponse(UUID id, UUID agendamentoId, int nota, String comentario, Instant criadoEm) {

    public static AvaliacaoResponse de(Avaliacao a) {
        return new AvaliacaoResponse(a.getId(), a.getAgendamento().getId(), a.getNota(),
                a.getComentario(), a.getCriadoEm());
    }
}
