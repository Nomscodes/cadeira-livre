package br.com.cadeiralivre.api.barbearia.dto;

import br.com.cadeiralivre.api.barbearia.Barbearia;

/** Visão completa do portal, só para o dono (painel). */
public record PortalResponse(String slug, String nomeFantasia, String razaoSocial, String logoUrl,
                             String corPrimaria, String endereco, String telefone, boolean publico) {

    public static PortalResponse de(Barbearia b) {
        return new PortalResponse(b.getSlug(), b.getNomeFantasia(), b.getRazaoSocial(), b.getLogoUrl(),
                b.getCorPrimaria(), b.getEndereco(), b.getTelefone(), b.isPublico());
    }
}
