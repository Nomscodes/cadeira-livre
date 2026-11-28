package br.com.cadeiralivre.api.agenda;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agenda.dto.AgendaResponse;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.BloqueioHorario;
import br.com.cadeiralivre.api.horario.BloqueioHorarioRepository;
import br.com.cadeiralivre.api.horario.HorarioFuncionamentoRepository;
import br.com.cadeiralivre.api.horario.dto.HorariosDoPainelResponse;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalHorarioRepository;
import lombok.RequiredArgsConstructor;

/**
 * Calendário da tela "Minha agenda": o mês do funcionário e os descansos que ele marca.
 * O descanso é guardado como bloqueio por horário do profissional, então a busca de horários do cliente
 * já deixa de oferecer aquela hora (RF20), sem tabela nova.
 */
@Service
@RequiredArgsConstructor
public class CalendarioDaAgendaService {

    static final String MOTIVO_DESCANSO = "Descanso";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AgendaService agendaService;
    private final AgendamentoRepository agendamentoRepository;
    private final BloqueioHorarioRepository bloqueioRepository;
    private final ProfissionalHorarioRepository profissionalHorarioRepository;
    private final HorarioFuncionamentoRepository horarioFuncionamentoRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AgendaResponse.Mes mes(YearMonth mes) {
        Profissional p = agendaService.profissionalLogado();
        Barbearia b = p.getBarbearia();

        // no calendário entram os atendimentos que ainda ocupam a hora (cancelado e recusado não)
        List<AgendaResponse.Item> agendamentos = agendamentoRepository.doProfissional(p.getId()).stream()
                .filter(a -> YearMonth.from(a.getData()).equals(mes))
                .filter(a -> a.getStatus() != StatusAgendamento.CANCELADO && a.getStatus() != StatusAgendamento.RECUSADO)
                .map(a -> AgendaResponse.Item.de(a, p.getId()))
                .toList();

        List<BloqueioHorario> doMes = bloqueioRepository.findAllByBarbeariaIdAndDataBetweenOrderByData(
                b.getId(), mes.atDay(1), mes.atEndOfMonth());
        List<AgendaResponse.Descanso> descansos = doMes.stream()
                .filter(x -> doProfissional(x, p) && x.getHoraInicio() != null)
                .map(x -> new AgendaResponse.Descanso(x.getData(), x.getHoraInicio().format(HORA)))
                .toList();
        List<AgendaResponse.DiaFechado> fechados = doMes.stream()
                .filter(x -> x.getProfissional() == null && x.getHoraInicio() == null)
                .map(x -> new AgendaResponse.DiaFechado(x.getData(), x.getMotivo()))
                .toList();
        List<AgendaResponse.DiaFechado> folgas = doMes.stream()
                .filter(x -> doProfissional(x, p) && x.getHoraInicio() == null)
                .map(x -> new AgendaResponse.DiaFechado(x.getData(), x.getMotivo()))
                .toList();

        AgendaResponse.ProfissionalDoMes profissional = new AgendaResponse.ProfissionalDoMes(p.getId(),
                p.getNomeSocial() != null ? p.getNomeSocial() : p.getNome(),
                HorariosDoPainelResponse.semanaDoProfissional(profissionalHorarioRepository.findAllByProfissionalId(p.getId())));
        return new AgendaResponse.Mes(profissional, b.isFuncionariosEditamHorario(), agendamentos, descansos,
                fechados, folgas,
                HorariosDoPainelResponse.semanaDoSalao(horarioFuncionamentoRepository.findAllByBarbeariaId(b.getId())));
    }

    /** Marca (descanso = true) ou desmarca uma hora de descanso. Só se o responsável liberou em Configurações. */
    @Transactional
    public void marcarDescanso(LocalDate data, String hora, boolean descanso) {
        Profissional p = agendaService.profissionalLogado();
        if (!p.getBarbearia().isFuncionariosEditamHorario()) {
            throw ApiException.proibido("O responsável não liberou a edição de horários.");
        }
        if (data == null) throw ApiException.invalido("Informe o dia do descanso.");
        if (data.isBefore(LocalDate.now(clock))) {
            throw ApiException.invalido("Não é possível mudar um dia que já passou.");
        }
        LocalTime inicio = hora(hora);

        List<BloqueioHorario> doHorario = bloqueioRepository.findAllByBarbeariaIdAndData(p.getBarbearia().getId(), data)
                .stream()
                .filter(x -> doProfissional(x, p) && inicio.equals(x.getHoraInicio()))
                .toList();
        if (!descanso) {
            bloqueioRepository.deleteAll(doHorario);
            return;
        }
        if (!doHorario.isEmpty()) return; // já estava marcado
        BloqueioHorario novo = new BloqueioHorario();
        novo.setBarbearia(p.getBarbearia());
        novo.setProfissional(p);
        novo.setData(data);
        novo.setHoraInicio(inicio);
        // a agenda anda de hora em hora; o descanso dura o que o administrador escolheu (D8) e o resto da hora fica livre
        novo.setHoraFim(inicio.plusMinutes(p.getBarbearia().getDescansoMinutos()));
        novo.setMotivo(MOTIVO_DESCANSO);
        bloqueioRepository.save(novo);
    }

    private boolean doProfissional(BloqueioHorario b, Profissional p) {
        UUID id = b.getProfissional() == null ? null : b.getProfissional().getId();
        return p.getId().equals(id);
    }

    private LocalTime hora(String hora) {
        try {
            return LocalTime.parse(hora == null ? "" : hora, HORA);
        } catch (DateTimeParseException e) {
            throw ApiException.invalido("Informe a hora no formato HH:mm.");
        }
    }
}
