package br.com.cadeiralivre.api.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Funcionário criando a conta com o código de convite que recebeu do responsável do salão. */
public record ConviteRequest(
        @NotBlank(message = "informe o código de convite") @Size(max = 8) String codigo,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72, message = "A senha precisa ter ao menos 8 caracteres.") String senha,
        @NotNull(message = "é preciso aceitar os termos de uso e a política de privacidade")
        @AssertTrue(message = "é preciso aceitar os termos de uso e a política de privacidade")
        Boolean aceitouTermos) {
}
