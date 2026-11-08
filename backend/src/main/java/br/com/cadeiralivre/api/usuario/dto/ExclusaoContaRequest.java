package br.com.cadeiralivre.api.usuario.dto;

import jakarta.validation.constraints.NotBlank;

// a senha é pedida de novo para confirmar uma ação que não tem volta
public record ExclusaoContaRequest(@NotBlank String senha) {
}
