package br.com.cadeiralivre.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Tela "Recuperar senha" do front (rotas públicas, como o login). */
@RestController
@RequestMapping("/auth/recuperar")
@RequiredArgsConstructor
public class RecuperacaoSenhaController {

    private final RecuperacaoSenhaService recuperacaoService;

    public record EmailRequest(@NotBlank(message = "Informe o e-mail cadastrado.") String email) {
    }

    public record CodigoRequest(@NotBlank String email, @NotBlank(message = "Informe o código recebido.") String codigo) {
    }

    public record RedefinirRequest(
            @NotBlank String email,
            @NotBlank(message = "Informe o código recebido.") String codigo,
            @NotBlank @Size(min = 8, max = 72, message = "A senha precisa ter ao menos 8 caracteres.") String novaSenha) {
    }

    /** Passo 1: gera o código. Responde igual com ou sem conta, para não revelar quem tem cadastro. */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void solicitar(@Valid @RequestBody EmailRequest req) {
        recuperacaoService.solicitar(req.email());
    }

    /** Passo 2: confere o código. */
    @PostMapping("/codigo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void conferir(@Valid @RequestBody CodigoRequest req) {
        recuperacaoService.conferir(req.email(), req.codigo());
    }

    /** Passo 3: define a nova senha. */
    @PostMapping("/redefinir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinir(@Valid @RequestBody RedefinirRequest req) {
        recuperacaoService.redefinir(req.email(), req.codigo(), req.novaSenha());
    }
}
