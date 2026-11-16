package br.com.cadeiralivre.api.auth.dto;

import java.util.UUID;

public record SessaoResponse(String token, UsuarioSessao usuario) {

    // profissionalId: o profissional ligado à conta (o dono ou um funcionário); null para o cliente
    public record UsuarioSessao(UUID id, String nome, String papel, UUID barbeariaId, UUID profissionalId) {
    }
}
