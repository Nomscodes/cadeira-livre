package br.com.cadeiralivre.api.avaliacao;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.avaliacao.dto.AvaliacaoRequest;
import br.com.cadeiralivre.api.avaliacao.dto.AvaliacaoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Área do cliente (precisa estar logado como CLIENTE): avaliar um atendimento concluído. */
@RestController
@RequestMapping("/cliente/agendamentos")
@RequiredArgsConstructor
public class ClienteAvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    @PostMapping("/{id}/avaliacao")
    public AvaliacaoResponse avaliar(@PathVariable UUID id, @RequestBody @Valid AvaliacaoRequest req) {
        return avaliacaoService.avaliar(id, req);
    }
}
