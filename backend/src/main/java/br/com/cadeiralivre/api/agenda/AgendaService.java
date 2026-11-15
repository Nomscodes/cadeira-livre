package br.com.cadeiralivre.api.agenda;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agenda.dto.AgendaResponse;
import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.dto.HorariosDoPainelResponse;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalHorarioRepository;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import br.com.cadeiralivre.api.servico.Servico;
import lombok.RequiredArgsConstructor;

/**
 * Tela "Minha agenda" do funcionário: a lista dos agendamentos dele e o resumo do topo.
 * O profissional vem sempre da conta logada (nunca da requisição).
 */
@Service
@RequiredArgsConstructor
public class AgendaService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ProfissionalRepository profissionalRepository;
    private final ProfissionalHorarioRepository horarioRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final Clock clock;

    /** Todos os agendamentos em que o profissional aparece; o front separa em próximos, realizados e cancelados. */
    @Transactional(readOnly = true)
    public List<AgendaResponse.Item> agendamentos() {
        Profissional p = profissionalLogado();
        return agendamentoRepository.doProfissional(p.getId()).stream()
                .map(a -> AgendaResponse.Item.de(a, p.getId()))
                .toList();
    }

    /** Perfil, salão e indicadores: hoje, próximos 7 dias, concluídos no mês e o próximo atendimento. */
    @Transactional(readOnly = true)
    public AgendaResponse.Resumo resumo() {
        Profissional p = profissionalLogado();
        Barbearia b = p.getBarbearia();
        List<Agendamento> meus = agendamentoRepository.doProfissional(p.getId());
        LocalDate hoje = LocalDate.now(clock);
        LocalDateTime agora = LocalDateTime.now(clock);

        // os próximos: aprovados que ainda não começaram (a lista já vem em ordem de data e hora)
        List<Agendamento> futuros = meus.stream()
                .filter(a -> a.getStatus() == StatusAgendamento.APROVADO)
                .filter(a -> !LocalDateTime.of(a.getData(), a.getHoraInicio()).isBefore(agora))
                .toList();
        long deHoje = meus.stream()
                .filter(a -> a.getData().equals(hoje))
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO && a.getStatus() != StatusAgendamento.RECUSADO)
                .count();
        long proximos7 = futuros.stream().filter(a -> !a.getData().isAfter(hoje.plusDays(7))).count();
        long concluidosMes = meus.stream()
                .filter(a -> a.getStatus() == StatusAgendamento.CONCLUIDO)
                .filter(a -> YearMonth.from(a.getData()).equals(YearMonth.from(hoje)))
                .count();
        AgendaResponse.Proximo proximo = futuros.isEmpty() ? null : proximo(futuros.get(0));

        AgendaResponse.Perfil perfil = new AgendaResponse.Perfil(p.getId(), p.getNome(), p.getNomeSocial(),
                p.getTelefone(),
                HorariosDoPainelResponse.semanaDoProfissional(horarioRepository.findAllByProfissionalId(p.getId())),
                p.getServicos().stream().map(Servico::getNome).sorted().toList());
        return new AgendaResponse.Resumo(perfil, new AgendaResponse.Salao(b.getNomeFantasia(), b.getTemaId()),
                new AgendaResponse.Indicadores(deHoje, proximos7, concluidosMes, proximo));
    }

    private AgendaResponse.Proximo proximo(Agendamento a) {
        return new AgendaResponse.Proximo(a.getData(), a.getHoraInicio().format(HORA), a.getClienteNome(),
                a.getItens().stream().map(AgendamentoItem::getNome).collect(Collectors.joining(" + ")));
    }

    // a agenda é só do funcionário; o profissional é o ligado à conta logada
    private Profissional profissionalLogado() {
        if (!UsuarioLogado.ehFuncionario()) {
            throw ApiException.proibido("Esta tela é só para os profissionais da equipe.");
        }
        UUID usuarioId = UsuarioLogado.usuarioId();
        return profissionalRepository.findByUsuarioId(usuarioId)
                .filter(Profissional::isAtivo)
                .orElseThrow(() -> ApiException.proibido("Seu acesso foi removido pelo responsável do salão."));
    }
}
