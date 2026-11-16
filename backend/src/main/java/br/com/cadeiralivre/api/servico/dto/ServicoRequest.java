package br.com.cadeiralivre.api.servico.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Serviço enviado pelo dono na tela de serviços (RF10), com os nomes que o front usa. */
public record ServicoRequest(
        @NotBlank(message = "informe o nome do serviço") @Size(max = 120) String nome,

        @Size(max = 500) String descricao,

        // pode ser 0 (serviço sem custo), como no front
        @NotNull(message = "informe um valor válido")
        @DecimalMin(value = "0.0", message = "informe um valor válido")
        BigDecimal preco,

        // tempo médio em minutos
        @NotNull(message = "informe o tempo médio")
        @Min(value = 5, message = "o tempo médio precisa ser de pelo menos 5 minutos")
        @Max(value = 600, message = "o tempo médio pode ser de no máximo 10 horas")
        Integer duracao,

        // profissionais que fazem o serviço; vazio ou null = ninguém por enquanto
        List<UUID> profissionalIds) {
}
