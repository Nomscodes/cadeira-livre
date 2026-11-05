package br.com.cadeiralivre.api.servico;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Todos os métodos recebem o id da barbearia: é assim que o isolamento por tenant é garantido. */
public interface ServicoRepository extends JpaRepository<Servico, UUID> {

    List<Servico> findAllByBarbeariaIdOrderByNome(UUID barbeariaId);

    List<Servico> findAllByBarbeariaIdAndAtivoTrueOrderByNome(UUID barbeariaId);

    Optional<Servico> findByIdAndBarbeariaId(UUID id, UUID barbeariaId);
}
