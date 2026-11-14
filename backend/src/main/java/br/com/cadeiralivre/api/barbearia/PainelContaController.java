package br.com.cadeiralivre.api.barbearia;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** Senha e dados da conta do dono na tela "Configurações" (exige JWT com papel DONO). */
@RestController
@RequestMapping("/painel")
@RequiredArgsConstructor
public class PainelContaController {

    private final ContaDoPainelService contaService;

    public record SenhaRequest(
            @NotBlank(message = "informe a senha atual") String atual,
            @NotBlank(message = "informe a nova senha")
            @Size(min = 8, max = 72, message = "A nova senha precisa ter ao menos 8 caracteres.") String nova) {
    }

    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@Valid @RequestBody SenhaRequest req) {
        contaService.trocarSenha(req.atual(), req.nova());
    }

    // apaga agendamentos, serviços, combos e bloqueios e oculta o portal
    @DeleteMapping("/dados")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apagarDados() {
        contaService.apagarDados();
    }

    // apaga tudo do salão e a conta do dono
    @DeleteMapping("/conta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void encerrarConta() {
        contaService.encerrarConta();
    }
}
