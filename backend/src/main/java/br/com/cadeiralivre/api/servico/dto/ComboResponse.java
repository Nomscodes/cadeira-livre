package br.com.cadeiralivre.api.servico.dto;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.servico.Combo;
import br.com.cadeiralivre.api.servico.Servico;

/** Combo no formato do front. A duração (em minutos) é a soma dos serviços, calculada pelo servidor. */
public record ComboResponse(UUID id, String nome, List<UUID> servicoIds, BigDecimal preco, int duracao,
                            List<UUID> profissionalIds) {

    public static ComboResponse de(Combo c) {
        // os ids vão sempre na mesma ordem (pelo nome), para a tela não "pular" a cada salvamento
        List<UUID> servicoIds = c.getServicos().stream()
                .sorted(Comparator.comparing(Servico::getNome)).map(Servico::getId).toList();
        List<UUID> profissionalIds = c.getProfissionais().stream()
                .sorted(Comparator.comparing(Profissional::getNome)).map(Profissional::getId).toList();
        return new ComboResponse(c.getId(), c.getNome(), servicoIds, c.getPreco(), c.duracaoMinutos(), profissionalIds);
    }
}
