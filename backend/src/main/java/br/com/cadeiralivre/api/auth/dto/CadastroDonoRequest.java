package br.com.cadeiralivre.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CadastroDonoRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(max = 160) String razaoSocial,
        @Pattern(regexp = "\\d{11}|\\d{14}", message = "informe CPF (11) ou CNPJ (14) só com números") String documento,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String senha) {
}
