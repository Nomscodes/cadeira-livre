package br.com.cadeiralivre.api.servico;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.servico.dto.ServicoRequest;
import br.com.cadeiralivre.api.servico.dto.ServicoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/painel/servicos")
@RequiredArgsConstructor
public class PainelServicoController {

    private final ServicoService servicoService;

    @GetMapping
    public List<ServicoResponse> listar() {
        return servicoService.listar();
    }

    @PostMapping
    public ServicoResponse criar(@Valid @RequestBody ServicoRequest req) {
        return servicoService.criar(req);
    }

    @PutMapping("/{id}")
    public ServicoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ServicoRequest req) {
        return servicoService.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        servicoService.excluir(id);
    }
}
