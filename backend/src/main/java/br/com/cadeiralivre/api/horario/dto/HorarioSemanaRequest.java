package br.com.cadeiralivre.api.horario.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// só os dias enviados são alterados; os outros continuam como estão
public record HorarioSemanaRequest(
        @NotEmpty @Size(max = 7) List<@NotNull @Valid HorarioDiaRequest> dias) {
}
