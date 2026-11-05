package br.com.cadeiralivre.api.profissional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Todos os métodos recebem o id da barbearia: é assim que o isolamento por tenant é garantido. */
public interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {

    // já traz os serviços de cada profissional na mesma consulta (sem uma consulta extra por profissional)
    @EntityGraph(attributePaths = "servicos")
    List<Profissional> findAllByBarbeariaIdOrderByNome(UUID barbeariaId);

    Optional<Profissional> findByIdAndBarbeariaId(UUID id, UUID barbeariaId);
}
