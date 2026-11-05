package br.com.cadeiralivre.api.profissional.dto;

import java.util.List;
import java.util.UUID;

import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.servico.Servico;

public record ProfissionalResponse(UUID id, String nome, String telefone, boolean ativo, List<UUID> servicoIds) {

    public static ProfissionalResponse de(Profissional p) {
        List<UUID> servicoIds = p.getServicos().stream().map(Servico::getId).toList();
        return new ProfissionalResponse(p.getId(), p.getNome(), p.getTelefone(), p.isAtivo(), servicoIds);
    }
}
