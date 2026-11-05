package br.com.cadeiralivre.api.auth.dto;

import java.util.UUID;

public record SessaoResponse(String token, UsuarioSessao usuario) {

    public record UsuarioSessao(UUID id, String nome, String papel, UUID barbeariaId) {
    }
}
