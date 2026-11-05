package br.com.cadeiralivre.api.barbearia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Customização white-label enviada pelo dono (RF06–RF09). */
public record PortalRequest(
        @NotBlank @Size(max = 120) String nomeFantasia,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "use o formato #RRGGBB") String corPrimaria,
        // logo em base64, do jeito que o front envia (ex.: "data:image/png;base64,iVBOR...")
        @Size(max = 1_400_000, message = "a logo pode ter no máximo 1 MB")
        @Pattern(regexp = "^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/]+={0,2}$",
                message = "envie a logo como imagem PNG, JPG ou WEBP")
        String logoUrl,
        @Size(max = 255) String endereco,
        @Size(max = 20) String telefone,
        Boolean publico) {
}
