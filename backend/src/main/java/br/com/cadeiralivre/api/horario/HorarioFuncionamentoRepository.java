package br.com.cadeiralivre.api.horario;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Todos os métodos recebem o id da barbearia: é assim que o isolamento por tenant é garantido. */
public interface HorarioFuncionamentoRepository extends JpaRepository<HorarioFuncionamento, UUID> {

    List<HorarioFuncionamento> findAllByBarbeariaId(UUID barbeariaId);

    Optional<HorarioFuncionamento> findByBarbeariaIdAndDiaSemana(UUID barbeariaId, DayOfWeek diaSemana);
}
