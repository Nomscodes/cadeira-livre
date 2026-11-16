package br.com.cadeiralivre.api.avaliacao.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AvaliacaoRequest(
        @NotNull(message = "informe uma nota de 1 a 5")
        @Min(value = 1, message = "a nota vai de 1 a 5")
        @Max(value = 5, message = "a nota vai de 1 a 5")
        Integer nota,
        @Size(max = 500, message = "o comentário pode ter no máximo 500 caracteres")
        String comentario) {
}
