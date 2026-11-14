package br.com.cadeiralivre.api.horario;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.horario.dto.BloqueioResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Folgas de um profissional por data (RF20), como o calendário do painel usa.
 * Exige JWT com papel DONO; a barbearia vem do token e o profissional tem que ser dela.
 */
@RestController
@RequestMapping("/painel/folgas")
@RequiredArgsConstructor
public class PainelFolgaController {

    private final BloqueioService bloqueioService;

    @GetMapping
    public List<BloqueioResponse> listar(@RequestParam YearMonth mes, @RequestParam(required = false) UUID profissionalId) {
        return bloqueioService.folgasDoMes(mes, profissionalId);
    }

    @PutMapping("/{profissionalId}/{data}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void darFolga(@PathVariable UUID profissionalId,
                         @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                         @Valid @RequestBody(required = false) PainelBloqueioController.MotivoRequest req) {
        bloqueioService.darFolga(profissionalId, data, req == null ? null : req.motivo());
    }

    @DeleteMapping("/{profissionalId}/{data}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void tirarFolga(@PathVariable UUID profissionalId,
                           @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        bloqueioService.tirarFolga(profissionalId, data);
    }
}
