package br.com.cadeiralivre.api.agendamento;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.agendamento.dto.CalendarioResponse;
import br.com.cadeiralivre.api.agendamento.dto.DisponibilidadeResponse;
import lombok.RequiredArgsConstructor;

/** Rotas públicas (sem JWT): o cliente consulta os dias e os horários livres no portal da barbearia (RF13). */
@RestController
@RequestMapping("/saloes/{slug}/disponibilidade")
@RequiredArgsConstructor
public class DisponibilidadePublicaController {

    private final DisponibilidadeService disponibilidadeService;

    // exemplo: /saloes/barbearia-teste/disponibilidade/dias?mes=2026-11&profissionais=id1,id2&duracao=50
    @GetMapping("/dias")
    public List<CalendarioResponse.Dia> dias(@PathVariable String slug,
                                             @RequestParam YearMonth mes,
                                             @RequestParam(required = false) List<UUID> profissionais,
                                             @RequestParam(defaultValue = "30") int duracao) {
        return disponibilidadeService.diasDoMes(slug, profissionais, mes, duracao);
    }

    // exemplo: /saloes/barbearia-teste/disponibilidade/horarios?data=2026-11-17&profissionais=id1&duracao=30
    @GetMapping("/horarios")
    public List<CalendarioResponse.Horario> horarios(@PathVariable String slug,
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                                     @RequestParam(required = false) List<UUID> profissionais,
                                                     @RequestParam(defaultValue = "30") int duracao) {
        return disponibilidadeService.horariosDoDia(slug, profissionais, data, duracao);
    }

    // consulta antiga por serviço (Swagger): /saloes/barbearia-teste/disponibilidade?servicoId=...&data=2026-11-10
    @GetMapping
    public DisponibilidadeResponse consultar(@PathVariable String slug,
                                             @RequestParam UUID servicoId,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return disponibilidadeService.consultar(slug, servicoId, data);
    }
}
