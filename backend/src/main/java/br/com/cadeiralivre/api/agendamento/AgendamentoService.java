package br.com.cadeiralivre.api.agendamento;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.dto.AgendamentoCriadoResponse;
import br.com.cadeiralivre.api.agendamento.dto.AgendamentoRequest;
import br.com.cadeiralivre.api.agendamento.dto.AgendamentoResponse;
import br.com.cadeiralivre.api.auditoria.AcaoAuditoria;
import br.com.cadeiralivre.api.auditoria.AuditoriaService;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.NotificacaoService;
import br.com.cadeiralivre.api.notificacao.TipoNotificacao;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import br.com.cadeiralivre.api.servico.Combo;
import br.com.cadeiralivre.api.servico.ComboRepository;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;
import br.com.cadeiralivre.api.usuario.ClienteRepository;
import lombok.RequiredArgsConstructor;

/**
 * Coração do fluxo: o cliente SOLICITA (PENDENTE) com um profissional e o dono APROVA ou RECUSA.
 * Depois do atendimento, o dono marca o agendamento como CONCLUÍDO.
 * RF21: cada profissional só pode ter um agendamento APROVADO por horário.
 * RNF09: aprovar, recusar e concluir ficam registrados na auditoria.
 * RF03: cada passo gera uma notificação para o dono ou para o cliente.
 */
@Service
@RequiredArgsConstructor
public class AgendamentoService {

    private final AgendamentoRepository agendamentoRepository;
    private final BarbeariaRepository barbeariaRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ComboRepository comboRepository;
    private final DisponibilidadeService disponibilidadeService;
    private final AuditoriaService auditoriaService;
    private final ClienteRepository clienteRepository;
    private final NotificacaoService notificacaoService;
    private final Clock clock;

    /** Rota pública: a barbearia vem do slug da URL. Se o cliente estiver logado, o agendamento entra no histórico dele. */
    @Transactional
    public AgendamentoCriadoResponse solicitar(String slug, AgendamentoRequest req) {
        Barbearia barbearia = barbeariaRepository.findBySlug(slug)
                .filter(Barbearia::isPublico)
                .orElseThrow(() -> ApiException.naoEncontrado("Salão não encontrado."));
        String whatsapp = req.clienteWhats().trim();
        if (whatsapp.replaceAll("\\D", "").length() < 10) {
            throw ApiException.invalido("Informe seu nome e um WhatsApp válido, com DDD.");
        }

        Agendamento a = new Agendamento();
        a.setBarbearia(barbearia);
        for (int ordem = 0; ordem < req.itens().size(); ordem++) {
            a.adicionarItem(item(barbearia, req.itens().get(ordem), ordem));
        }

        // a duração é a soma dos itens; os profissionais são todos os dos itens
        LocalTime fim = req.hora().plusMinutes(a.duracaoMinutos());
        if (!fim.isAfter(req.hora())) throw ApiException.invalido("O atendimento não pode atravessar a meia-noite.");
        if (LocalDateTime.of(req.data(), req.hora()).isBefore(LocalDateTime.now(clock))) {
            throw ApiException.invalido("Escolha um horário futuro.");
        }
        List<Profissional> profissionais = a.getItens().stream().map(AgendamentoItem::getProfissional).distinct().toList();
        // mesmas regras do calendário (RF13): funcionamento, bloqueios, folgas, almoço e agendamentos aprovados
        if (!disponibilidadeService.estaLivre(barbearia, profissionais, req.data(), req.hora(), a.duracaoMinutos())) {
            throw ApiException.conflito("Esse horário acabou de ser ocupado. Escolha outro horário.");
        }

        // o primeiro item é o "principal": a agenda, as notificações e o RF21 usam o serviço e o profissional dele
        AgendamentoItem principal = a.getItens().get(0);
        a.setServico(principal.getServico() != null ? principal.getServico() : primeiroServico(principal.getCombo()));
        a.setProfissional(principal.getProfissional());
        a.setValor(a.somaDosItens());
        a.setClienteNome(req.clienteNome().trim());
        a.setClienteTelefone(whatsapp);
        a.setData(req.data());
        a.setHoraInicio(req.hora());
        a.setHoraFim(fim);
        a.setStatus(StatusAgendamento.PENDENTE);

        // sem login o agendamento continua funcionando, só não aparece em "meus agendamentos"
        UUID clienteId = UsuarioLogado.clienteId();
        if (clienteId != null) {
            clienteRepository.findById(clienteId).ifPresent(a::setCliente);
        }
        Agendamento salvo = agendamentoRepository.save(a);
        notificacaoService.paraDono(salvo, TipoNotificacao.NOVO_AGENDAMENTO);
        return AgendamentoCriadoResponse.de(salvo);
    }

    // cada item é um serviço OU um combo do salão, com um profissional que atende e faz aquilo
    private AgendamentoItem item(Barbearia barbearia, AgendamentoRequest.Item pedido, int ordem) {
        Profissional profissional = profissionalRepository.findByIdAndBarbeariaId(pedido.profissionalId(), barbearia.getId())
                .filter(p -> p.isAtivo() && p.isAtende())
                .orElseThrow(() -> ApiException.invalido("Este profissional não atende clientes."));
        boolean temServico = pedido.servicoId() != null;
        boolean temCombo = pedido.comboId() != null;
        if (temServico == temCombo) throw ApiException.invalido("Cada item precisa de um serviço ou de um combo.");

        if (temServico) {
            Servico servico = servicoRepository.findByIdAndBarbeariaId(pedido.servicoId(), barbearia.getId())
                    .filter(Servico::isAtivo)
                    .orElseThrow(() -> ApiException.invalido("Serviço inválido para este salão."));
            if (!fazOServico(profissional, servico)) {
                throw ApiException.invalido("Profissional inválido para o serviço escolhido.");
            }
            return AgendamentoItem.doServico(servico, profissional, ordem);
        }
        Combo combo = comboRepository.findByIdAndBarbeariaIdAndAtivoTrue(pedido.comboId(), barbearia.getId())
                .orElseThrow(() -> ApiException.invalido("Combo inválido para este salão."));
        if (combo.getProfissionais().stream().noneMatch(p -> p.getId().equals(profissional.getId()))) {
            throw ApiException.invalido("Profissional inválido para o combo escolhido.");
        }
        return AgendamentoItem.doCombo(combo, profissional, ordem);
    }

    // o serviço "principal" de um combo é o primeiro em ordem de nome
    private Servico primeiroServico(Combo combo) {
        return combo.getServicos().stream().min(Comparator.comparing(Servico::getNome))
                .orElseThrow(() -> ApiException.invalido("Combo sem serviços."));
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
        // todos os profissionais dos itens, sempre na mesma ordem (por id) para duas aprovações não travarem uma à outra
        List<UUID> profissionais = a.getItens().stream().map(i -> i.getProfissional().getId())
                .distinct().sorted().toList();
        for (UUID profissionalId : profissionais) {
            // trava a agenda do profissional até a aprovação terminar: se duas aprovações do mesmo horário
            // chegarem juntas, a segunda espera a primeira acabar e aí já enxerga o conflito
            profissionalRepository.buscarComTrava(profissionalId);
            // reconfere o conflito no momento da aprovação (RF21), na agenda de cada profissional
            if (agendamentoRepository.existeConflito(profissionalId, a.getData(),
                    StatusAgendamento.APROVADO, a.getHoraInicio(), a.getHoraFim())) {
                throw ApiException.conflito("O profissional já tem um agendamento aprovado nesse horário.");
            }
        }
        a.setStatus(StatusAgendamento.APROVADO);
        auditoriaService.registrar(AcaoAuditoria.AGENDAMENTO_APROVADO, detalheParaAuditoria(a));
        notificacaoService.paraCliente(a, TipoNotificacao.HORARIO_APROVADO);
        return AgendamentoResponse.de(a);
    }

    @Transactional
    public AgendamentoResponse recusar(UUID id) {
        Agendamento a = buscarPendente(id);
        a.setStatus(StatusAgendamento.RECUSADO);
        auditoriaService.registrar(AcaoAuditoria.AGENDAMENTO_RECUSADO, detalheParaAuditoria(a));
        notificacaoService.paraCliente(a, TipoNotificacao.HORARIO_RECUSADO);
        return AgendamentoResponse.de(a);
    }

    /** O dono marca que o atendimento aconteceu. Só depois disso o cliente pode avaliar. */
    @Transactional
    public AgendamentoResponse concluir(UUID id) {
        Agendamento a = buscarDaBarbearia(id);
        if (a.getStatus() != StatusAgendamento.APROVADO) {
            throw ApiException.conflito("Só agendamentos aprovados podem ser concluídos.");
        }
        // não dá para concluir um atendimento que ainda nem começou
        if (LocalDateTime.now(clock).isBefore(LocalDateTime.of(a.getData(), a.getHoraInicio()))) {
            throw ApiException.invalido("O atendimento só pode ser concluído depois que o horário começar.");
        }
        a.setStatus(StatusAgendamento.CONCLUIDO);
        auditoriaService.registrar(AcaoAuditoria.AGENDAMENTO_CONCLUIDO, detalheParaAuditoria(a));
        notificacaoService.paraCliente(a, TipoNotificacao.ATENDIMENTO_CONCLUIDO);
        return AgendamentoResponse.de(a);
    }

    // o dono só enxerga os agendamentos da própria barbearia (RNF05)
    private Agendamento buscarDaBarbearia(UUID id) {
        return agendamentoRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Agendamento não encontrado."));
    }

    private Agendamento buscarPendente(UUID id) {
        Agendamento a = buscarDaBarbearia(id);
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
