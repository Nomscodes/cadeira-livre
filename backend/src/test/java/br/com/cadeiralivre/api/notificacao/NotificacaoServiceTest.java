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

import java.math.BigDecimal;
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
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.dto.NotificacaoResponse;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.tenant.TenantContext;
import br.com.cadeiralivre.api.usuario.Cliente;
import br.com.cadeiralivre.api.usuario.DonoBarbearia;
import br.com.cadeiralivre.api.usuario.Funcionario;
import br.com.cadeiralivre.api.usuario.Usuario;

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
    void equipeRecebeSoQuemTemContaDeFuncionarioUmaVezCada() {
        // combo: o Diego faz corte e barba (2 itens), o dono faz a sobrancelha e a Ana ainda não usou o convite
        Profissional diego = profissionalComConta("Diego Santos", new Funcionario());
        Profissional donoGabriel = profissionalComConta("Gabriel Naoki", new DonoBarbearia());
        Profissional ana = profissionalComConta("Ana Lima", null);
        agendamento.adicionarItem(item(diego));
        agendamento.adicionarItem(item(diego));
        agendamento.adicionarItem(item(donoGabriel));
        agendamento.adicionarItem(item(ana));

        notificacaoService.paraEquipe(agendamento, TipoNotificacao.ATENDIMENTO_NA_AGENDA);

        ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(salva.capture());
        assertSame(diego, salva.getValue().getProfissional());
        assertSame(barbearia, salva.getValue().getBarbearia());
        assertEquals(TipoNotificacao.ATENDIMENTO_NA_AGENDA, salva.getValue().getTipo());
    }

    @Test
    void textoUsaONomeSocialDoProfissionalQuandoTiver() {
        agendamento.getProfissional().setNomeSocial("Diego");

        NotificacaoResponse r = NotificacaoResponse.de(notificacao(TipoNotificacao.HORARIO_APROVADO));

        assertEquals("A barbearia confirmou seu Corte com Diego em 12/11 às 14:00.", r.texto());
    }

    @Test
    void textoComVariosItensMostraTodosOsServicosEProfissionais() {
        // combo com o Rafael + sobrancelha com o Lucas: o texto não pode falar só do serviço principal
        Profissional rafael = profissionalComConta("Rafael Lima", null);
        rafael.setNomeSocial("Rafael");
        Profissional lucas = profissionalComConta("Lucas Pereira", null);
        lucas.setNomeSocial("Lucas");
        AgendamentoItem combo = item(rafael);
        combo.setNome("Corte + Barba");
        AgendamentoItem sobrancelha = item(lucas);
        sobrancelha.setNome("Sobrancelha");
        agendamento.adicionarItem(combo);
        agendamento.adicionarItem(sobrancelha);

        NotificacaoResponse r = NotificacaoResponse.de(notificacao(TipoNotificacao.NOVO_AGENDAMENTO));

        assertEquals("Gabriel Naoki pediu Corte + Barba + Sobrancelha com Rafael, Lucas em 12/11 às 14:00."
                + " Aprove ou recuse no painel.", r.texto());
    }

    @Test
    void atendimentoNaAgendaMostraOClienteEOHorario() {
        Notificacao n = notificacao(TipoNotificacao.ATENDIMENTO_NA_AGENDA);

        NotificacaoResponse r = NotificacaoResponse.de(n);

        assertEquals("Novo atendimento na sua agenda", r.titulo());
        assertEquals("Gabriel Naoki marcou Corte com você em 12/11 às 14:00.", r.texto());
    }

    @Test
    void cancelamentoPeloSalaoGuardaOTextoSemOAgendamento() {
        notificacaoService.cancelamentoPeloSalao(agendamento);

        ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(salva.capture());
        Notificacao n = salva.getValue();
        assertEquals(TipoNotificacao.HORARIO_CANCELADO_PELO_SALAO, n.getTipo());
        assertSame(gabriel, n.getCliente());
        assertNull(n.getBarbearia());
        // o agendamento vai ser apagado: o aviso fica só com o texto, sem o nome do cliente
        assertNull(n.getAgendamento());
        assertEquals("Seu horário de Corte em 12/11 às 14:00 na Barbearia Teste - Gabriel Naoki foi cancelado pelo salão.",
                n.getTexto());
        assertEquals(agora, n.getCriadaEm());
    }

    @Test
    void cancelamentoPeloSalaoNaoAvisaQuemAgendouSemConta() {
        agendamento.setCliente(null);

        notificacaoService.cancelamentoPeloSalao(agendamento);

        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    void avisoDoSalaoFicaLigadoAoAgendamentoComOTextoPronto() {
        notificacaoService.avisoDoSalao(agendamento, "A Barbearia Teste não vai abrir em 12/11. Seu horário das 14:00 continua marcado.");

        ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(salva.capture());
        Notificacao n = salva.getValue();
        assertEquals(TipoNotificacao.AVISO_DO_SALAO, n.getTipo());
        assertSame(gabriel, n.getCliente());
        assertSame(agendamento, n.getAgendamento()); // o horário continua existindo (D1)
        assertEquals("A Barbearia Teste não vai abrir em 12/11. Seu horário das 14:00 continua marcado.", n.getTexto());
    }

    @Test
    void avisoDoSalaoNaoVaiParaQuemAgendouSemConta() {
        agendamento.setCliente(null);

        notificacaoService.avisoDoSalao(agendamento, "qualquer texto");

        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    void avisoDeHorarioJaApagadoUsaOTextoGuardado() {
        // o salão apagou os dados: o agendamento não existe mais, só o texto que ficou guardado (V21)
        Notificacao n = notificacao(TipoNotificacao.HORARIO_CANCELADO_PELO_SALAO);
        n.setAgendamento(null);
        n.setTexto("Seu horário de Corte em 12/11 às 14:00 na Barbearia Naoki foi cancelado pelo salão.");

        NotificacaoResponse r = NotificacaoResponse.de(n);

        assertEquals("Horário cancelado pelo salão", r.titulo());
        assertEquals("Seu horário de Corte em 12/11 às 14:00 na Barbearia Naoki foi cancelado pelo salão.", r.texto());
        assertNull(r.agendamentoId());
        assertFalse(r.lida());
    }

    @Test
    void avisoDoSalaoComAgendamentoUsaOTextoGuardadoEMantemOAgendamento() {
        Notificacao n = notificacao(TipoNotificacao.AVISO_DO_SALAO);
        n.setTexto("A Barbearia Naoki não vai atender em 12/11 (Feriado). Seu horário das 14:00 continua marcado.");

        NotificacaoResponse r = NotificacaoResponse.de(n);

        assertEquals("Aviso do salão", r.titulo());
        assertEquals("A Barbearia Naoki não vai atender em 12/11 (Feriado). Seu horário das 14:00 continua marcado.", r.texto());
        assertEquals(agendamento.getId(), r.agendamentoId());
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
        when(notificacaoRepository.findTop30ByBarbeariaIdAndProfissionalIsNullOrderByCriadaEmDesc(barbearia.getId()))
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
    void lembreteDoDonoMostraOClienteEOHorario() {
        TenantContext.set(barbearia.getId());
        when(notificacaoRepository.findTop30ByBarbeariaIdAndProfissionalIsNullOrderByCriadaEmDesc(barbearia.getId()))
                .thenReturn(List.of(notificacao(TipoNotificacao.LEMBRETE_DONO)));

        NotificacaoResponse n = notificacaoService.listarDoDono().get(0);

        assertEquals("Atendimento daqui a pouco", n.titulo());
        assertEquals("Gabriel Naoki tem horário às 14:00 com Diego Santos (Corte).", n.texto());
    }

    @Test
    void lembreteDoClienteMostraABarbearia() {
        logarComo(gabriel.getId(), "ROLE_CLIENTE");
        when(notificacaoRepository.findTop30ByClienteIdOrderByCriadaEmDesc(gabriel.getId()))
                .thenReturn(List.of(notificacao(TipoNotificacao.LEMBRETE_CLIENTE)));

        NotificacaoResponse n = notificacaoService.listarDoCliente().get(0);

        assertEquals("Seu horário é amanhã", n.titulo());
        assertEquals("Corte com Diego Santos em 12/11 às 14:00, na Barbearia Teste - Gabriel Naoki.", n.texto());
    }

    @Test
    void alertaDoFuncionarioMostraOTextoQueEleEscreveu() {
        Notificacao alerta = notificacao(TipoNotificacao.ALERTA_FUNCIONARIO);
        alerta.setTexto("Cliente avisou que vai atrasar 15 minutos.");
        alerta.setRemetente("Diego Santos");
        TenantContext.set(barbearia.getId());
        when(notificacaoRepository.findTop30ByBarbeariaIdAndProfissionalIsNullOrderByCriadaEmDesc(barbearia.getId()))
                .thenReturn(List.of(alerta));

        NotificacaoResponse n = notificacaoService.listarDoDono().get(0);

        assertEquals("Alerta de funcionário", n.titulo());
        assertEquals("Cliente avisou que vai atrasar 15 minutos.", n.texto());
    }

    @Test
    void clienteQueExcluiuAContaApareceComoClienteRemovido() {
        // depois da exclusão da conta (PR16) o agendamento fica sem o nome; o texto acompanha (LGPD)
        agendamento.setClienteNome("Cliente removido");
        TenantContext.set(barbearia.getId());
        when(notificacaoRepository.findTop30ByBarbeariaIdAndProfissionalIsNullOrderByCriadaEmDesc(barbearia.getId()))
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

    private Profissional profissionalComConta(String nome, Usuario conta) {
        Profissional p = new Profissional();
        p.setId(UUID.randomUUID());
        p.setNome(nome);
        p.setUsuario(conta);
        return p;
    }

    private AgendamentoItem item(Profissional p) {
        AgendamentoItem i = new AgendamentoItem();
        i.setNome("Corte");
        i.setProfissional(p);
        i.setPreco(new BigDecimal("40.00"));
        i.setDuracaoMinutos(30);
        return i;
    }

    // simula o que o JwtAuthenticationFilter faz com o token
    private void logarComo(UUID usuarioId, String papel) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuarioId.toString(), null, List.of(new SimpleGrantedAuthority(papel))));
    }
}
