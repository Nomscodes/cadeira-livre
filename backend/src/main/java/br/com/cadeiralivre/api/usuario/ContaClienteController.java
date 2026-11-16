package br.com.cadeiralivre.api.usuario;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.usuario.dto.ContaResponse;
import br.com.cadeiralivre.api.usuario.dto.ExclusaoContaRequest;
import br.com.cadeiralivre.api.usuario.dto.TrocaSenhaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Área do cliente (precisa estar logado como CLIENTE): dados da conta, troca de senha e exclusão (LGPD). */
@RestController
@RequestMapping("/cliente/conta")
@RequiredArgsConstructor
public class ContaClienteController {

    private final ContaClienteService contaClienteService;

    @GetMapping
    public ContaResponse meusDados() {
        return contaClienteService.meusDados();
    }

    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@RequestBody @Valid TrocaSenhaRequest req) {
        contaClienteService.trocarSenha(req);
    }

    // POST com a senha no corpo, e não DELETE: a exclusão precisa da confirmação da senha
    @PostMapping("/excluir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirConta(@RequestBody @Valid ExclusaoContaRequest req) {
        contaClienteService.excluirConta(req);
    }
}
