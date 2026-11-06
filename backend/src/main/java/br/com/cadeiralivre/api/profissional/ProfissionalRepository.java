package br.com.cadeiralivre.api.profissional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Todos os métodos recebem o id da barbearia: é assim que o isolamento por tenant é garantido. */
public interface ProfissionalRepository extends JpaRepository<Profissional, UUID> {

    // já traz os serviços de cada profissional na mesma consulta (sem uma consulta extra por profissional)
    @EntityGraph(attributePaths = "servicos")
    List<Profissional> findAllByBarbeariaIdOrderByNome(UUID barbeariaId);

    Optional<Profissional> findByIdAndBarbeariaId(UUID id, UUID barbeariaId);

    // profissionais ativos que fazem o serviço escolhido pelo cliente
    @Query("""
            select p from Profissional p join p.servicos s
            where p.barbearia.id = :barbeariaId and p.ativo = true and s.id = :servicoId
            order by p.nome
            """)
    List<Profissional> buscarAtivosQueFazemOServico(@Param("barbeariaId") UUID barbeariaId,
                                                    @Param("servicoId") UUID servicoId);
}
