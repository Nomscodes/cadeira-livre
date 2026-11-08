package br.com.cadeiralivre.api.avaliacao;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.avaliacao.dto.AvaliacoesDoPortalResponse;
import lombok.RequiredArgsConstructor;

/** Rota pública (sem login): avaliações que aparecem no portal da barbearia. */
@RestController
@RequestMapping("/saloes/{slug}/avaliacoes")
@RequiredArgsConstructor
public class AvaliacaoPublicaController {

    private final AvaliacaoService avaliacaoService;

    @GetMapping
    public AvaliacoesDoPortalResponse listar(@PathVariable String slug) {
        return avaliacaoService.listarDoPortal(slug);
    }
}
