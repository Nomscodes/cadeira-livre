package br.com.cadeiralivre.api.agendamento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.agendamento.dto.AgendamentoResponse;
import lombok.RequiredArgsConstructor;

/** Painel do dono: lista as solicitações, aprova ou recusa cada uma e marca o atendimento como concluído. */
@RestController
@RequestMapping("/painel/agendamentos")
@RequiredArgsConstructor
public class PainelAgendamentoController {

    private final AgendamentoService agendamentoService;

    // sem data: todos os agendamentos; com data (ex.: ?data=2026-11-10): só a agenda daquele dia
    @GetMapping
    public List<AgendamentoResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return agendamentoService.listarDoPainel(data);
    }

    @PostMapping("/{id}/aprovar")
    public AgendamentoResponse aprovar(@PathVariable UUID id) {
        return agendamentoService.aprovar(id);
    }

    @PostMapping("/{id}/recusar")
    public AgendamentoResponse recusar(@PathVariable UUID id) {
        return agendamentoService.recusar(id);
    }

    // depois do atendimento: só agendamento aprovado e depois que o horário começou
    @PostMapping("/{id}/concluir")
    public AgendamentoResponse concluir(@PathVariable UUID id) {
        return agendamentoService.concluir(id);
    }
}
