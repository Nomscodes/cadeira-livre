package br.com.cadeiralivre.api.agendamento;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID> {

    List<Agendamento> findAllByBarbeariaIdOrderByDataDescHoraInicioDesc(UUID barbeariaId);

    Optional<Agendamento> findByIdAndBarbeariaId(UUID id, UUID barbeariaId);

    /** RF21: existe agendamento no status dado que sobreponha o intervalo [inicio, fim)? */
    @Query("""
            select count(a) > 0 from Agendamento a
            where a.barbearia.id = :barbeariaId
              and a.data = :data
              and a.status = :status
              and a.horaInicio < :fim
              and a.horaFim > :inicio
            """)
    boolean existeConflito(@Param("barbeariaId") UUID barbeariaId,
                           @Param("data") LocalDate data,
                           @Param("status") StatusAgendamento status,
                           @Param("inicio") LocalTime inicio,
                           @Param("fim") LocalTime fim);
}
