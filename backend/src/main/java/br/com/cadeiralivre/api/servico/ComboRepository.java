package br.com.cadeiralivre.api.servico;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Todos os métodos recebem o id da barbearia: é assim que o isolamento por tenant é garantido. */
public interface ComboRepository extends JpaRepository<Combo, UUID> {

    // já traz os serviços e os profissionais de cada combo na mesma consulta
    @EntityGraph(attributePaths = {"servicos", "profissionais"})
    List<Combo> findAllByBarbeariaIdAndAtivoTrueOrderByNome(UUID barbeariaId);

    Optional<Combo> findByIdAndBarbeariaIdAndAtivoTrue(UUID id, UUID barbeariaId);

    // combos que têm o serviço (usado quando o serviço é excluído)
    List<Combo> findAllByBarbeariaIdAndAtivoTrueAndServicosId(UUID barbeariaId, UUID servicoId);
}
