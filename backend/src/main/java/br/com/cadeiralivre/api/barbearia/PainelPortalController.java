package br.com.cadeiralivre.api.barbearia;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.barbearia.dto.PortalRequest;
import br.com.cadeiralivre.api.barbearia.dto.PortalResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** Rota do painel (exige JWT com papel DONO). A barbearia vem do token, nunca do corpo da requisição. */
@RestController
@RequestMapping("/painel/portal")
@RequiredArgsConstructor
public class PainelPortalController {

    private final BarbeariaService barbeariaService;

    /** Resultado do "Testar publicação": ok = pode publicar; senão, a lista do que falta. */
    public record ValidacaoResponse(boolean ok, List<String> problemas) {
    }

    public record PublicacaoRequest(@NotNull(message = "informe se o portal fica público") Boolean publico) {
    }

    public record PublicacaoResponse(boolean publico) {
    }

    @GetMapping
    public PortalResponse obter() {
        return barbeariaService.obterDoPainel();
    }

    @PutMapping
    public PortalResponse atualizar(@Valid @RequestBody PortalRequest req) {
        return barbeariaService.atualizar(req);
    }

    @PostMapping("/validar-publicacao")
    public ValidacaoResponse validarPublicacao() {
        List<String> problemas = barbeariaService.problemasParaPublicar();
        return new ValidacaoResponse(problemas.isEmpty(), problemas);
    }

    // 422 com a lista de problemas quando o portal ainda não pode ficar público
    @PutMapping("/publicacao")
    public PublicacaoResponse alterarPublicacao(@Valid @RequestBody PublicacaoRequest req) {
        return new PublicacaoResponse(barbeariaService.alterarPublicacao(req.publico()));
    }
}
