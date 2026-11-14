package br.com.cadeiralivre.api.agendamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.cadeiralivre.api.agendamento.dto.MeuAgendamentoResponse;
import br.com.cadeiralivre.api.avaliacao.AvaliacaoRepository;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.NotificacaoService;
import br.com.cadeiralivre.api.notificacao.TipoNotificacao;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.servico.Servico;

@ExtendWith(MockitoExtension.class)
class ClienteAgendamentoServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private NotificacaoService notificacaoService;

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    private ClienteAgendamentoService clienteAgendamentoService;

    // "Agora" fixo nos testes: 10/11/2026 às 09:00 no horário de Brasília
    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private final UUID joaoId = UUID.randomUUID();
    private Agendamento agendamento;

    @BeforeEach
    void preparar() {
        // o Clock não é mock, por isso o service é criado na mão
        clienteAgendamentoService = new ClienteAgendamentoService(agendamentoRepository, avaliacaoRepository,
                notificacaoService, relogio);

        Barbearia barbearia = new Barbearia();
        barbearia.setId(UUID.randomUUID());
        barbearia.setNomeFantasia("Barbearia Teste");
        barbearia.setSlug("barbearia-teste");
        barbearia.setTelefone("62999990000");
        barbearia.setCancelamentoHoras(1); // prazo de cancelamento deste salão

        Servico corte = new Servico();
        corte.setNome("Corte");
        corte.setPreco(new BigDecimal("40.00"));
        corte.setDuracaoMinutos(30);

        Profissional rafael = new Profissional();
        rafael.setId(UUID.randomUUID());
        rafael.setNome("Rafael Lima");
        rafael.setNomeSocial("Rafael");

        // agendamento do João para amanhã às 10:00
        agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setBarbearia(barbearia);
        agendamento.setServico(corte);
        agendamento.setProfissional(rafael);
        agendamento.setData(LocalDate.of(2026, 11, 11));
        agendamento.setHoraInicio(LocalTime.of(10, 0));
        agendamento.setHoraFim(LocalTime.of(10, 30));
        agendamento.setStatus(StatusAgendamento.PENDENTE);
        agendamento.adicionarItem(AgendamentoItem.doServico(corte, rafael, 0));
        agendamento.setValor(new BigDecimal("40.00"));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void historicoTrazOsAgendamentosDoClienteLogadoComABarbearia() {
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findAllByClienteIdOrderByDataDescHoraInicioDesc(joaoId))
                .thenReturn(List.of(agendamento));

        List<MeuAgendamentoResponse> historico = clienteAgendamentoService.listarMeus();

        assertEquals(1, historico.size());
        MeuAgendamentoResponse meu = historico.get(0);
        assertEquals("Barbearia Teste", meu.salao().nome());
        assertEquals("barbearia-teste", meu.salao().slug());
        assertEquals("62999990000", meu.salao().whatsapp());
        assertEquals("Corte", meu.servicos());
        assertEquals("Rafael", meu.profissionais()); // o cliente vê o nome social
        assertEquals("10:00", meu.hora());
        assertEquals(new BigDecimal("40.00"), meu.valor());
        assertFalse(meu.avaliado());
        assertTrue(meu.podeCancelar());
        assertEquals(1, meu.cancelamentoHoras());
    }

    @Test
    void detalheDeAgendamentoDeOutroClienteDevolve404() {
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(agendamento.getId(), joaoId)).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> clienteAgendamentoService.detalhe(agendamento.getId()));

        assertEquals(404, erro.getStatus().value());
    }

    @Test
    void detalheMostraSeJaFoiAvaliado() {
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(agendamento.getId(), joaoId)).thenReturn(Optional.of(agendamento));
        when(avaliacaoRepository.existsByAgendamentoId(agendamento.getId())).thenReturn(true);

        MeuAgendamentoResponse meu = clienteAgendamentoService.detalhe(agendamento.getId());

        assertTrue(meu.avaliado());
        assertFalse(meu.podeCancelar()); // concluído não se cancela
    }

    @Test
    void prazoDeCancelamentoEhODoSalao() {
        // o salão pede 4 horas; o agendamento é hoje às 12:00 e "agora" são 09:00 (faltam 3 horas)
        agendamento.getBarbearia().setCancelamentoHoras(4);
        agendamento.setData(LocalDate.of(2026, 11, 10));
        agendamento.setHoraInicio(LocalTime.of(12, 0));
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(agendamento.getId(), joaoId)).thenReturn(Optional.of(agendamento));

        ApiException erro = assertThrows(ApiException.class,
                () -> clienteAgendamentoService.cancelar(agendamento.getId()));

        assertEquals("O prazo para cancelar já passou. Fale com o salão pelo WhatsApp.", erro.getMessage());
        assertEquals(StatusAgendamento.PENDENTE, agendamento.getStatus());
    }

    @Test
    void cancelarPendenteMudaParaCancelado() {
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(agendamento.getId(), joaoId)).thenReturn(Optional.of(agendamento));

        MeuAgendamentoResponse resposta = clienteAgendamentoService.cancelar(agendamento.getId());

        assertEquals(StatusAgendamento.CANCELADO, resposta.status());
        // o dono fica sabendo do cancelamento
        verify(notificacaoService).paraDono(agendamento, TipoNotificacao.CANCELAMENTO);
    }

    @Test
    void cancelarAprovadoTambemFunciona() {
        // como a disponibilidade só considera os APROVADOS, o horário volta a ficar livre
        agendamento.setStatus(StatusAgendamento.APROVADO);
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(agendamento.getId(), joaoId)).thenReturn(Optional.of(agendamento));

        clienteAgendamentoService.cancelar(agendamento.getId());

        assertEquals(StatusAgendamento.CANCELADO, agendamento.getStatus());
    }

    @Test
    void agendamentoDeOutroClienteDevolve404() {
        logarComo("ROLE_CLIENTE");
        // a busca é por id E cliente: o agendamento de outra pessoa simplesmente não é encontrado
        when(agendamentoRepository.findByIdAndClienteId(agendamento.getId(), joaoId)).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> clienteAgendamentoService.cancelar(agendamento.getId()));

        assertEquals(404, erro.getStatus().value());
    }

    @Test
    void naoDaPraCancelarAgendamentoRecusado() {
        agendamento.setStatus(StatusAgendamento.RECUSADO);
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(agendamento.getId(), joaoId)).thenReturn(Optional.of(agendamento));

        ApiException erro = assertThrows(ApiException.class,
                () -> clienteAgendamentoService.cancelar(agendamento.getId()));

        assertEquals(409, erro.getStatus().value());
        assertEquals(StatusAgendamento.RECUSADO, agendamento.getStatus());
        verifyNoInteractions(notificacaoService);
    }

    @Test
    void faltandoMenosDe1HoraNaoDaPraCancelar() {
        // hoje às 09:30, e "agora" são 09:00: faltam só 30 minutos
        agendamento.setData(LocalDate.of(2026, 11, 10));
        agendamento.setHoraInicio(LocalTime.of(9, 30));
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(agendamento.getId(), joaoId)).thenReturn(Optional.of(agendamento));

        ApiException erro = assertThrows(ApiException.class,
                () -> clienteAgendamentoService.cancelar(agendamento.getId()));

        assertEquals(422, erro.getStatus().value());
        assertEquals(StatusAgendamento.PENDENTE, agendamento.getStatus());
    }

    @Test
    void faltandoExatamente1HoraAindaDaPraCancelar() {
        // hoje às 10:00, e "agora" são 09:00: é o limite, ainda vale
        agendamento.setData(LocalDate.of(2026, 11, 10));
        agendamento.setHoraInicio(LocalTime.of(10, 0));
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(agendamento.getId(), joaoId)).thenReturn(Optional.of(agendamento));

        clienteAgendamentoService.cancelar(agendamento.getId());

        assertEquals(StatusAgendamento.CANCELADO, agendamento.getStatus());
    }

    @Test
    void quemNaoEhClienteNaoTemHistorico() {
        // o SecurityConfig já barra o dono em /cliente/**; o service confere de novo por segurança
        logarComo("ROLE_DONO");

        ApiException erro = assertThrows(ApiException.class, () -> clienteAgendamentoService.listarMeus());

        assertEquals(401, erro.getStatus().value());
        verifyNoInteractions(agendamentoRepository);
    }

    // simula o que o JwtAuthenticationFilter faz com o token do João
    private void logarComo(String papel) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                joaoId.toString(), null, List.of(new SimpleGrantedAuthority(papel))));
    }
}
