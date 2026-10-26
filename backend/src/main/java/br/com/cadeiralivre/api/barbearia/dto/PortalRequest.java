package br.com.cadeiralivre.api.barbearia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Customização white-label enviada pelo dono (RF06–RF09). */
public record PortalRequest(
        @NotBlank @Size(max = 120) String nomeFantasia,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "use o formato #RRGGBB") String corPrimaria,
        String logoUrl,
        @Size(max = 255) String endereco,
        @Size(max = 20) String telefone,
        Boolean publico) {
}
