package br.com.cadeiralivre.api.agendamento;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.dto.AgendamentoRequest;
import br.com.cadeiralivre.api.agendamento.dto.AgendamentoResponse;
import br.com.cadeiralivre.api.auditoria.AcaoAuditoria;
import br.com.cadeiralivre.api.auditoria.AuditoriaService;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;
import br.com.cadeiralivre.api.usuario.ClienteRepository;
import lombok.RequiredArgsConstructor;

/**
 * Coração do fluxo: o cliente SOLICITA (PENDENTE) com um profissional e o dono APROVA ou RECUSA.
 * RF21: cada profissional só pode ter um agendamento APROVADO por horário.
 * RNF09: aprovar e recusar ficam registrados na auditoria.
 */
@Service
@RequiredArgsConstructor
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final BarbeariaRepository barbeariaRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final AuditoriaService auditoriaService;
    private final ClienteRepository clienteRepository;
    private final Clock clock;

    /** Rota pública: a barbearia vem do slug da URL. Se o cliente estiver logado, o agendamento entra no histórico dele. */
    @Transactional
    public AgendamentoResponse solicitar(String slug, AgendamentoRequest req) {
        Barbearia barbearia = barbeariaRepository.findBySlug(slug)
                .filter(Barbearia::isPublico)
                .orElseThrow(() -> ApiException.naoEncontrado("Salão não encontrado."));

        Servico servico = servicoRepository.findByIdAndBarbeariaId(req.servicoId(), barbearia.getId())
                .filter(Servico::isAtivo)
                .orElseThrow(() -> ApiException.invalido("Serviço inválido para este salão."));

        // o profissional tem que ser deste salão, estar ativo e fazer o serviço escolhido
        Profissional profissional = profissionalRepository.findByIdAndBarbeariaId(req.profissionalId(), barbearia.getId())
                .filter(Profissional::isAtivo)
                .filter(p -> fazOServico(p, servico))
                .orElseThrow(() -> ApiException.invalido("Profissional inválido para o serviço escolhido."));

        LocalTime fim = req.hora().plusMinutes(servico.getDuracaoMinutos());
        if (!fim.isAfter(req.hora())) throw ApiException.invalido("O atendimento não pode atravessar a meia-noite.");
        if (LocalDateTime.of(req.data(), req.hora()).isBefore(LocalDateTime.now(clock))) {
            throw ApiException.invalido("Escolha um horário futuro.");
        }
        // mesmas regras da disponibilidade (RF13): funcionamento, bloqueios, folgas e agendamentos aprovados
        if (!disponibilidadeService.estaLivre(barbearia, servico, profissional, req.data(), req.hora())) {
            throw ApiException.conflito("Esse horário não está disponível. Escolha outro horário.");
        }

        Agendamento a = new Agendamento();
        a.setBarbearia(barbearia);
        a.setServico(servico);
        a.setProfissional(profissional);
        a.setClienteNome(req.clienteNome().trim());
        a.setClienteTelefone(req.clienteTelefone().trim());
        a.setData(req.data());
        a.setHoraInicio(req.hora());
        a.setHoraFim(fim);
        a.setStatus(StatusAgendamento.PENDENTE);

        // sem login o agendamento continua funcionando, só não aparece em "meus agendamentos"
        UUID clienteId = UsuarioLogado.clienteId();
        if (clienteId != null) {
            clienteRepository.findById(clienteId).ifPresent(a::setCliente);
        }
        return AgendamentoResponse.de(agendamentoRepository.save(a));
    }

    /** Rotas do painel: a barbearia vem do JWT (TenantContext). Com data, mostra só a agenda daquele dia. */
    @Transactional(readOnly = true)
    public List<AgendamentoResponse> listarDoPainel(LocalDate data) {
        UUID barbeariaId = TenantContext.require();
        List<Agendamento> agendamentos = data == null
                ? agendamentoRepository.findAllByBarbeariaIdOrderByDataDescHoraInicioDesc(barbeariaId)
                : agendamentoRepository.findAllByBarbeariaIdAndDataOrderByHoraInicioAscProfissionalNomeAsc(barbeariaId, data);
        return agendamentos.stream().map(AgendamentoResponse::de).toList();
    }

    @Transactional
    public AgendamentoResponse aprovar(UUID id) {
        Agendamento a = buscarPendente(id);
        // trava a agenda do profissional até a aprovação terminar: se duas aprovações do mesmo horário
        // chegarem juntas, a segunda espera a primeira acabar e aí já enxerga o conflito
        profissionalRepository.buscarComTrava(a.getProfissional().getId());
        // reconfere o conflito no momento da aprovação (RF21), na agenda do profissional
        if (agendamentoRepository.existeConflito(a.getProfissional().getId(), a.getData(),
                StatusAgendamento.APROVADO, a.getHoraInicio(), a.getHoraFim())) {
            throw ApiException.conflito("O profissional já tem um agendamento aprovado nesse horário.");
        }
        a.setStatus(StatusAgendamento.APROVADO);
        auditoriaService.registrar(AcaoAuditoria.AGENDAMENTO_APROVADO, detalheParaAuditoria(a));
        return AgendamentoResponse.de(a);
    }

    @Transactional
    public AgendamentoResponse recusar(UUID id) {
        Agendamento a = buscarPendente(id);
        a.setStatus(StatusAgendamento.RECUSADO);
        auditoriaService.registrar(AcaoAuditoria.AGENDAMENTO_RECUSADO, detalheParaAuditoria(a));
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

    // sem nome nem telefone do cliente: o log fica guardado mesmo depois que os dados do cliente forem apagados (LGPD)
    private String detalheParaAuditoria(Agendamento a) {
        return "Agendamento " + a.getId() + " | " + a.getData() + " " + a.getHoraInicio()
                + " | " + a.getProfissional().getNome();
    }

    private boolean fazOServico(Profissional p, Servico servico) {
        return p.getServicos().stream().anyMatch(s -> s.getId().equals(servico.getId()));
    }
}
