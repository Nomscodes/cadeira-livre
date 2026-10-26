package br.com.cadeiralivre.api.barbearia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.barbearia.dto.PortalPublicoResponse;
import lombok.RequiredArgsConstructor;

/** Rota pública (sem JWT): o cliente abre o portal da barbearia pelo slug. */
@RestController
@RequestMapping("/saloes")
@RequiredArgsConstructor
public class BarbeariaPublicaController {

    private final BarbeariaService barbeariaService;

    @GetMapping("/{slug}")
    public PortalPublicoResponse portal(@PathVariable String slug) {
        return barbeariaService.portalPublico(slug);
    }
}
