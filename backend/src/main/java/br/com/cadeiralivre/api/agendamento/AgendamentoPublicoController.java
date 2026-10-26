package br.com.cadeiralivre.api.agendamento;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.agendamento.dto.AgendamentoRequest;
import br.com.cadeiralivre.api.agendamento.dto.AgendamentoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Rota pública (sem JWT): o cliente solicita um horário no portal da barbearia. */
@RestController
@RequestMapping("/saloes/{slug}/agendamentos")
@RequiredArgsConstructor
public class AgendamentoPublicoController {

    private final AgendamentoService agendamentoService;

    @PostMapping
    public AgendamentoResponse solicitar(@PathVariable String slug, @Valid @RequestBody AgendamentoRequest req) {
        return agendamentoService.solicitar(slug, req);
    }
}
