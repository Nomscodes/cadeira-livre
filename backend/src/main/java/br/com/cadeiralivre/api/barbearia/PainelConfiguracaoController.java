package br.com.cadeiralivre.api.barbearia;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

/** Tela "Configurações" do painel (exige JWT com papel DONO). A barbearia vem do token, nunca do corpo. */
@RestController
@RequestMapping("/painel/configuracoes")
@RequiredArgsConstructor
public class PainelConfiguracaoController {

    private final BarbeariaService barbeariaService;

    public record ConfiguracoesRequest(
            @NotNull(message = "escolha o tipo de atendimento")
            @Pattern(regexp = "local|domicilio", message = "escolha local ou domicilio") String atendimento,
            @NotNull(message = "Informe as horas de antecedência entre 0 e 72.")
            @Min(value = 0, message = "Informe as horas de antecedência entre 0 e 72.")
            @Max(value = 72, message = "Informe as horas de antecedência entre 0 e 72.") Integer cancelamentoHoras,
            Boolean funcionariosEditamHorario) {
    }

    public record TransferenciaRequest(@NotNull(message = "escolha o profissional") UUID profissionalId) {
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void salvar(@Valid @RequestBody ConfiguracoesRequest req) {
        barbeariaService.salvarConfiguracoes(req.atendimento(), req.cancelamentoHoras(),
                Boolean.TRUE.equals(req.funcionariosEditamHorario()));
    }

    @PostMapping("/transferir-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferirAdministrador(@Valid @RequestBody TransferenciaRequest req) {
        barbeariaService.transferirAdministrador(req.profissionalId());
    }
}
