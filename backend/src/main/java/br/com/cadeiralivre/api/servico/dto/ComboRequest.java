package br.com.cadeiralivre.api.servico.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Combo enviado pelo dono na tela de serviços, com os nomes que o front usa. */
public record ComboRequest(
        @NotBlank(message = "informe o nome do combo") @Size(max = 120) String nome,

        @NotNull(message = "escolha os serviços do combo")
        @Size(min = 2, message = "um combo precisa de pelo menos dois serviços")
        List<UUID> servicoIds,

        // valor final do pacote
        @NotNull(message = "informe o valor final do combo")
        @DecimalMin(value = "0.0", inclusive = false, message = "informe o valor final do combo")
        BigDecimal preco,

        // profissionais que fazem o combo; vazio ou null = ninguém por enquanto
        List<UUID> profissionalIds) {
}
