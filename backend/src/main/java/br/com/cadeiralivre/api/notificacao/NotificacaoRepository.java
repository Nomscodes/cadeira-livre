package br.com.cadeiralivre.api.notificacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    // as 30 mais recentes, já com os dados do agendamento usados no texto
    @EntityGraph(attributePaths = {"agendamento", "agendamento.servico", "agendamento.profissional", "agendamento.barbearia"})
    List<Notificacao> findTop30ByBarbeariaIdOrderByCriadaEmDesc(UUID barbeariaId);

    @EntityGraph(attributePaths = {"agendamento", "agendamento.servico", "agendamento.profissional", "agendamento.barbearia"})
    List<Notificacao> findTop30ByClienteIdOrderByCriadaEmDesc(UUID clienteId);

    // cada um só mexe nas notificações que são dele
    Optional<Notificacao> findByIdAndBarbeariaId(UUID id, UUID barbeariaId);

    Optional<Notificacao> findByIdAndClienteId(UUID id, UUID clienteId);

    // para não criar o mesmo lembrete duas vezes
    boolean existsByAgendamentoIdAndTipo(UUID agendamentoId, TipoNotificacao tipo);
}
