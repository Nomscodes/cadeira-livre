package br.com.cadeiralivre.api.barbearia;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.barbearia.dto.PortalPublicoResponse;
import lombok.RequiredArgsConstructor;

/** Rotas públicas (sem JWT): a busca de salões e o portal de cada barbearia pelo slug. */
@RestController
@RequestMapping("/saloes")
@RequiredArgsConstructor
public class BarbeariaPublicaController {

    private final BarbeariaService barbeariaService;

    /** Busca de salões publicados; todos os filtros são opcionais. */
    @GetMapping
    public List<PortalPublicoResponse.SalaoDaBusca> buscar(@RequestParam(required = false) String cidade,
                                                           @RequestParam(required = false) String categoria,
                                                           @RequestParam(required = false) String servico,
                                                           @RequestParam(required = false) String nome) {
        return barbeariaService.buscar(cidade, categoria, servico, nome);
    }

    @GetMapping("/{slug}")
    public PortalPublicoResponse portal(@PathVariable String slug) {
        return barbeariaService.portalPublico(slug);
    }
}
