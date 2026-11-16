package br.com.cadeiralivre.api.avaliacao;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, UUID> {

    // cada atendimento só pode ser avaliado uma vez
    boolean existsByAgendamentoId(UUID agendamentoId);

    // as 20 avaliações mais recentes do portal, já com o serviço e o profissional do atendimento
    @EntityGraph(attributePaths = {"agendamento", "agendamento.servico", "agendamento.profissional"})
    List<Avaliacao> findTop20ByAgendamentoBarbeariaIdOrderByCriadoEmDesc(UUID barbeariaId);

    long countByAgendamentoBarbeariaId(UUID barbeariaId);

    // média calculada pelo banco, com todas as avaliações (sem avaliações, volta null)
    @Query("select avg(a.nota) from Avaliacao a where a.agendamento.barbearia.id = :barbeariaId")
    Double mediaDaBarbearia(@Param("barbeariaId") UUID barbeariaId);
}
