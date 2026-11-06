package br.com.cadeiralivre.api.horario;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.horario.dto.BloqueioRequest;
import br.com.cadeiralivre.api.horario.dto.BloqueioResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/painel/bloqueios")
@RequiredArgsConstructor
public class PainelBloqueioController {

    private final BloqueioService bloqueioService;

    @GetMapping
    public List<BloqueioResponse> listar() {
        return bloqueioService.listar();
    }

    @PostMapping
    public BloqueioResponse criar(@Valid @RequestBody BloqueioRequest req) {
        return bloqueioService.criar(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        bloqueioService.excluir(id);
    }
}
