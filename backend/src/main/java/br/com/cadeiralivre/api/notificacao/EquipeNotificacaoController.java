package br.com.cadeiralivre.api.notificacao;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.notificacao.dto.NotificacaoDaEquipeResponse;
import lombok.RequiredArgsConstructor;

/** Sino do painel (o caminho que o front usa): lista, marcar como lida e ações do responsável. */
@RestController
@RequestMapping("/notificacoes")
@RequiredArgsConstructor
public class EquipeNotificacaoController {

    private final NotificacaoDaEquipeService service;

    /** O que vem do modal: a ação e, no aviso, a mensagem e para quem vai. */
    public record AcaoRequest(String acao, String mensagem, List<UUID> profissionalIds) {
    }

    public record AcaoResponse(int enviados) {
    }

    @GetMapping
    public List<NotificacaoDaEquipeResponse> listar() {
        return service.listar();
    }

    @PostMapping("/{id}/lida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarComoLida(@PathVariable UUID id) {
        service.marcarComoLida(id);
    }

    @PostMapping("/{id}/acao")
    public AcaoResponse executarAcao(@PathVariable UUID id, @RequestBody AcaoRequest req) {
        return new AcaoResponse(service.executarAcao(id, req.acao(), req.mensagem(), req.profissionalIds()));
    }
}
