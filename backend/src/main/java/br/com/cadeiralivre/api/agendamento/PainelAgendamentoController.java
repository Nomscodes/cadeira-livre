package br.com.cadeiralivre.api.agendamento;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.agendamento.dto.AgendamentoResponse;
import lombok.RequiredArgsConstructor;

/** Painel do dono: lista as solicitações e aprova ou recusa cada uma. */
@RestController
@RequestMapping("/painel/agendamentos")
@RequiredArgsConstructor
public class PainelAgendamentoController {

    private final AgendamentoService agendamentoService;

    @GetMapping
    public List<AgendamentoResponse> listar() {
        return agendamentoService.listarDoPainel();
    }

    @PostMapping("/{id}/aprovar")
    public AgendamentoResponse aprovar(@PathVariable UUID id) {
        return agendamentoService.aprovar(id);
    }

    @PostMapping("/{id}/recusar")
    public AgendamentoResponse recusar(@PathVariable UUID id) {
        return agendamentoService.recusar(id);
    }
}
