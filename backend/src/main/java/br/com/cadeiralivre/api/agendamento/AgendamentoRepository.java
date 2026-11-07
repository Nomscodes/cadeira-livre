package br.com.cadeiralivre.api.agendamento;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgendamentoRepository extends JpaRepository<Agendamento, UUID> {

    // a lista do painel já traz o serviço e o profissional juntos (sem uma consulta extra por agendamento)
    @EntityGraph(attributePaths = {"servico", "profissional"})
    List<Agendamento> findAllByBarbeariaIdOrderByDataDescHoraInicioDesc(UUID barbeariaId);

    // agenda de um dia no painel, em ordem de horário
    @EntityGraph(attributePaths = {"servico", "profissional"})
    List<Agendamento> findAllByBarbeariaIdAndDataOrderByHoraInicioAscProfissionalNomeAsc(UUID barbeariaId, LocalDate data);

    Optional<Agendamento> findByIdAndBarbeariaId(UUID id, UUID barbeariaId);

    // histórico do cliente: os agendamentos dele em todas as barbearias, do mais recente para o mais antigo
    @EntityGraph(attributePaths = {"barbearia", "servico", "profissional"})
    List<Agendamento> findAllByClienteIdOrderByDataDescHoraInicioDesc(UUID clienteId);

    // o cliente só enxerga (e cancela) os agendamentos que são dele
    Optional<Agendamento> findByIdAndClienteId(UUID id, UUID clienteId);

    // agendamentos da barbearia em um dia e status (a disponibilidade usa os APROVADOS)
    List<Agendamento> findAllByBarbeariaIdAndDataAndStatus(UUID barbeariaId, LocalDate data, StatusAgendamento status);

    /** RF21: o profissional já tem agendamento no status dado que sobreponha o intervalo [inicio, fim)? */
    @Query("""
            select count(a) > 0 from Agendamento a
            where a.profissional.id = :profissionalId
              and a.data = :data
              and a.status = :status
              and a.horaInicio < :fim
              and a.horaFim > :inicio
            """)
    boolean existeConflito(@Param("profissionalId") UUID profissionalId,
                           @Param("data") LocalDate data,
                           @Param("status") StatusAgendamento status,
                           @Param("inicio") LocalTime inicio,
                           @Param("fim") LocalTime fim);
}
