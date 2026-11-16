package br.com.cadeiralivre.api.agendamento.dto;

import java.util.UUID;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;

/** Resposta do pedido de agendamento: o id e o contato do salão, que o front mostra na confirmação. */
public record AgendamentoCriadoResponse(UUID id, String status, Salao salao) {

    public record Salao(String nome, String endereco, String whatsapp) {
    }

    public static AgendamentoCriadoResponse de(Agendamento a) {
        Barbearia b = a.getBarbearia();
        return new AgendamentoCriadoResponse(a.getId(), a.getStatus().name(),
                new Salao(b.getNomeFantasia(), b.getEndereco(), b.getTelefone()));
    }
}
