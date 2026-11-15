package br.com.cadeiralivre.api.agenda;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.agenda.dto.AgendaResponse;
import lombok.RequiredArgsConstructor;

/** Tela "Minha agenda" do funcionário (o caminho que o front usa). */
@RestController
@RequestMapping("/agenda")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;

    @GetMapping("/resumo")
    public AgendaResponse.Resumo resumo() {
        return agendaService.resumo();
    }

    @GetMapping("/agendamentos")
    public List<AgendaResponse.Item> agendamentos() {
        return agendaService.agendamentos();
    }
}
