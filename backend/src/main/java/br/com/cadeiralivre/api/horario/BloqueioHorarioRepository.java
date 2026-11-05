package br.com.cadeiralivre.api.horario;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BloqueioHorarioRepository extends JpaRepository<BloqueioHorario, UUID> {

    List<BloqueioHorario> findAllByBarbeariaIdAndData(UUID barbeariaId, LocalDate data);
}
