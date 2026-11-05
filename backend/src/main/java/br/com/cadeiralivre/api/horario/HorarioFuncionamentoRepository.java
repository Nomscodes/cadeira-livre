package br.com.cadeiralivre.api.horario;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HorarioFuncionamentoRepository extends JpaRepository<HorarioFuncionamento, UUID> {

    List<HorarioFuncionamento> findAllByBarbeariaId(UUID barbeariaId);
}
