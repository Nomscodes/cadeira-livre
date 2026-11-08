package br.com.cadeiralivre.api.notificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.dto.NotificacaoResponse;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.tenant.TenantContext;
import br.com.cadeiralivre.api.usuario.Cliente;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    @Mock
    private NotificacaoRepository notificacaoRepository;

    private NotificacaoService notificacaoService;

    // "Agora" fixo nos testes: 10/11/2026 às 09:00 no horário de Brasília
    private final Instant agora = Instant.parse("2026-11-10T12:00:00Z");
    private final Clock relogio = Clock.fixed(agora, ZoneId.of("America/Sao_Paulo"));

    private Barbearia barbearia;
    private Cliente gabriel;
    private Agendamento agendamento;

    @BeforeEach
    void preparar() {
        // o Clock não é mock, por isso o service é criado na mão
        notificacaoService = new NotificacaoService(notificacaoRepository, relogio);

        barbearia = new Barbearia();
        barbearia.setId(UUID.randomUUID());
        barbearia.setNomeFantasia("Barbearia Teste - Gabriel Naoki");

        gabriel = new Cliente();
        gabriel.setId(UUID.randomUUID());

        Servico corte = new Servico();
        corte.setNome("Corte");

        Profissional diego = new Profissional();
        diego.setNome("Diego Santos");

        // Gabriel agendou com login: Corte com o Diego em 12/11 às 14:00
        agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setBarbearia(barbearia);
        agendamento.setCliente(gabriel);
        agendamento.setServico(corte);
        agendamento.setProfissional(diego);
        agendamento.setClienteNome("Gabriel Naoki");
        agendamento.setData(LocalDate.of(2026, 11, 12));
        agendamento.setHoraInicio(LocalTime.of(14, 0));
        agendamento.setHoraFim(LocalTime.of(14, 30));
        agendamento.setStatus(StatusAgendamento.APROVADO);
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ---------- criar notificações ----------

    @Test
    void notificacaoParaODonoFicaComABarbeariaDoAgendamento() {
        notificacaoService.paraDono(agendamento, TipoNotificacao.NOVO_AGENDAMENTO);

        ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(salva.capture());
        assertSame(barbearia, salva.getValue().getBarbearia());
        assertNull(salva.getValue().getCliente());
        assertEquals(TipoNotificacao.NOVO_AGENDAMENTO, salva.getValue().getTipo());
        assertEquals(agora, salva.getValue().getCriadaEm());
        assertNull(salva.getValue().getLidaEm());
    }

    @Test
    void notificacaoParaOClienteFicaComOClienteDoAgendamento() {
        notificacaoService.paraCliente(agendamento, TipoNotificacao.HORARIO_APROVADO);

        ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(salva.capture());
        assertSame(gabriel, salva.getValue().getCliente());
        assertNull(salva.getValue().getBarbearia());
    }

    @Test
    void clienteQueAgendouSemContaNaoRecebeNotificacao() {
        agendamento.setCliente(null);

        notificacaoService.paraCliente(agendamento, TipoNotificacao.HORARIO_APROVADO);

        verify(notificacaoRepository, never()).save(any());
    }

    // ---------- listar (texto montado na hora) ----------

    @Test
    void sinoDoDonoMostraOPedidoComOsDadosDoAgendamento() {
        TenantContext.set(barbearia.getId());
        when(notificacaoRepository.findTop30ByBarbeariaIdOrderByCriadaEmDesc(barbearia.getId()))
                .thenReturn(List.of(notificacao(TipoNotificacao.NOVO_AGENDAMENTO)));

        NotificacaoResponse n = notificacaoService.listarDoDono().get(0);

        assertEquals("Novo pedido de agendamento", n.titulo());
        assertEquals("Gabriel Naoki pediu Corte com Diego Santos em 12/11 às 14:00. Aprove ou recuse no painel.", n.texto());
        assertFalse(n.lida());
    }

    @Test
    void sinoDoClienteMostraOHorarioConfirmado() {
        logarComo(gabriel.getId(), "ROLE_CLIENTE");
        when(notificacaoRepository.findTop30ByClienteIdOrderByCriadaEmDesc(gabriel.getId()))
                .thenReturn(List.of(notificacao(TipoNotificacao.HORARIO_APROVADO)));

        NotificacaoResponse n = notificacaoService.listarDoCliente().get(0);

        assertEquals("Horário confirmado", n.titulo());
        assertEquals("A barbearia confirmou seu Corte com Diego Santos em 12/11 às 14:00.", n.texto());
    }

    @Test
    void clienteQueExcluiuAContaApareceComoClienteRemovido() {
        // depois da exclusão da conta (PR16) o agendamento fica sem o nome; o texto acompanha (LGPD)
        agendamento.setClienteNome("Cliente removido");
        TenantContext.set(barbearia.getId());
        when(notificacaoRepository.findTop30ByBarbeariaIdOrderByCriadaEmDesc(barbearia.getId()))
                .thenReturn(List.of(notificacao(TipoNotificacao.CANCELAMENTO)));

        NotificacaoResponse n = notificacaoService.listarDoDono().get(0);

        assertEquals("Cliente removido cancelou Corte com Diego Santos de 12/11 às 14:00.", n.texto());
    }

    @Test
    void quemNaoEhClienteNaoVeNotificacoesDeCliente() {
        logarComo(UUID.randomUUID(), "ROLE_DONO");

        ApiException erro = assertThrows(ApiException.class, () -> notificacaoService.listarDoCliente());

        assertEquals(401, erro.getStatus().value());
        verifyNoInteractions(notificacaoRepository);
    }

    // ---------- marcar como lida (RF04) ----------

    @Test
    void marcarComoLidaGuardaQuandoFoiLida() {
        logarComo(gabriel.getId(), "ROLE_CLIENTE");
        Notificacao n = notificacao(TipoNotificacao.HORARIO_APROVADO);
        when(notificacaoRepository.findByIdAndClienteId(n.getId(), gabriel.getId())).thenReturn(Optional.of(n));

        notificacaoService.marcarComoLidaDoCliente(n.getId());

        assertEquals(agora, n.getLidaEm());
    }

    @Test
    void abrirDeNovoNaoMudaADataEmQueFoiLida() {
        TenantContext.set(barbearia.getId());
        Notificacao n = notificacao(TipoNotificacao.NOVO_AGENDAMENTO);
        Instant primeiraLeitura = Instant.parse("2026-11-09T18:00:00Z");
        n.setLidaEm(primeiraLeitura);
        when(notificacaoRepository.findByIdAndBarbeariaId(n.getId(), barbearia.getId())).thenReturn(Optional.of(n));

        notificacaoService.marcarComoLidaDoDono(n.getId());

        assertEquals(primeiraLeitura, n.getLidaEm());
    }

    @Test
    void notificacaoDeOutraBarbeariaDevolve404() {
        TenantContext.set(barbearia.getId());
        UUID deOutraBarbearia = UUID.randomUUID();
        when(notificacaoRepository.findByIdAndBarbeariaId(deOutraBarbearia, barbearia.getId())).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> notificacaoService.marcarComoLidaDoDono(deOutraBarbearia));

        assertEquals(404, erro.getStatus().value());
    }

    // ---------- métodos de apoio ----------

    private Notificacao notificacao(TipoNotificacao tipo) {
        Notificacao n = new Notificacao();
        n.setId(UUID.randomUUID());
        n.setTipo(tipo);
        n.setAgendamento(agendamento);
        n.setCriadaEm(agora);
        return n;
    }

    // simula o que o JwtAuthenticationFilter faz com o token
    private void logarComo(UUID usuarioId, String papel) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuarioId.toString(), null, List.of(new SimpleGrantedAuthority(papel))));
    }
}
