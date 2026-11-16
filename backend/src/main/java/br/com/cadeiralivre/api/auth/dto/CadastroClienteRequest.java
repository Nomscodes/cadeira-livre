package br.com.cadeiralivre.api.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CadastroClienteRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email String email,
        @NotBlank @Size(max = 20) String telefone,
        @NotBlank @Size(min = 8, max = 72) String senha,
        @NotNull(message = "é preciso aceitar os termos de uso e a política de privacidade")
        @AssertTrue(message = "é preciso aceitar os termos de uso e a política de privacidade")
        Boolean aceitouTermos) {
}
