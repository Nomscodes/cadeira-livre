package br.com.cadeiralivre.api.notificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import br.com.cadeiralivre.api.notificacao.dto.NotificacaoDaEquipeResponse;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.tenant.TenantContext;
import br.com.cadeiralivre.api.usuario.DonoBarbearia;
import br.com.cadeiralivre.api.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class NotificacaoDaEquipeServiceTest {

    @Mock
    private NotificacaoRepository notificacaoRepository;
    @Mock
    private ProfissionalRepository profissionalRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    private NotificacaoDaEquipeService service;

    // "Agora" fixo nos testes: 14/11/2026 às 09:00 no horário de Brasília
    private final Instant agora = Instant.parse("2026-11-14T12:00:00Z");

    private Barbearia barbearia;
    private DonoBarbearia dono;
    private Profissional gabriel; // o dono também atende
    private Profissional diego;
    private Agendamento agendamento;

    @BeforeEach
    void preparar() {
        service = new NotificacaoDaEquipeService(notificacaoRepository, profissionalRepository, usuarioRepository,
                Clock.fixed(agora, ZoneId.of("America/Sao_Paulo")));

        barbearia = new Barbearia();
        barbearia.setId(UUID.randomUUID());
        barbearia.setNomeFantasia("Barbearia Teste - Gabriel Naoki");

        dono = new DonoBarbearia();
        dono.setId(UUID.randomUUID());
        dono.setNome("Gabriel Naoki");

        gabriel = profissional("Gabriel Naoki");
        diego = profissional("Diego Santos");

        Servico corte = new Servico();
        corte.setNome("Corte");

        // Marcos pediu Corte + Barba com o Diego em 20/11 às 10:00
        agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setBarbearia(barbearia);
        agendamento.setServico(corte);
        agendamento.setProfissional(diego);
        agendamento.setClienteNome("Marcos Silva");
        agendamento.setData(LocalDate.of(2026, 11, 20));
        agendamento.setHoraInicio(LocalTime.of(10, 0));
        agendamento.setHoraFim(LocalTime.of(11, 0));
        agendamento.setStatus(StatusAgendamento.PENDENTE);
        agendamento.adicionarItem(item("Corte", diego));
        agendamento.adicionarItem(item("Barba", diego));

        TenantContext.set(barbearia.getId());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                dono.getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_DONO"))));
    }

    @AfterEach
    void limpar() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ---------- listar ----------

    @Test
    void listaNoFormatoDoFrontComDetalhesEAcaoDeAviso() {
        when(notificacaoRepository.findTop30ByBarbeariaIdAndProfissionalIsNullOrderByCriadaEmDesc(barbearia.getId()))
                .thenReturn(List.of(notificacao(TipoNotificacao.NOVO_AGENDAMENTO)));

        NotificacaoDaEquipeResponse n = service.listar().get(0);

        assertEquals("agendamento", n.tipo());
        assertEquals("Novo pedido de agendamento", n.titulo());
        assertFalse(n.lida());
        assertEquals("Marcos Silva", n.detalhes().cliente());
        assertEquals("Corte + Barba", n.detalhes().servicos());
        assertEquals("Diego Santos", n.detalhes().profissionais());
        assertEquals(LocalDate.of(2026, 11, 20), n.detalhes().data());
        assertEquals("10:00", n.detalhes().hora());
        assertEquals("aviso", n.acoes().get(0).id());
    }

    @Test
    void alertaDoFuncionarioMostraQuemMandou() {
        Notificacao alerta = notificacao(TipoNotificacao.ALERTA_FUNCIONARIO);
        alerta.setTexto("Diego Santos sinalizou o cliente Marcos Silva: atraso.");
        alerta.setRemetente("Diego Santos");
        when(notificacaoRepository.findTop30ByBarbeariaIdAndProfissionalIsNullOrderByCriadaEmDesc(barbearia.getId()))
                .thenReturn(List.of(alerta));

        NotificacaoDaEquipeResponse n = service.listar().get(0);

        assertEquals("alerta", n.tipo());
        assertEquals("Diego Santos", n.remetente());
        assertEquals("Diego Santos sinalizou o cliente Marcos Silva: atraso.", n.texto());
    }

    // ---------- marcar como lida ----------

    @Test
    void marcarComoLidaGuardaAHora() {
        Notificacao n = notificacao(TipoNotificacao.NOVO_AGENDAMENTO);
        when(notificacaoRepository.findByIdAndBarbeariaId(n.getId(), barbearia.getId())).thenReturn(Optional.of(n));

        service.marcarComoLida(n.getId());

        assertEquals(agora, n.getLidaEm());
    }

    @Test
    void donoNaoMarcaOAvisoQueFoiParaUmProfissional() {
        Notificacao aviso = notificacao(TipoNotificacao.AVISO_EQUIPE);
        aviso.setProfissional(diego);
        when(notificacaoRepository.findByIdAndBarbeariaId(aviso.getId(), barbearia.getId())).thenReturn(Optional.of(aviso));

        ApiException erro = assertThrows(ApiException.class, () -> service.marcarComoLida(aviso.getId()));

        assertEquals(404, erro.getStatus().value());
    }

    // ---------- enviar aviso à equipe ----------

    @Test
    void avisoVaiParaOsProfissionaisEscolhidosMenosOProprioDono() {
        Notificacao origem = notificacao(TipoNotificacao.NOVO_AGENDAMENTO);
        prepararAviso(origem);
        when(profissionalRepository.findByIdAndBarbeariaId(diego.getId(), barbearia.getId())).thenReturn(Optional.of(diego));

        int enviados = service.executarAcao(origem.getId(), "aviso", "  Cliente chega 10 min antes.  ",
                List.of(diego.getId(), gabriel.getId(), diego.getId()));

        assertEquals(1, enviados);
        ArgumentCaptor<Notificacao> salva = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository).save(salva.capture());
        Notificacao aviso = salva.getValue();
        assertEquals(TipoNotificacao.AVISO_EQUIPE, aviso.getTipo());
        assertSame(diego, aviso.getProfissional());
        assertSame(barbearia, aviso.getBarbearia());
        assertSame(agendamento, aviso.getAgendamento());
        assertEquals("Cliente chega 10 min antes.", aviso.getTexto());
        assertEquals("Gabriel Naoki", aviso.getRemetente());
        assertEquals(agora, aviso.getCriadaEm());
    }

    @Test
    void avisoSemMensagemEhRecusado() {
        Notificacao origem = notificacao(TipoNotificacao.NOVO_AGENDAMENTO);
        when(notificacaoRepository.findByIdAndBarbeariaId(origem.getId(), barbearia.getId())).thenReturn(Optional.of(origem));

        ApiException erro = assertThrows(ApiException.class,
                () -> service.executarAcao(origem.getId(), "aviso", "   ", List.of(diego.getId())));

        assertEquals(422, erro.getStatus().value());
        assertEquals("Escreva a mensagem do aviso.", erro.getMessage());
    }

    @Test
    void avisoSoParaOProprioDonoOuOutraBarbeariaEhRecusado() {
        Notificacao origem = notificacao(TipoNotificacao.NOVO_AGENDAMENTO);
        prepararAviso(origem);
        UUID deOutraBarbearia = UUID.randomUUID();
        when(profissionalRepository.findByIdAndBarbeariaId(deOutraBarbearia, barbearia.getId())).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> service.executarAcao(origem.getId(), "aviso", "Oi", List.of(gabriel.getId(), deOutraBarbearia)));

        assertEquals("Escolha ao menos um profissional da equipe para receber o aviso.", erro.getMessage());
        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    void naoDaParaRepassarUmAvisoNemUsarAcaoDesconhecida() {
        Notificacao origem = notificacao(TipoNotificacao.NOVO_AGENDAMENTO);
        when(notificacaoRepository.findByIdAndBarbeariaId(origem.getId(), barbearia.getId())).thenReturn(Optional.of(origem));

        ApiException erro = assertThrows(ApiException.class,
                () -> service.executarAcao(origem.getId(), "apagar", null, null));

        assertEquals("Esta ação não está disponível para esta notificação.", erro.getMessage());
    }

    // ---------- métodos de apoio ----------

    private void prepararAviso(Notificacao origem) {
        when(notificacaoRepository.findByIdAndBarbeariaId(origem.getId(), barbearia.getId())).thenReturn(Optional.of(origem));
        when(usuarioRepository.findById(dono.getId())).thenReturn(Optional.of(dono));
        when(profissionalRepository.findByUsuarioId(dono.getId())).thenReturn(Optional.of(gabriel));
    }

    private Notificacao notificacao(TipoNotificacao tipo) {
        Notificacao n = new Notificacao();
        n.setId(UUID.randomUUID());
        n.setTipo(tipo);
        n.setAgendamento(agendamento);
        n.setBarbearia(barbearia);
        n.setCriadaEm(agora);
        return n;
    }

    private Profissional profissional(String nome) {
        Profissional p = new Profissional();
        p.setId(UUID.randomUUID());
        p.setNome(nome);
        return p;
    }

    private AgendamentoItem item(String nome, Profissional p) {
        AgendamentoItem i = new AgendamentoItem();
        i.setNome(nome);
        i.setProfissional(p);
        i.setPreco(new BigDecimal("30.00"));
        i.setDuracaoMinutos(30);
        return i;
    }
}
