package br.com.cadeiralivre.api.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.dto.NotificacaoResponse;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/**
 * Notificações dentro do sistema (RF03), sem SMS nem e-mail (fora do escopo do doc V7).
 * Os outros services chamam paraDono/paraCliente quando algo acontece com um agendamento.
 */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;
    private final Clock clock;

    /** Aviso para o dono da barbearia do agendamento. */
    @Transactional
    public void paraDono(Agendamento agendamento, TipoNotificacao tipo) {
        Notificacao n = nova(agendamento, tipo);
        n.setBarbearia(agendamento.getBarbearia());
        notificacaoRepository.save(n);
    }

    /** Aviso para o cliente. Quem agendou sem conta não recebe, porque não tem onde ver. */
    @Transactional
    public void paraCliente(Agendamento agendamento, TipoNotificacao tipo) {
        if (agendamento.getCliente() == null) return;
        Notificacao n = nova(agendamento, tipo);
        n.setCliente(agendamento.getCliente());
        notificacaoRepository.save(n);
    }

    /** Sino do painel: a barbearia vem do JWT. */
    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listarDoDono() {
        return notificacaoRepository.findTop30ByBarbeariaIdOrderByCriadaEmDesc(TenantContext.require())
                .stream().map(NotificacaoResponse::de).toList();
    }

    /** Sino da área do cliente: o cliente vem do JWT. */
    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listarDoCliente() {
        return notificacaoRepository.findTop30ByClienteIdOrderByCriadaEmDesc(clienteLogado())
                .stream().map(NotificacaoResponse::de).toList();
    }

    @Transactional
    public void marcarComoLidaDoDono(UUID id) {
        marcarComoLida(notificacaoRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Notificação não encontrada.")));
    }

    @Transactional
    public void marcarComoLidaDoCliente(UUID id) {
        marcarComoLida(notificacaoRepository.findByIdAndClienteId(id, clienteLogado())
                .orElseThrow(() -> ApiException.naoEncontrado("Notificação não encontrada.")));
    }

    // RF04: guarda quando o aviso foi visto; abrir de novo não muda a data
    private void marcarComoLida(Notificacao n) {
        if (n.getLidaEm() == null) {
            n.setLidaEm(Instant.now(clock));
        }
    }

    private Notificacao nova(Agendamento agendamento, TipoNotificacao tipo) {
        Notificacao n = new Notificacao();
        n.setAgendamento(agendamento);
        n.setTipo(tipo);
        n.setCriadaEm(Instant.now(clock));
        return n;
    }

    private UUID clienteLogado() {
        UUID clienteId = UsuarioLogado.clienteId();
        if (clienteId == null) throw ApiException.naoAutorizado("Faça login para continuar.");
        return clienteId;
    }
}
