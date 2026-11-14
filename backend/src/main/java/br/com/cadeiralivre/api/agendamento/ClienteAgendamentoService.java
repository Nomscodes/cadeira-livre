package br.com.cadeiralivre.api.agendamento;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.dto.MeuAgendamentoResponse;
import br.com.cadeiralivre.api.avaliacao.AvaliacaoRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.NotificacaoService;
import br.com.cadeiralivre.api.notificacao.TipoNotificacao;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;

/**
 * Área do cliente: histórico dos agendamentos dele, detalhe e cancelamento.
 * O cliente vem do JWT; ele nunca enxerga agendamentos de outra pessoa.
 */
@Service
@RequiredArgsConstructor
public class ClienteAgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final NotificacaoService notificacaoService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MeuAgendamentoResponse> listarMeus() {
        LocalDateTime agora = LocalDateTime.now(clock);
        return agendamentoRepository.findAllByClienteIdOrderByDataDescHoraInicioDesc(clienteLogado())
                .stream().map(a -> resposta(a, agora)).toList();
    }

    @Transactional(readOnly = true)
    public MeuAgendamentoResponse detalhe(UUID id) {
        return resposta(buscarDoCliente(id), LocalDateTime.now(clock));
    }

    /**
     * Cancelar libera o horário (a disponibilidade só considera os APROVADOS). Vale para pendente ou aprovado,
     * até o prazo de cancelamento do salão (em horas antes do horário). O dono recebe uma notificação.
     */
    @Transactional
    public MeuAgendamentoResponse cancelar(UUID id) {
        Agendamento a = buscarDoCliente(id);
        LocalDateTime agora = LocalDateTime.now(clock);
        if (a.getStatus() != StatusAgendamento.PENDENTE && a.getStatus() != StatusAgendamento.APROVADO) {
            throw ApiException.conflito("Só agendamentos pendentes ou aprovados podem ser cancelados.");
        }
        if (!MeuAgendamentoResponse.podeCancelar(a, agora)) {
            throw ApiException.invalido("O prazo para cancelar já passou. Fale com o salão pelo WhatsApp.");
        }
        a.setStatus(StatusAgendamento.CANCELADO);
        notificacaoService.paraDono(a, TipoNotificacao.CANCELAMENTO);
        return resposta(a, agora);
    }

    private Agendamento buscarDoCliente(UUID id) {
        return agendamentoRepository.findByIdAndClienteId(id, clienteLogado())
                .orElseThrow(() -> ApiException.naoEncontrado("Agendamento não encontrado."));
    }

    private MeuAgendamentoResponse resposta(Agendamento a, LocalDateTime agora) {
        return MeuAgendamentoResponse.de(a, avaliacaoRepository.existsByAgendamentoId(a.getId()), agora);
    }

    private UUID clienteLogado() {
        UUID clienteId = UsuarioLogado.clienteId();
        if (clienteId == null) throw ApiException.naoAutorizado("Faça login para continuar.");
        return clienteId;
    }
}
