package br.com.cadeiralivre.api.profissional;

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

import br.com.cadeiralivre.api.profissional.dto.ProfissionalRequest;
import br.com.cadeiralivre.api.profissional.dto.ProfissionalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Rotas dos profissionais no painel (exige JWT com papel DONO). A barbearia vem do token, nunca do corpo. */
@RestController
@RequestMapping("/painel/profissionais")
@RequiredArgsConstructor
public class PainelProfissionalController {

    private final ProfissionalService profissionalService;

    /** O que a tela de profissionais mostra: os profissionais e os serviços que podem ser escolhidos. */
    public record ProfissionaisDoPainel(List<ProfissionalResponse> profissionais, List<ServicoResumo> servicos) {
    }

    public record ServicoResumo(UUID id, String nome) {
    }

    @GetMapping
    public ProfissionaisDoPainel listar() {
        List<ServicoResumo> servicos = profissionalService.servicosParaEscolher().stream()
                .map(s -> new ServicoResumo(s.getId(), s.getNome())).toList();
        return new ProfissionaisDoPainel(profissionalService.listar(), servicos);
    }

    // devolve { profissional, convite }: o código que o funcionário usa para criar o login
    @PostMapping
    public ProfissionalResponse.ComConvite criar(@Valid @RequestBody ProfissionalRequest req) {
        return profissionalService.criar(req);
    }

    @PutMapping("/{id}")
    public ProfissionalResponse.ComConvite atualizar(@PathVariable UUID id, @Valid @RequestBody ProfissionalRequest req) {
        return profissionalService.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id) {
        profissionalService.excluir(id);
    }
}
