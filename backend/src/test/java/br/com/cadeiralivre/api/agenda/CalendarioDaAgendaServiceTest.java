package br.com.cadeiralivre.api.agenda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.agenda.dto.AgendaResponse;
import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.BloqueioHorario;
import br.com.cadeiralivre.api.horario.BloqueioHorarioRepository;
import br.com.cadeiralivre.api.horario.HorarioFuncionamentoRepository;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalHorarioRepository;

@ExtendWith(MockitoExtension.class)
class CalendarioDaAgendaServiceTest {

    @Mock
    private AgendaService agendaService;
    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private BloqueioHorarioRepository bloqueioRepository;
    @Mock
    private ProfissionalHorarioRepository profissionalHorarioRepository;
    @Mock
    private HorarioFuncionamentoRepository horarioFuncionamentoRepository;

    private CalendarioDaAgendaService service;

    // "Agora" fixo: sábado 14/11/2026 às 10:00 no horário de Brasília
    private final Instant agora = Instant.parse("2026-11-14T13:00:00Z");
    private final YearMonth novembro = YearMonth.of(2026, 11);

    private Barbearia barbearia;
    private Profissional naoki;

    @BeforeEach
    void preparar() {
        service = new CalendarioDaAgendaService(agendaService, agendamentoRepository, bloqueioRepository,
                profissionalHorarioRepository, horarioFuncionamentoRepository,
                Clock.fixed(agora, ZoneId.of("America/Sao_Paulo")));

        barbearia = new Barbearia();
        barbearia.setId(UUID.randomUUID());
        barbearia.setFuncionariosEditamHorario(true);

        naoki = new Profissional();
        naoki.setId(UUID.randomUUID());
        naoki.setNome("Naoki Barbeiro");
        naoki.setNomeSocial("Naoki");
        naoki.setBarbearia(barbearia);
        when(agendaService.profissionalLogado()).thenReturn(naoki);
    }

    // ---------- calendário do mês ----------

    @Test
    void mesSeparaDescansosDiasFechadosEFolgasEDeixaDeForaOsCancelados() {
        Agendamento aprovado = agendamento(LocalDate.of(2026, 11, 19), StatusAgendamento.APROVADO);
        Agendamento cancelado = agendamento(LocalDate.of(2026, 11, 18), StatusAgendamento.CANCELADO);
        Agendamento dezembro = agendamento(LocalDate.of(2026, 12, 2), StatusAgendamento.APROVADO);
        when(agendamentoRepository.doProfissional(naoki.getId())).thenReturn(List.of(cancelado, aprovado, dezembro));

        Profissional outro = new Profissional();
        outro.setId(UUID.randomUUID());
        when(bloqueioRepository.findAllByBarbeariaIdAndDataBetweenOrderByData(
                barbearia.getId(), novembro.atDay(1), novembro.atEndOfMonth())).thenReturn(List.of(
                bloqueio(null, LocalDate.of(2026, 11, 20), null, "Consciência Negra"),
                bloqueio(naoki, LocalDate.of(2026, 11, 26), null, "Folga"),
                bloqueio(naoki, LocalDate.of(2026, 11, 17), LocalTime.of(15, 0), "Descanso"),
                bloqueio(outro, LocalDate.of(2026, 11, 17), LocalTime.of(10, 0), "Descanso")));

        AgendaResponse.Mes mes = service.mes(novembro);

        assertEquals("Naoki", mes.profissional().nome());
        assertTrue(mes.podeEditar());
        assertEquals(1, mes.agendamentos().size());
        assertEquals(LocalDate.of(2026, 11, 19), mes.agendamentos().get(0).data());
        assertEquals(List.of(new AgendaResponse.Descanso(LocalDate.of(2026, 11, 17), "15:00")), mes.descansos());
        assertEquals(List.of(new AgendaResponse.DiaFechado(LocalDate.of(2026, 11, 20), "Consciência Negra")), mes.bloqueios());
        assertEquals(List.of(new AgendaResponse.DiaFechado(LocalDate.of(2026, 11, 26), "Folga")), mes.folgas());
        assertEquals(7, mes.salaoHorarios().size());
    }

    // ---------- descansos ----------

    @Test
    void marcarDescansoCriaBloqueioDeUmaHoraDoProfissional() {
        LocalDate dia = LocalDate.of(2026, 11, 17);
        when(bloqueioRepository.findAllByBarbeariaIdAndData(barbearia.getId(), dia)).thenReturn(List.of());

        service.marcarDescanso(dia, "15:00", true);

        ArgumentCaptor<BloqueioHorario> salvo = ArgumentCaptor.forClass(BloqueioHorario.class);
        verify(bloqueioRepository).save(salvo.capture());
        assertSame(naoki, salvo.getValue().getProfissional());
        assertEquals(LocalTime.of(15, 0), salvo.getValue().getHoraInicio());
        assertEquals(LocalTime.of(16, 0), salvo.getValue().getHoraFim());
        assertEquals("Descanso", salvo.getValue().getMotivo());
    }

    @Test
    void desmarcarDescansoApagaOBloqueioDaquelaHora() {
        LocalDate dia = LocalDate.of(2026, 11, 17);
        BloqueioHorario descanso = bloqueio(naoki, dia, LocalTime.of(15, 0), "Descanso");
        when(bloqueioRepository.findAllByBarbeariaIdAndData(barbearia.getId(), dia)).thenReturn(List.of(descanso));

        service.marcarDescanso(dia, "15:00", false);

        verify(bloqueioRepository).deleteAll(List.of(descanso));
        verify(bloqueioRepository, never()).save(any());
    }

    @Test
    void semALiberacaoDoResponsavelNaoMarcaDescanso() {
        barbearia.setFuncionariosEditamHorario(false);

        ApiException erro = assertThrows(ApiException.class,
                () -> service.marcarDescanso(LocalDate.of(2026, 11, 17), "15:00", true));

        assertEquals(403, erro.getStatus().value());
        assertEquals("O responsável não liberou a edição de horários.", erro.getMessage());
    }

    @Test
    void naoMarcaDescansoEmDiaQueJaPassouNemComHoraInvalida() {
        ApiException passado = assertThrows(ApiException.class,
                () -> service.marcarDescanso(LocalDate.of(2026, 11, 13), "15:00", true));
        ApiException horaRuim = assertThrows(ApiException.class,
                () -> service.marcarDescanso(LocalDate.of(2026, 11, 17), "15h", true));

        assertEquals(422, passado.getStatus().value());
        assertEquals("Informe a hora no formato HH:mm.", horaRuim.getMessage());
        assertFalse(passado.getMessage().isBlank());
        verify(bloqueioRepository, never()).save(any());
    }

    // ---------- métodos de apoio ----------

    private Agendamento agendamento(LocalDate data, StatusAgendamento status) {
        Agendamento a = new Agendamento();
        a.setId(UUID.randomUUID());
        a.setClienteNome("Gabriel Naoki");
        a.setData(data);
        a.setHoraInicio(LocalTime.of(15, 0));
        a.setHoraFim(LocalTime.of(15, 30));
        a.setStatus(status);
        AgendamentoItem i = new AgendamentoItem();
        i.setNome("Corte");
        i.setProfissional(naoki);
        i.setPreco(new BigDecimal("40.00"));
        i.setDuracaoMinutos(30);
        a.adicionarItem(i);
        return a;
    }

    private BloqueioHorario bloqueio(Profissional p, LocalDate data, LocalTime inicio, String motivo) {
        BloqueioHorario b = new BloqueioHorario();
        b.setBarbearia(barbearia);
        b.setProfissional(p);
        b.setData(data);
        b.setHoraInicio(inicio);
        b.setHoraFim(inicio == null ? null : inicio.plusHours(1));
        b.setMotivo(motivo);
        return b;
    }
}
