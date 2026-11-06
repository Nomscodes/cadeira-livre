package br.com.cadeiralivre.api.horario.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import br.com.cadeiralivre.api.horario.BloqueioHorario;
import br.com.cadeiralivre.api.profissional.Profissional;

public record BloqueioResponse(UUID id, LocalDate data, LocalTime horaInicio, LocalTime horaFim, boolean diaInteiro,
                               String motivo, UUID profissionalId, String profissionalNome) {

    public static BloqueioResponse de(BloqueioHorario b) {
        Profissional p = b.getProfissional();
        return new BloqueioResponse(b.getId(), b.getData(), b.getHoraInicio(), b.getHoraFim(),
                b.getHoraInicio() == null, b.getMotivo(),
                p == null ? null : p.getId(),
                p == null ? null : p.getNome());
    }
}
