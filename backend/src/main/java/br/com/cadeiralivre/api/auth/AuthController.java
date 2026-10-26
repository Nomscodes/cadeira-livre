package br.com.cadeiralivre.api.auth;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.auth.dto.CadastroClienteRequest;
import br.com.cadeiralivre.api.auth.dto.CadastroDonoRequest;
import br.com.cadeiralivre.api.auth.dto.LoginRequest;
import br.com.cadeiralivre.api.auth.dto.SessaoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Camada HTTP: recebe o DTO, valida (@Valid) e delega ao service. Não tem regra de negócio. */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public SessaoResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /** Cadastro do dono + criação da barbearia (RF02). */
    @PostMapping("/cadastro")
    public SessaoResponse cadastrarDono(@Valid @RequestBody CadastroDonoRequest req) {
        return authService.cadastrarDono(req);
    }

    /** Cadastro de cliente (RF01). */
    @PostMapping("/cadastro/cliente")
    public SessaoResponse cadastrarCliente(@Valid @RequestBody CadastroClienteRequest req) {
        return authService.cadastrarCliente(req);
    }
}
