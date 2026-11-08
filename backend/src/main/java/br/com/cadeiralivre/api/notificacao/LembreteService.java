package br.com.cadeiralivre.api.notificacao;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import lombok.RequiredArgsConstructor;

/**
 * Tarefa automática dos lembretes (RF03): roda a cada 5 minutos e olha os agendamentos aprovados.
 * O dono é lembrado 30 minutos antes; o cliente, na véspera. Cada lembrete é criado uma vez só.
 */
@Service
@RequiredArgsConstructor
public class LembreteService {

    private static final Logger log = LoggerFactory.getLogger(LembreteService.class);

    static final int MINUTOS_ANTES_PARA_O_DONO = 30;

    private final AgendamentoRepository agendamentoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final NotificacaoService notificacaoService;
    private final Clock clock;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void gerarLembretes() {
        LocalDateTime agora = LocalDateTime.now(clock);
        int paraDonos = lembrarDonos(agora);
        int paraClientes = lembrarClientes(agora.toLocalDate().plusDays(1));
        if (paraDonos + paraClientes > 0) {
            log.info("Lembretes criados: {} para donos e {} para clientes.", paraDonos, paraClientes);
        }
    }

    // dono: atendimentos aprovados de hoje que começam nos próximos 30 minutos
    private int lembrarDonos(LocalDateTime agora) {
        LocalTime de = agora.toLocalTime();
        LocalTime ate = de.plusMinutes(MINUTOS_ANTES_PARA_O_DONO);
        if (ate.isBefore(de)) {
            ate = LocalTime.MAX; // perto da meia-noite: não passa para o dia seguinte
        }
        int criados = 0;
        for (Agendamento a : agendamentoRepository.findAllByStatusAndDataAndHoraInicioBetween(
                StatusAgendamento.APROVADO, agora.toLocalDate(), de, ate)) {
            if (!notificacaoRepository.existsByAgendamentoIdAndTipo(a.getId(), TipoNotificacao.LEMBRETE_DONO)) {
                notificacaoService.paraDono(a, TipoNotificacao.LEMBRETE_DONO);
                criados++;
            }
        }
        return criados;
    }

    // cliente: na véspera, atendimentos aprovados de amanhã de quem agendou com conta
    private int lembrarClientes(LocalDate amanha) {
        int criados = 0;
        for (Agendamento a : agendamentoRepository.findAllByStatusAndDataAndClienteIsNotNull(
                StatusAgendamento.APROVADO, amanha)) {
            if (!notificacaoRepository.existsByAgendamentoIdAndTipo(a.getId(), TipoNotificacao.LEMBRETE_CLIENTE)) {
                notificacaoService.paraCliente(a, TipoNotificacao.LEMBRETE_CLIENTE);
                criados++;
            }
        }
        return criados;
    }
}
