package br.com.cadeiralivre.api.horario;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.horario.dto.HorarioDiaResponse;
import br.com.cadeiralivre.api.horario.dto.HorarioSemanaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/painel/horarios")
@RequiredArgsConstructor
public class PainelHorarioController {

    private final HorarioService horarioService;

    @GetMapping
    public List<HorarioDiaResponse> listar() {
        return horarioService.listar();
    }

    @PutMapping
    public List<HorarioDiaResponse> salvar(@Valid @RequestBody HorarioSemanaRequest req) {
        return horarioService.salvar(req);
    }
}
