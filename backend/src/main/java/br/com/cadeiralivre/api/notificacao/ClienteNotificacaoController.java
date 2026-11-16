package br.com.cadeiralivre.api.notificacao;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.notificacao.dto.NotificacaoResponse;
import lombok.RequiredArgsConstructor;

/** Sino da área do cliente: as notificações do cliente logado. */
@RestController
@RequestMapping("/cliente/notificacoes")
@RequiredArgsConstructor
public class ClienteNotificacaoController {

    private final NotificacaoService notificacaoService;

    @GetMapping
    public List<NotificacaoResponse> listar() {
        return notificacaoService.listarDoCliente();
    }

    @PostMapping("/{id}/lida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarComoLida(@PathVariable UUID id) {
        notificacaoService.marcarComoLidaDoCliente(id);
    }
}
