package br.com.cadeiralivre.api.profissional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Horário de trabalho de um profissional em um dia da semana.
 * Dia com ativo = false é folga fixa (o profissional não atende nesse dia da semana).
 */
@Entity
@Table(name = "profissional_horario")
@Getter
@Setter
@NoArgsConstructor
public class ProfissionalHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DayOfWeek diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    @Column(nullable = false)
    private boolean ativo = true;

    public ProfissionalHorario(Profissional profissional, DayOfWeek diaSemana, LocalTime horaInicio, LocalTime horaFim,
                               boolean ativo) {
        this.profissional = profissional;
        this.diaSemana = diaSemana;
        this.horaInicio = horaInicio;
        this.horaFim = horaFim;
        this.ativo = ativo;
    }

    /** Horário de quem acabou de ser cadastrado, igual ao do front: segunda a sexta, das 9h às 18h. */
    public static List<ProfissionalHorario> semanaPadrao(Profissional profissional) {
        List<ProfissionalHorario> semana = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            boolean diaUtil = dia != DayOfWeek.SATURDAY && dia != DayOfWeek.SUNDAY;
            semana.add(new ProfissionalHorario(profissional, dia, LocalTime.of(9, 0), LocalTime.of(18, 0), diaUtil));
        }
        return semana;
    }
}
