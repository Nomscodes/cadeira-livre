package br.com.cadeiralivre.api.agendamento;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.agendamento.dto.DisponibilidadeResponse;
import lombok.RequiredArgsConstructor;

/** Rota pública (sem JWT): o cliente consulta os horários livres no portal da barbearia (RF13). */
@RestController
@RequestMapping("/saloes/{slug}/disponibilidade")
@RequiredArgsConstructor
public class DisponibilidadePublicaController {

    private final DisponibilidadeService disponibilidadeService;

    // exemplo: /saloes/barbearia-teste/disponibilidade?servicoId=...&data=2026-11-10
    @GetMapping
    public DisponibilidadeResponse consultar(@PathVariable String slug,
                                             @RequestParam UUID servicoId,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return disponibilidadeService.consultar(slug, servicoId, data);
    }
}
