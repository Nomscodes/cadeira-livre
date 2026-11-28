package br.com.cadeiralivre.api.agenda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.Notificacao;
import br.com.cadeiralivre.api.notificacao.NotificacaoRepository;
import br.com.cadeiralivre.api.notificacao.TipoNotificacao;
import br.com.cadeiralivre.api.profissional.Profissional;

@ExtendWith(MockitoExtension.class)
class AlertaDoFuncionarioServiceTest {

    @Mock
    private AgendaService agendaService;
    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private NotificacaoRepository notificacaoRepository;

    private AlertaDoFuncionarioService service;

    private final Instant agora = Instant.parse("2026-11-14T13:00:00Z");

    private Barbearia barbearia;
    private Profissional naoki;
    private Agendamento agendamento;

    @BeforeEach
    void preparar() {
        service = new AlertaDoFuncionarioService(agendaService, agendamentoRepository, notificacaoRepository,
                Clock.fixed(agora, ZoneId.of("America/Sao_Paulo")));

        barbearia = new Barbearia();
        barbearia.setId(UUID.randomUUID());
        naoki = new Profissional();
        naoki.setId(UUID.randomUUID());
        naoki.setNome("Naoki Barbeiro");
        naoki.setBarbearia(barbearia);
        when(agendaService.profissionalLogado()).thenReturn(naoki);

        agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setBarbearia(barbearia);
        agendamento.setClienteNome("Gabriel Naoki");
        agendamento.setData(LocalDate.of(2026, 11, 19));
        agendamento.setHoraInicio(LocalTime.of(15, 0));
        agendamento.setHoraFim(LocalTime.of(15, 30));
        agendamento.setStatus(StatusAgendamento.APROVADO);
    }

    @Test
    void alertaViraNotificacaoParaODonoComQuemMandou() {
        agendamento.adicionarItem(item(naoki));
        when(agendamentoRepository.findByIdAndBarbeariaId(agendamento.getId(), barbearia.getId()))
                .thenReturn(Optional.of(agendamento));

        service.alertar(agendamento.getId(), "Cliente agressivo", " Gritou na última visita. ");

        ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(salva.capture());
        Notificacao n = salva.getValue();
        assertEquals(TipoNotificacao.ALERTA_FUNCIONARIO, n.getTipo());
        assertSame(barbearia, n.getBarbearia());
        assertNull(n.getProfissional()); // é para o dono
        assertEquals("Naoki Barbeiro sinalizou o cliente Gabriel Naoki: Cliente agressivo. Gritou na última visita.", n.getTexto());
        assertEquals("Naoki Barbeiro", n.getRemetente());
        assertEquals(agora, n.getCriadaEm());
    }

    @Test
    void alertaSemMotivoEhRecusado() {
        ApiException erro = assertThrows(ApiException.class, () -> service.alertar(agendamento.getId(), " ", null));

        assertEquals(422, erro.getStatus().value());
        assertEquals("Escolha o motivo do alerta.", erro.getMessage());
        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    void motivoForaDaListaEhRecusado() {
        ApiException erro = assertThrows(ApiException.class,
                () -> service.alertar(agendamento.getId(), "Qualquer motivo inventado", null));

        assertEquals(422, erro.getStatus().value());
        assertEquals("Escolha um dos motivos da lista.", erro.getMessage());
        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    void descricaoComMaisDe300CaracteresEhRecusada() {
        ApiException erro = assertThrows(ApiException.class,
                () -> service.alertar(agendamento.getId(), "Cliente agressivo", "a".repeat(301)));

        assertEquals(422, erro.getStatus().value());
        assertEquals("A descrição pode ter até 300 caracteres.", erro.getMessage());
        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    void textoComNomesLongosCabeNaColunaDe500() {
        // nome do profissional e do cliente no tamanho máximo (120) e descrição no limite (300)
        naoki.setNome("N".repeat(120));
        agendamento.setClienteNome("G".repeat(120));
        agendamento.adicionarItem(item(naoki));
        when(agendamentoRepository.findByIdAndBarbeariaId(agendamento.getId(), barbearia.getId()))
                .thenReturn(Optional.of(agendamento));

        service.alertar(agendamento.getId(), "Não me sinto seguro(a) com esse cliente", "d".repeat(300));

        ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(salva.capture());
        String texto = salva.getValue().getTexto();
        assertEquals(500, texto.length());
        assertTrue(texto.endsWith("..."));
    }

    @Test
    void naoAlertaSobreAtendimentoDeOutroProfissional() {
        Profissional outro = new Profissional();
        outro.setId(UUID.randomUUID());
        agendamento.adicionarItem(item(outro));
        when(agendamentoRepository.findByIdAndBarbeariaId(agendamento.getId(), barbearia.getId()))
                .thenReturn(Optional.of(agendamento));

        ApiException erro = assertThrows(ApiException.class,
                () -> service.alertar(agendamento.getId(), "Cliente agressivo", null));

        assertEquals(404, erro.getStatus().value());
        verify(notificacaoRepository, never()).save(any());
    }

    private AgendamentoItem item(Profissional p) {
        AgendamentoItem i = new AgendamentoItem();
        i.setNome("Corte");
        i.setProfissional(p);
        i.setPreco(new BigDecimal("40.00"));
        i.setDuracaoMinutos(30);
        return i;
    }
}
