package br.com.cadeiralivre.api.agendamento;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.agendamento.dto.MeuAgendamentoResponse;
import lombok.RequiredArgsConstructor;

/** Área do cliente (precisa estar logado como CLIENTE): "meus agendamentos" e cancelamento. */
@RestController
@RequestMapping("/cliente/agendamentos")
@RequiredArgsConstructor
public class ClienteAgendamentoController {

    private final ClienteAgendamentoService clienteAgendamentoService;

    @GetMapping
    public List<MeuAgendamentoResponse> listar() {
        return clienteAgendamentoService.listarMeus();
    }

    @PostMapping("/{id}/cancelar")
    public MeuAgendamentoResponse cancelar(@PathVariable UUID id) {
        return clienteAgendamentoService.cancelar(id);
    }
}
