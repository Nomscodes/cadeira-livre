package br.com.cadeiralivre.api.agenda;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.agenda.dto.AgendaResponse;
import br.com.cadeiralivre.api.common.exception.ApiException;
import lombok.RequiredArgsConstructor;

/** Tela "Minha agenda" do funcionário (o caminho que o front usa). */
@RestController
@RequestMapping("/agenda")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;
    private final CalendarioDaAgendaService calendarioService;
    private final AlertaDoFuncionarioService alertaService;

    /** O que vem ao clicar numa hora do calendário: marcar ou desmarcar o descanso. */
    public record DescansoRequest(LocalDate data, String hora, boolean descanso) {
    }

    /** Alerta sobre o cliente de um atendimento (motivo escolhido na lista e descrição opcional). */
    public record AlertaRequest(UUID agendamentoId, String motivo, String descricao) {
    }

    @PostMapping("/alertas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alertar(@RequestBody AlertaRequest req) {
        alertaService.alertar(req.agendamentoId(), req.motivo(), req.descricao());
    }

    /** Calendário do mês (?mes=2026-11); sem o parâmetro, o mês atual. */
    @GetMapping
    public AgendaResponse.Mes mes(@RequestParam(required = false) String mes) {
        return calendarioService.mes(mes == null || mes.isBlank() ? YearMonth.now() : mesValido(mes));
    }

    @PutMapping("/descansos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void marcarDescanso(@RequestBody DescansoRequest req) {
        calendarioService.marcarDescanso(req.data(), req.hora(), req.descanso());
    }

    @GetMapping("/resumo")
    public AgendaResponse.Resumo resumo() {
        return agendaService.resumo();
    }

    @GetMapping("/agendamentos")
    public List<AgendaResponse.Item> agendamentos() {
        return agendaService.agendamentos();
    }

    private YearMonth mesValido(String mes) {
        try {
            return YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw ApiException.invalido("Informe o mês no formato AAAA-MM.");
        }
    }
}
