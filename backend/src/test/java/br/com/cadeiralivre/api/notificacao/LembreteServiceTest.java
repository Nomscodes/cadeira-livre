package br.com.cadeiralivre.api.notificacao;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;

@ExtendWith(MockitoExtension.class)
class LembreteServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private NotificacaoRepository notificacaoRepository;

    @Mock
    private NotificacaoService notificacaoService;

    private final ZoneId brasilia = ZoneId.of("America/Sao_Paulo");
    private final LocalDate hoje = LocalDate.of(2026, 11, 10);
    private final LocalDate amanha = LocalDate.of(2026, 11, 11);

    private LembreteService lembreteService;
    private Agendamento agendamento;

    @BeforeEach
    void preparar() {
        // "agora": 10/11/2026 às 09:00 no horário de Brasília
        lembreteService = criarComRelogio(Instant.parse("2026-11-10T12:00:00Z"));

        agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setStatus(StatusAgendamento.APROVADO);
    }

    @Test
    void lembraODonoDosAtendimentosQueComecamNosProximos30Minutos() {
        when(agendamentoRepository.findAllByStatusAndDataAndHoraInicioBetween(
                StatusAgendamento.APROVADO, hoje, LocalTime.of(9, 0), LocalTime.of(9, 30)))
                .thenReturn(List.of(agendamento));

        lembreteService.gerarLembretes();

        verify(notificacaoService).paraDono(agendamento, TipoNotificacao.LEMBRETE_DONO);
    }

    @Test
    void naoRepeteOLembreteDoDono() {
        // a tarefa roda a cada 5 minutos: o mesmo atendimento aparece em várias rodadas
        when(agendamentoRepository.findAllByStatusAndDataAndHoraInicioBetween(
                StatusAgendamento.APROVADO, hoje, LocalTime.of(9, 0), LocalTime.of(9, 30)))
                .thenReturn(List.of(agendamento));
        when(notificacaoRepository.existsByAgendamentoIdAndTipo(agendamento.getId(), TipoNotificacao.LEMBRETE_DONO))
                .thenReturn(true);

        lembreteService.gerarLembretes();

        verify(notificacaoService, never()).paraDono(any(), any());
    }

    @Test
    void lembraOClienteNaVesperaDoHorario() {
        when(agendamentoRepository.findAllByStatusAndDataAndClienteIsNotNull(StatusAgendamento.APROVADO, amanha))
                .thenReturn(List.of(agendamento));

        lembreteService.gerarLembretes();

        verify(notificacaoService).paraCliente(agendamento, TipoNotificacao.LEMBRETE_CLIENTE);
    }

    @Test
    void naoRepeteOLembreteDoCliente() {
        when(agendamentoRepository.findAllByStatusAndDataAndClienteIsNotNull(StatusAgendamento.APROVADO, amanha))
                .thenReturn(List.of(agendamento));
        when(notificacaoRepository.existsByAgendamentoIdAndTipo(agendamento.getId(), TipoNotificacao.LEMBRETE_CLIENTE))
                .thenReturn(true);

        lembreteService.gerarLembretes();

        verify(notificacaoService, never()).paraCliente(any(), any());
    }

    @Test
    void pertoDaMeiaNoiteOLembreteDoDonoNaoPassaParaODiaSeguinte() {
        // 10/11 às 23:50: a janela vai até o fim do dia, e não até 00:20 de amanhã
        lembreteService = criarComRelogio(Instant.parse("2026-11-11T02:50:00Z"));

        lembreteService.gerarLembretes();

        verify(agendamentoRepository).findAllByStatusAndDataAndHoraInicioBetween(
                StatusAgendamento.APROVADO, hoje, LocalTime.of(23, 50), LocalTime.MAX);
    }

    private LembreteService criarComRelogio(Instant agora) {
        return new LembreteService(agendamentoRepository, notificacaoRepository, notificacaoService,
                Clock.fixed(agora, brasilia));
    }
}
