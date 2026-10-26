package br.com.cadeiralivre.api.agendamento;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.dto.AgendamentoRequest;
import br.com.cadeiralivre.api.agendamento.dto.AgendamentoResponse;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/**
 * Coração do fluxo: o cliente SOLICITA (PENDENTE) e o dono APROVA ou RECUSA.
 * RF21: só pode existir um agendamento APROVADO por horário.
 * Parte 2: validar horário de funcionamento/bloqueios e travar a concorrência no banco.
 */
@Service
@RequiredArgsConstructor
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final BarbeariaRepository barbeariaRepository;
    private final ServicoRepository servicoRepository;
    private final Clock clock;

    /** Rota pública: a barbearia vem do slug da URL. */
    @Transactional
    public AgendamentoResponse solicitar(String slug, AgendamentoRequest req) {
        Barbearia barbearia = barbeariaRepository.findBySlug(slug)
                .filter(Barbearia::isPublico)
                .orElseThrow(() -> ApiException.naoEncontrado("Salão não encontrado."));

        Servico servico = servicoRepository.findByIdAndBarbeariaId(req.servicoId(), barbearia.getId())
                .filter(Servico::isAtivo)
                .orElseThrow(() -> ApiException.invalido("Serviço inválido para este salão."));

        LocalTime fim = req.hora().plusMinutes(servico.getDuracaoMinutos());
        if (!fim.isAfter(req.hora())) throw ApiException.invalido("O atendimento não pode atravessar a meia-noite.");
        if (LocalDateTime.of(req.data(), req.hora()).isBefore(LocalDateTime.now(clock))) {
            throw ApiException.invalido("Escolha um horário futuro.");
        }
        if (agendamentoRepository.existeConflito(barbearia.getId(), req.data(),
                StatusAgendamento.APROVADO, req.hora(), fim)) {
            throw ApiException.conflito("Esse horário já está ocupado. Escolha outro horário.");
        }

        Agendamento a = new Agendamento();
        a.setBarbearia(barbearia);
        a.setServico(servico);
        a.setClienteNome(req.clienteNome().trim());
        a.setClienteTelefone(req.clienteTelefone().trim());
        a.setData(req.data());
        a.setHoraInicio(req.hora());
        a.setHoraFim(fim);
        a.setStatus(StatusAgendamento.PENDENTE);
        return AgendamentoResponse.de(agendamentoRepository.save(a));
    }

    /** Rotas do painel: a barbearia vem do JWT (TenantContext). */
    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarDoPainel() {
        return agendamentoRepository
                .findAllByBarbeariaIdOrderByDataDescHoraInicioDesc(TenantContext.require())
                .stream().map(AgendamentoResponse::de).toList();
    }

    @Transactional
    public AgendamentoResponse aprovar(UUID id) {
        Agendamento a = buscarPendente(id);
        // reconfere o conflito no momento da aprovação (RF21)
        if (agendamentoRepository.existeConflito(a.getBarbearia().getId(), a.getData(),
                StatusAgendamento.APROVADO, a.getHoraInicio(), a.getHoraFim())) {
            throw ApiException.conflito("Já existe um agendamento aprovado nesse horário.");
        }
        a.setStatus(StatusAgendamento.APROVADO);
        return AgendamentoResponse.de(a);
    }

    @Transactional
    public AgendamentoResponse recusar(UUID id) {
        Agendamento a = buscarPendente(id);
        a.setStatus(StatusAgendamento.RECUSADO);
        return AgendamentoResponse.de(a);
    }

    private Agendamento buscarPendente(UUID id) {
        Agendamento a = agendamentoRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Agendamento não encontrado."));
        if (a.getStatus() != StatusAgendamento.PENDENTE) {
            throw ApiException.conflito("Só solicitações pendentes podem ser aprovadas ou recusadas.");
        }
        return a;
    }
}
