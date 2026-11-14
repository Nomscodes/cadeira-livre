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

import br.com.cadeiralivre.api.servico.dto.ComboResponse;
import br.com.cadeiralivre.api.servico.dto.ServicoRequest;
import br.com.cadeiralivre.api.servico.dto.ServicoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Rotas do catálogo no painel (exige JWT com papel DONO). A barbearia vem do token, nunca do corpo. */
@RestController
@RequestMapping("/painel/servicos")
@RequiredArgsConstructor
public class PainelServicoController {

    private final ServicoService servicoService;
    private final ComboService comboService;

    /** O que a tela de serviços mostra: os serviços, os combos e quem pode ser escolhido para eles. */
    public record ServicosDoPainel(List<ServicoResponse> servicos, List<ComboResponse> combos,
                                   List<ProfissionalResumo> profissionais) {
    }

    public record ProfissionalResumo(UUID id, String nome) {
    }

    @GetMapping
    public ServicosDoPainel listar() {
        List<ProfissionalResumo> profissionais = servicoService.profissionaisQueAtendem().stream()
                .map(p -> new ProfissionalResumo(p.getId(), p.getNome())).toList();
        return new ServicosDoPainel(servicoService.listar(), comboService.listar(), profissionais);
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
