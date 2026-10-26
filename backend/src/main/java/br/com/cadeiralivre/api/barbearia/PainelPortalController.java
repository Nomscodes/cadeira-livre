package br.com.cadeiralivre.api.barbearia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.barbearia.dto.PortalRequest;
import br.com.cadeiralivre.api.barbearia.dto.PortalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Rota do painel (exige JWT com papel DONO). A barbearia vem do token, nunca do corpo da requisição. */
@RestController
@RequestMapping("/painel/portal")
@RequiredArgsConstructor
public class PainelPortalController {

    private final BarbeariaService barbeariaService;

    @GetMapping
    public PortalResponse obter() {
        return barbeariaService.obterDoPainel();
    }

    @PutMapping
    public PortalResponse atualizar(@Valid @RequestBody PortalRequest req) {
        return barbeariaService.atualizar(req);
    }
}
