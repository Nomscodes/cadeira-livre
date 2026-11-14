package br.com.cadeiralivre.api.profissional.dto;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Profissional enviado pelo dono na tela de profissionais, com os nomes que o front usa. */
public record ProfissionalRequest(
        @NotBlank(message = "informe o nome completo") @Size(max = 120) String nome,

        // nome mostrado aos clientes (ex.: só o primeiro nome ou um apelido)
        @Size(max = 60) String nomeSocial,

        @Size(max = 20) String whatsapp,

        // os avisos de agendamento vão também para o WhatsApp dele (precisa do WhatsApp preenchido)
        Boolean encaminhar,

        // false = não atende clientes (só gerencia); null = atende
        Boolean atende,

        // lista completa dos serviços que ele faz: o que não vier aqui é desvinculado
        Set<@NotNull UUID> servicoIds) {
}
