package br.com.cadeiralivre.api.barbearia.dto;

import java.util.List;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.servico.dto.ServicoResponse;

/** Visão pública do portal. Se publico = false, só slug, nome e o flag são preenchidos. */
public record PortalPublicoResponse(String slug, String nomeFantasia, boolean publico, String logoUrl,
                                    String corPrimaria, String endereco, String telefone,
                                    List<ServicoResponse> servicos) {

    public static PortalPublicoResponse oculto(Barbearia b) {
        return new PortalPublicoResponse(b.getSlug(), b.getNomeFantasia(), false, null, null, null, null, List.of());
    }

    public static PortalPublicoResponse de(Barbearia b, List<ServicoResponse> servicos) {
        return new PortalPublicoResponse(b.getSlug(), b.getNomeFantasia(), true, b.getLogoUrl(),
                b.getCorPrimaria(), b.getEndereco(), b.getTelefone(), servicos);
    }
}
