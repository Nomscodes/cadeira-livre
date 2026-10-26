package br.com.cadeiralivre.api.servico.dto;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.cadeiralivre.api.servico.Servico;

public record ServicoResponse(UUID id, String nome, String descricao, BigDecimal preco,
                              int duracaoMinutos, boolean ativo) {

    public static ServicoResponse de(Servico s) {
        return new ServicoResponse(s.getId(), s.getNome(), s.getDescricao(), s.getPreco(),
                s.getDuracaoMinutos(), s.isAtivo());
    }
}
