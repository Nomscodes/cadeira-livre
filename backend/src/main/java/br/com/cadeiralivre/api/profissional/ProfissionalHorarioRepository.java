package br.com.cadeiralivre.api.profissional;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Horários de trabalho dos profissionais. Sempre filtrados pelo profissional ou pela barbearia dele. */
public interface ProfissionalHorarioRepository extends JpaRepository<ProfissionalHorario, UUID> {

    List<ProfissionalHorario> findAllByProfissionalId(UUID profissionalId);

    // os horários de todos os profissionais do salão numa consulta só (lista do painel)
    List<ProfissionalHorario> findAllByProfissionalBarbeariaId(UUID barbeariaId);
}
