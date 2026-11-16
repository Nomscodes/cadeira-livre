package br.com.cadeiralivre.api.horario;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Todos os métodos recebem o id da barbearia: é assim que o isolamento por tenant é garantido. */
public interface BloqueioHorarioRepository extends JpaRepository<BloqueioHorario, UUID> {

    List<BloqueioHorario> findAllByBarbeariaIdAndData(UUID barbeariaId, LocalDate data);

    // bloqueios a partir de uma data, já trazendo o profissional junto (sem uma consulta extra por bloqueio)
    @EntityGraph(attributePaths = "profissional")
    List<BloqueioHorario> findAllByBarbeariaIdAndDataGreaterThanEqualOrderByDataAscHoraInicioAsc(
            UUID barbeariaId, LocalDate data);

    Optional<BloqueioHorario> findByIdAndBarbeariaId(UUID id, UUID barbeariaId);

    // bloqueios e folgas de um período (o calendário do painel mostra um mês por vez)
    @EntityGraph(attributePaths = "profissional")
    List<BloqueioHorario> findAllByBarbeariaIdAndDataBetweenOrderByData(UUID barbeariaId, LocalDate inicio, LocalDate fim);
}
