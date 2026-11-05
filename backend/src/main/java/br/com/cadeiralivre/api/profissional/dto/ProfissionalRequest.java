package br.com.cadeiralivre.api.profissional.dto;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProfissionalRequest(
        @NotBlank @Size(max = 120) String nome,
        @Size(max = 20) String telefone,
        // lista completa dos serviços que ele faz: o que não vier aqui é desvinculado
        Set<@NotNull UUID> servicoIds,
        // só na edição: false desativa, true reativa, vazio mantém como está
        Boolean ativo) {
}
