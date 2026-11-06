package br.com.cadeiralivre.api.horario;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.profissional.Profissional;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bloqueio de data ou horário, como feriado ou manutenção (RF20).
 * Sem hora = dia inteiro. Com profissional = folga só dele; sem profissional = barbearia toda.
 */
@Entity
@Table(name = "bloqueio_horario")
@Getter
@Setter
@NoArgsConstructor
public class BloqueioHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbearia_id", nullable = false)
    private Barbearia barbearia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id")
    private Profissional profissional;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fim")
    private LocalTime horaFim;

    @Column(length = 160)
    private String motivo;
}
