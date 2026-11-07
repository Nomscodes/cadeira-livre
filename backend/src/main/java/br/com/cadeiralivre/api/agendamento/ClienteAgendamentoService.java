package br.com.cadeiralivre.api.agendamento;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.dto.MeuAgendamentoResponse;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;

/**
 * Área do cliente: histórico dos agendamentos dele e cancelamento.
 * O cliente vem do JWT; ele nunca enxerga agendamentos de outra pessoa.
 */
@Service
@RequiredArgsConstructor
public class ClienteAgendamentoService {

    // o cliente pode cancelar até 1 hora antes do horário marcado
    private static final int HORAS_DE_ANTECEDENCIA_PARA_CANCELAR = 1;

    private final AgendamentoRepository agendamentoRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MeuAgendamentoResponse> listarMeus() {
        return agendamentoRepository.findAllByClienteIdOrderByDataDescHoraInicioDesc(clienteLogado())
                .stream().map(MeuAgendamentoResponse::de).toList();
    }

    /** Cancelar um agendamento aprovado libera o horário: a disponibilidade só considera os APROVADOS. */
    @Transactional
    public MeuAgendamentoResponse cancelar(UUID id) {
        Agendamento a = agendamentoRepository.findByIdAndClienteId(id, clienteLogado())
                .orElseThrow(() -> ApiException.naoEncontrado("Agendamento não encontrado."));

        if (a.getStatus() != StatusAgendamento.PENDENTE && a.getStatus() != StatusAgendamento.APROVADO) {
            throw ApiException.conflito("Só agendamentos pendentes ou aprovados podem ser cancelados.");
        }
        LocalDateTime limiteParaCancelar = LocalDateTime.of(a.getData(), a.getHoraInicio())
                .minusHours(HORAS_DE_ANTECEDENCIA_PARA_CANCELAR);
        if (LocalDateTime.now(clock).isAfter(limiteParaCancelar)) {
            throw ApiException.invalido("O cancelamento só pode ser feito até 1 hora antes do horário.");
        }

        a.setStatus(StatusAgendamento.CANCELADO);
        return MeuAgendamentoResponse.de(a);
    }

    private UUID clienteLogado() {
        UUID clienteId = UsuarioLogado.clienteId();
        if (clienteId == null) throw ApiException.naoAutorizado("Faça login para continuar.");
        return clienteId;
    }
}
