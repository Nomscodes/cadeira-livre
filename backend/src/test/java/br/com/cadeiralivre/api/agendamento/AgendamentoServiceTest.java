package br.com.cadeiralivre.api.agendamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.agendamento.dto.AgendamentoRequest;
import br.com.cadeiralivre.api.agendamento.dto.AgendamentoResponse;
import br.com.cadeiralivre.api.auditoria.AcaoAuditoria;
import br.com.cadeiralivre.api.auditoria.AuditoriaService;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class AgendamentoServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private DisponibilidadeService disponibilidadeService;

    @Mock
    private AuditoriaService auditoriaService;

    private AgendamentoService agendamentoService;

    // "Agora" fixo nos testes: 10/11/2026 às 09:00 no horário de Brasília
    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final LocalDate amanha = LocalDate.of(2026, 11, 11);
    private final LocalTime dezHoras = LocalTime.of(10, 0);
    private final LocalTime dezEMeia = LocalTime.of(10, 30);

    private Barbearia barbearia;
    private Servico corte;
    private Profissional rafael;

    @BeforeEach
    void preparar() {
        // o Clock não é mock, por isso o service é criado na mão
        agendamentoService = new AgendamentoService(agendamentoRepository, barbeariaRepository, servicoRepository,
                profissionalRepository, disponibilidadeService, auditoriaService, relogio);

        barbearia = new Barbearia();
        barbearia.setId(UUID.randomUUID());
        barbearia.setSlug("barbearia-teste");
        barbearia.setPublico(true);

        corte = new Servico();
        corte.setId(UUID.randomUUID());
        corte.setNome("Corte");
        corte.setDuracaoMinutos(30);
        corte.setBarbearia(barbearia);

        rafael = new Profissional();
        rafael.setId(UUID.randomUUID());
        rafael.setNome("Rafael Lima");
        rafael.setBarbearia(barbearia);
        rafael.getServicos().add(corte);
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    // ---------- solicitação pelo cliente (rota pública) ----------

    @Test
    void solicitacaoDoClienteNasceComoPendenteComOProfissionalEscolhido() {
        salaoServicoEProfissionalExistem();
        when(disponibilidadeService.estaLivre(barbearia, corte, rafael, amanha, dezHoras)).thenReturn(true);
        when(agendamentoRepository.save(any(Agendamento.class))).thenAnswer(chamada -> chamada.getArgument(0));

        AgendamentoResponse resposta = agendamentoService.solicitar("barbearia-teste", pedido(amanha, dezHoras));

        assertEquals(StatusAgendamento.PENDENTE, resposta.status());
        assertEquals(dezEMeia, resposta.horaFim()); // 10:00 + 30 min do corte
        assertEquals("Rafael Lima", resposta.profissional());
    }

    @Test
    void horarioQueNaoEstaDisponivelDevolve409() {
        salaoServicoEProfissionalExistem();
        // folga, bloqueio, fora do funcionamento ou já aprovado para o profissional
        when(disponibilidadeService.estaLivre(barbearia, corte, rafael, amanha, dezHoras)).thenReturn(false);

        ApiException erro = assertThrows(ApiException.class,
                () -> agendamentoService.solicitar("barbearia-teste", pedido(amanha, dezHoras)));

        assertEquals(409, erro.getStatus().value());
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void horarioNoPassadoDevolve422() {
        salaoServicoEProfissionalExistem();
        LocalDate ontem = LocalDate.of(2026, 11, 9);

        ApiException erro = assertThrows(ApiException.class,
                () -> agendamentoService.solicitar("barbearia-teste", pedido(ontem, dezHoras)));

        assertEquals(422, erro.getStatus().value());
    }

    @Test
    void salaoQueAindaNaoFoiPublicadoDevolve404() {
        barbearia.setPublico(false);
        when(barbeariaRepository.findBySlug("barbearia-teste")).thenReturn(Optional.of(barbearia));

        ApiException erro = assertThrows(ApiException.class,
                () -> agendamentoService.solicitar("barbearia-teste", pedido(amanha, dezHoras)));

        assertEquals(404, erro.getStatus().value());
    }

    @Test
    void servicoQueNaoEhDoSalaoDevolve422() {
        when(barbeariaRepository.findBySlug("barbearia-teste")).thenReturn(Optional.of(barbearia));
        when(servicoRepository.findByIdAndBarbeariaId(corte.getId(), barbearia.getId())).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> agendamentoService.solicitar("barbearia-teste", pedido(amanha, dezHoras)));

        assertEquals(422, erro.getStatus().value());
    }

    @Test
    void servicoDesativadoNaoPodeSerAgendado() {
        corte.setAtivo(false);
        salaoEServicoExistem();

        ApiException erro = assertThrows(ApiException.class,
                () -> agendamentoService.solicitar("barbearia-teste", pedido(amanha, dezHoras)));

        assertEquals(422, erro.getStatus().value());
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void profissionalQueNaoFazOServicoDevolve422() {
        rafael.getServicos().clear();
        salaoServicoEProfissionalExistem();

        ApiException erro = assertThrows(ApiException.class,
                () -> agendamentoService.solicitar("barbearia-teste", pedido(amanha, dezHoras)));

        assertEquals(422, erro.getStatus().value());
        verify(agendamentoRepository, never()).save(any());
    }

    @Test
    void profissionalDeOutraBarbeariaDevolve422() {
        salaoEServicoExistem();
        when(profissionalRepository.findByIdAndBarbeariaId(rafael.getId(), barbearia.getId())).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> agendamentoService.solicitar("barbearia-teste", pedido(amanha, dezHoras)));

        assertEquals(422, erro.getStatus().value());
        verify(agendamentoRepository, never()).save(any());
    }

    // ---------- lista do painel ----------

    @Test
    void listarSemDataTrazTodosOsAgendamentosDaBarbearia() {
        logarComoDono();

        agendamentoService.listarDoPainel(null);

        verify(agendamentoRepository).findAllByBarbeariaIdOrderByDataDescHoraInicioDesc(barbearia.getId());
    }

    @Test
    void listarComDataTrazSoAAgendaDaqueleDia() {
        logarComoDono();
        when(agendamentoRepository.findAllByBarbeariaIdAndDataOrderByHoraInicioAscProfissionalNomeAsc(barbearia.getId(), amanha))
                .thenReturn(List.of(agendamentoPendente()));

        List<AgendamentoResponse> agenda = agendamentoService.listarDoPainel(amanha);

        assertEquals(1, agenda.size());
        assertEquals(amanha, agenda.get(0).data());
        verify(agendamentoRepository, never()).findAllByBarbeariaIdOrderByDataDescHoraInicioDesc(any());
    }

    // ---------- aprovação e recusa pelo dono (painel) ----------

    @Test
    void aprovarSemConflitoMudaParaAprovadoERegistraNaAuditoria() {
        logarComoDono();
        Agendamento pendente = agendamentoPendente();
        when(agendamentoRepository.findByIdAndBarbeariaId(pendente.getId(), barbearia.getId())).thenReturn(Optional.of(pendente));
        when(agendamentoRepository.existeConflito(rafael.getId(), amanha, StatusAgendamento.APROVADO, dezHoras, dezEMeia))
                .thenReturn(false);

        AgendamentoResponse resposta = agendamentoService.aprovar(pendente.getId());

        assertEquals(StatusAgendamento.APROVADO, resposta.status());
        verify(auditoriaService).registrar(eq(AcaoAuditoria.AGENDAMENTO_APROVADO), anyString());
    }

    @Test
    void aprovarTravaAAgendaDoProfissionalAntesDeConferirOConflito() {
        logarComoDono();
        Agendamento pendente = agendamentoPendente();
        when(agendamentoRepository.findByIdAndBarbeariaId(pendente.getId(), barbearia.getId())).thenReturn(Optional.of(pendente));
        when(agendamentoRepository.existeConflito(rafael.getId(), amanha, StatusAgendamento.APROVADO, dezHoras, dezEMeia))
                .thenReturn(false);

        agendamentoService.aprovar(pendente.getId());

        // a ordem importa: primeiro trava, depois confere (senão duas aprovações juntas passariam)
        InOrder ordem = inOrder(profissionalRepository, agendamentoRepository);
        ordem.verify(profissionalRepository).buscarComTrava(rafael.getId());
        ordem.verify(agendamentoRepository).existeConflito(rafael.getId(), amanha, StatusAgendamento.APROVADO, dezHoras, dezEMeia);
    }

    @Test
    void aprovarComOutroAgendamentoJaAprovadoNoHorarioDoProfissionalDevolve409() {
        logarComoDono();
        Agendamento pendente = agendamentoPendente();
        when(agendamentoRepository.findByIdAndBarbeariaId(pendente.getId(), barbearia.getId())).thenReturn(Optional.of(pendente));
        when(agendamentoRepository.existeConflito(rafael.getId(), amanha, StatusAgendamento.APROVADO, dezHoras, dezEMeia))
                .thenReturn(true);

        ApiException erro = assertThrows(ApiException.class, () -> agendamentoService.aprovar(pendente.getId()));

        assertEquals(409, erro.getStatus().value());
        assertEquals(StatusAgendamento.PENDENTE, pendente.getStatus()); // continua pendente
        verify(auditoriaService, never()).registrar(any(), any());
    }

    @Test
    void naoDaPraAprovarAgendamentoJaRecusado() {
        logarComoDono();
        Agendamento recusado = agendamentoPendente();
        recusado.setStatus(StatusAgendamento.RECUSADO);
        when(agendamentoRepository.findByIdAndBarbeariaId(recusado.getId(), barbearia.getId())).thenReturn(Optional.of(recusado));

        ApiException erro = assertThrows(ApiException.class, () -> agendamentoService.aprovar(recusado.getId()));

        assertEquals(409, erro.getStatus().value());
    }

    @Test
    void recusarMudaParaRecusadoERegistraNaAuditoria() {
        logarComoDono();
        Agendamento pendente = agendamentoPendente();
        when(agendamentoRepository.findByIdAndBarbeariaId(pendente.getId(), barbearia.getId())).thenReturn(Optional.of(pendente));

        AgendamentoResponse resposta = agendamentoService.recusar(pendente.getId());

        assertEquals(StatusAgendamento.RECUSADO, resposta.status());
        verify(auditoriaService).registrar(eq(AcaoAuditoria.AGENDAMENTO_RECUSADO), anyString());
    }

    @Test
    void auditoriaGuardaOAgendamentoSemOsDadosDoCliente() {
        logarComoDono();
        Agendamento pendente = agendamentoPendente();
        when(agendamentoRepository.findByIdAndBarbeariaId(pendente.getId(), barbearia.getId())).thenReturn(Optional.of(pendente));

        agendamentoService.recusar(pendente.getId());

        ArgumentCaptor<String> detalhe = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(eq(AcaoAuditoria.AGENDAMENTO_RECUSADO), detalhe.capture());
        assertTrue(detalhe.getValue().contains(pendente.getId().toString()));
        // LGPD: o log não guarda nome nem telefone do cliente
        assertFalse(detalhe.getValue().contains("Cliente Teste"));
        assertFalse(detalhe.getValue().contains("62999999999"));
    }

    @Test
    void aprovarAgendamentoDeOutraBarbeariaDevolve404() {
        logarComoDono();
        UUID agendamentoDeOutraBarbearia = UUID.randomUUID();
        when(agendamentoRepository.findByIdAndBarbeariaId(agendamentoDeOutraBarbearia, barbearia.getId()))
                .thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> agendamentoService.aprovar(agendamentoDeOutraBarbearia));

        assertEquals(404, erro.getStatus().value());
    }

    // ---------- métodos de apoio ----------

    private void salaoEServicoExistem() {
        when(barbeariaRepository.findBySlug("barbearia-teste")).thenReturn(Optional.of(barbearia));
        when(servicoRepository.findByIdAndBarbeariaId(corte.getId(), barbearia.getId())).thenReturn(Optional.of(corte));
    }

    private void salaoServicoEProfissionalExistem() {
        salaoEServicoExistem();
        when(profissionalRepository.findByIdAndBarbeariaId(rafael.getId(), barbearia.getId())).thenReturn(Optional.of(rafael));
    }

    private void logarComoDono() {
        TenantContext.set(barbearia.getId());
    }

    private AgendamentoRequest pedido(LocalDate data, LocalTime hora) {
        return new AgendamentoRequest(corte.getId(), rafael.getId(), data, hora, "Cliente Teste", "62999999999");
    }

    private Agendamento agendamentoPendente() {
        Agendamento a = new Agendamento();
        a.setId(UUID.randomUUID());
        a.setBarbearia(barbearia);
        a.setServico(corte);
        a.setProfissional(rafael);
        a.setClienteNome("Cliente Teste");
        a.setClienteTelefone("62999999999");
        a.setData(amanha);
        a.setHoraInicio(dezHoras);
        a.setHoraFim(dezEMeia);
        a.setStatus(StatusAgendamento.PENDENTE);
        return a;
    }
}
