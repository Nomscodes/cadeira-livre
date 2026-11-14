package br.com.cadeiralivre.api.servico.dto;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.servico.Servico;

/** Serviço no formato do front: duracao em minutos e os profissionais que fazem o serviço. */
public record ServicoResponse(UUID id, String nome, String descricao, BigDecimal preco, int duracao,
                              List<UUID> profissionalIds, boolean destaque) {

    public static ServicoResponse de(Servico s) {
        // ids sempre na mesma ordem (pelo nome do profissional)
        List<UUID> profissionalIds = s.getProfissionais().stream()
                .sorted(Comparator.comparing(Profissional::getNome)).map(Profissional::getId).toList();
        return new ServicoResponse(s.getId(), s.getNome(), s.getDescricao(), s.getPreco(), s.getDuracaoMinutos(),
                profissionalIds, s.isDestaque());
    }
}
