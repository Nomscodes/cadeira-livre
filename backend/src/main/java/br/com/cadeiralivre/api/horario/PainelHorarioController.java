package br.com.cadeiralivre.api.horario;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.horario.dto.HorariosDoPainelResponse;
import br.com.cadeiralivre.api.horario.dto.SemanaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Horários no painel (exige JWT com papel DONO). A barbearia vem do token, nunca do corpo da requisição. */
@RestController
@RequestMapping("/painel/horarios")
@RequiredArgsConstructor
public class PainelHorarioController {

    private final HorarioService horarioService;

    @GetMapping
    public HorariosDoPainelResponse listar() {
        return horarioService.listar();
    }

    @PutMapping("/salao")
    public HorariosDoPainelResponse.Avisos salvarDoSalao(@Valid @RequestBody SemanaRequest req) {
        return new HorariosDoPainelResponse.Avisos(horarioService.salvarDoSalao(req));
    }

    @PutMapping("/profissionais/{id}")
    public HorariosDoPainelResponse.Avisos salvarDoProfissional(@PathVariable UUID id, @Valid @RequestBody SemanaRequest req) {
        return new HorariosDoPainelResponse.Avisos(horarioService.salvarDoProfissional(id, req));
    }
}
