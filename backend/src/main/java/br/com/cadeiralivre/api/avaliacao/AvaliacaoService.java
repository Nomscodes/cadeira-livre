package br.com.cadeiralivre.api.avaliacao;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.avaliacao.dto.AvaliacaoRequest;
import br.com.cadeiralivre.api.avaliacao.dto.AvaliacaoResponse;
import br.com.cadeiralivre.api.avaliacao.dto.AvaliacoesDoPortalResponse;
import br.com.cadeiralivre.api.avaliacao.dto.AvaliacoesDoPortalResponse.AvaliacaoDoPortal;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;

/**
 * Sistema de avaliação (escopo do doc V7): o cliente avalia um atendimento dele que o dono marcou como concluído,
 * e o portal público mostra a média e as avaliações mais recentes.
 */
@Service
@RequiredArgsConstructor
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final BarbeariaRepository barbeariaRepository;

    @Transactional
    public AvaliacaoResponse avaliar(UUID agendamentoId, AvaliacaoRequest req) {
        UUID clienteId = UsuarioLogado.clienteId();
        if (clienteId == null) throw ApiException.naoAutorizado("Faça login para continuar.");

        // o cliente só avalia atendimento dele
        Agendamento agendamento = agendamentoRepository.findByIdAndClienteId(agendamentoId, clienteId)
                .orElseThrow(() -> ApiException.naoEncontrado("Agendamento não encontrado."));
        if (agendamento.getStatus() != StatusAgendamento.CONCLUIDO) {
            throw ApiException.conflito("Só atendimentos concluídos podem ser avaliados.");
        }
        if (avaliacaoRepository.existsByAgendamentoId(agendamentoId)) {
            throw ApiException.conflito("Este atendimento já foi avaliado.");
        }

        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setAgendamento(agendamento);
        avaliacao.setNota(req.nota().shortValue());
        avaliacao.setComentario(textoOuNulo(req.comentario()));
        return AvaliacaoResponse.de(avaliacaoRepository.save(avaliacao));
    }

    /** Rota pública: só para portal publicado. */
    @Transactional(readOnly = true)
    public AvaliacoesDoPortalResponse listarDoPortal(String slug) {
        Barbearia barbearia = barbeariaRepository.findBySlug(slug)
                .filter(Barbearia::isPublico)
                .orElseThrow(() -> ApiException.naoEncontrado("Salão não encontrado."));

        Double media = avaliacaoRepository.mediaDaBarbearia(barbearia.getId());
        long total = avaliacaoRepository.countByAgendamentoBarbeariaId(barbearia.getId());
        List<AvaliacaoDoPortal> recentes = avaliacaoRepository
                .findTop20ByAgendamentoBarbeariaIdOrderByCriadoEmDesc(barbearia.getId())
                .stream().map(AvaliacaoDoPortal::de).toList();

        return new AvaliacoesDoPortalResponse(umaCasaDecimal(media), total, recentes);
    }

    // comentário em branco é guardado como "sem comentário"
    private String textoOuNulo(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }

    // ex.: 4.666... vira 4.7; sem avaliações continua null
    private Double umaCasaDecimal(Double media) {
        return media == null ? null : Math.round(media * 10) / 10.0;
    }
}
