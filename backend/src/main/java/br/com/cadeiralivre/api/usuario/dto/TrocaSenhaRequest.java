package br.com.cadeiralivre.api.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// mesmas regras de tamanho da senha do cadastro
public record TrocaSenhaRequest(
        @NotBlank String senhaAtual,
        @NotBlank @Size(min = 8, max = 72) String novaSenha) {
}
