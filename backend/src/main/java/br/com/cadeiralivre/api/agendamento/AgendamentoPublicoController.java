package br.com.cadeiralivre.api.agendamento;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.agendamento.dto.AgendamentoCriadoResponse;
import br.com.cadeiralivre.api.agendamento.dto.AgendamentoRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Rota pública (sem JWT): o cliente solicita um horário no portal da barbearia. */
@RestController
@RequestMapping("/saloes/{slug}/agendamentos")
@RequiredArgsConstructor
public class AgendamentoPublicoController {

    private final AgendamentoService agendamentoService;

    @PostMapping
    public AgendamentoCriadoResponse solicitar(@PathVariable String slug, @Valid @RequestBody AgendamentoRequest req) {
        return agendamentoService.solicitar(slug, req);
    }
}
