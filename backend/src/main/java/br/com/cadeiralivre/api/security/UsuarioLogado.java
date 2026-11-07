package br.com.cadeiralivre.api.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Diz quem está logado na requisição atual.
 * O JwtAuthenticationFilter guarda o id do usuário (subject do token) como "nome" da autenticação.
 */
public final class UsuarioLogado {

    private UsuarioLogado() {
    }

    /** Id do cliente logado, ou null se ninguém estiver logado (ou se quem está logado não for cliente). */
    public static UUID clienteId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        boolean ehCliente = auth.getAuthorities().stream()
                .anyMatch(papel -> "ROLE_CLIENTE".equals(papel.getAuthority()));
        return ehCliente ? UUID.fromString(auth.getName()) : null;
    }
}
