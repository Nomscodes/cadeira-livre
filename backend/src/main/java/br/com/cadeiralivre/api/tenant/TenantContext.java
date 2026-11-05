package br.com.cadeiralivre.api.tenant;

import java.util.UUID;

import br.com.cadeiralivre.api.common.exception.ApiException;

/**
 * Guarda a barbearia (tenant) da requisição atual, lida do JWT.
 * Todo service do painel usa require() e filtra as queries por barbearia_id (RNF04/RNF05).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> ATUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID barbeariaId) {
        ATUAL.set(barbeariaId);
    }

    public static UUID require() {
        UUID id = ATUAL.get();
        if (id == null) throw ApiException.proibido("Usuário sem barbearia associada.");
        return id;
    }

    public static void clear() {
        ATUAL.remove();
    }
}
