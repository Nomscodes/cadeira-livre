package br.com.cadeiralivre.api.usuario.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.cadeiralivre.api.usuario.Usuario;

/** Dados pessoais que o sistema guarda do cliente (LGPD: direito de acesso). Nunca devolve a senha. */
public record ContaResponse(UUID id, String nome, String email, String telefone,
                            Instant aceiteTermosEm, Instant criadoEm) {

    public static ContaResponse de(Usuario u) {
        return new ContaResponse(u.getId(), u.getNome(), u.getEmail(), u.getTelefone(),
                u.getAceiteTermosEm(), u.getCriadoEm());
    }
}
