package br.com.cadeiralivre.api.servico;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.cadeiralivre.api.servico.dto.ComboRequest;
import br.com.cadeiralivre.api.servico.dto.ComboResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Combos do painel (exige JWT com papel DONO). A barbearia vem do token, nunca do corpo da requisição.
 * A lista dos combos vem junto com os serviços, em GET /painel/servicos.
 */
@RestController
@RequestMapping("/painel/combos")
@RequiredArgsConstructor
public class PainelComboController {

    private final ComboService comboService;

    @PostMapping
    public ComboResponse criar(@Valid @RequestBody ComboRequest req) {
        return comboService.criar(req);
    }

    @PutMapping("/{id}")
    public ComboResponse atualizar(@PathVariable UUID id, @Valid @RequestBody ComboRequest req) {
        return comboService.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        comboService.excluir(id);
    }
}
