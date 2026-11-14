package br.com.cadeiralivre.api.agendamento.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pedido de agendamento do cliente (RF14), no formato do front: um ou mais itens (serviço OU combo, cada um com o
 * profissional escolhido), a data, a hora de início e o contato do cliente.
 */
public record AgendamentoRequest(
        @NotEmpty(message = "escolha ao menos um serviço") @Size(max = 10) List<@NotNull @Valid Item> itens,
        @NotNull(message = "escolha a data") LocalDate data,
        @NotNull(message = "escolha o horário") LocalTime hora,
        @NotBlank(message = "informe seu nome") @Size(max = 120) String clienteNome,
        @NotBlank(message = "informe seu WhatsApp") @Size(max = 20) String clienteWhats) {

    /** Informe servicoId OU comboId, sempre com o profissional. */
    public record Item(UUID servicoId, UUID comboId, @NotNull(message = "escolha o profissional") UUID profissionalId) {
    }
}
