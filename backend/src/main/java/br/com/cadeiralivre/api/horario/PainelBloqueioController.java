package br.com.cadeiralivre.api.horario;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.horario.dto.BloqueioRequest;
import br.com.cadeiralivre.api.horario.dto.BloqueioResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Dias em que o salão inteiro fica fechado (RF20), como o calendário do painel usa: um por data.
 * Exige JWT com papel DONO; a barbearia vem do token.
 */
@RestController
@RequestMapping("/painel/bloqueios")
@RequiredArgsConstructor
public class PainelBloqueioController {

    private final BloqueioService bloqueioService;

    /** Corpo do PUT de bloqueio e de folga: só o motivo (opcional). */
    public record MotivoRequest(@Size(max = 160) String motivo) {
    }

    /** Com ?mes=AAAA-MM, os dias fechados daquele mês; sem o mês, todos os bloqueios de hoje em diante. */
    @GetMapping
    public List<BloqueioResponse> listar(@RequestParam(required = false) YearMonth mes) {
        return mes == null ? bloqueioService.listar() : bloqueioService.bloqueiosDoMes(mes);
    }

    @PutMapping("/{data}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fecharDia(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                          @Valid @RequestBody(required = false) MotivoRequest req) {
        bloqueioService.fecharDia(data, req == null ? null : req.motivo());
    }

    @DeleteMapping("/{data}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reabrirDia(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        bloqueioService.reabrirDia(data);
    }

    /** Bloqueio de um horário dentro do dia (ou folga), pelo Swagger. */
    @PostMapping
    public BloqueioResponse criar(@Valid @RequestBody BloqueioRequest req) {
        return bloqueioService.criar(req);
    }
}
