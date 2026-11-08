package br.com.cadeiralivre.api.avaliacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.avaliacao.dto.AvaliacaoRequest;
import br.com.cadeiralivre.api.avaliacao.dto.AvaliacaoResponse;
import br.com.cadeiralivre.api.avaliacao.dto.AvaliacoesDoPortalResponse;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.servico.Servico;

@ExtendWith(MockitoExtension.class)
class AvaliacaoServiceTest {

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @InjectMocks
    private AvaliacaoService avaliacaoService;

    private final UUID gabrielId = UUID.randomUUID();
    private Barbearia barbearia;
    private Agendamento atendimento;

    @BeforeEach
    void preparar() {
        barbearia = new Barbearia();
        barbearia.setId(UUID.randomUUID());
        barbearia.setSlug("barbearia-teste");
        barbearia.setPublico(true);

        Servico corte = new Servico();
        corte.setNome("Corte");

        Profissional diego = new Profissional();
        diego.setNome("Diego Santos");

        // atendimento do Gabriel que o dono já marcou como concluído
        atendimento = new Agendamento();
        atendimento.setId(UUID.randomUUID());
        atendimento.setBarbearia(barbearia);
        atendimento.setServico(corte);
        atendimento.setProfissional(diego);
        atendimento.setClienteNome("Gabriel Naoki");
        atendimento.setData(LocalDate.of(2026, 11, 6));
        atendimento.setHoraInicio(LocalTime.of(16, 0));
        atendimento.setHoraFim(LocalTime.of(16, 30));
        atendimento.setStatus(StatusAgendamento.CONCLUIDO);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    // ---------- avaliar (área do cliente) ----------

    @Test
    void avaliarAtendimentoConcluidoGuardaANotaEOComentario() {
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(atendimento.getId(), gabrielId)).thenReturn(Optional.of(atendimento));
        when(avaliacaoRepository.save(any(Avaliacao.class))).thenAnswer(chamada -> chamada.getArgument(0));

        AvaliacaoResponse resposta = avaliacaoService.avaliar(atendimento.getId(),
                new AvaliacaoRequest(5, "  Corte excelente, atendimento pontual.  "));

        ArgumentCaptor<Avaliacao> salva = ArgumentCaptor.forClass(Avaliacao.class);
        verify(avaliacaoRepository).save(salva.capture());
        assertSame(atendimento, salva.getValue().getAgendamento());
        assertEquals(5, resposta.nota());
        assertEquals("Corte excelente, atendimento pontual.", resposta.comentario());
        assertEquals(atendimento.getId(), resposta.agendamentoId());
    }

    @Test
    void comentarioEmBrancoFicaComoSemComentario() {
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(atendimento.getId(), gabrielId)).thenReturn(Optional.of(atendimento));
        when(avaliacaoRepository.save(any(Avaliacao.class))).thenAnswer(chamada -> chamada.getArgument(0));

        AvaliacaoResponse resposta = avaliacaoService.avaliar(atendimento.getId(), new AvaliacaoRequest(4, "   "));

        assertNull(resposta.comentario());
    }

    @Test
    void atendimentoQueNaoFoiConcluidoNaoPodeSerAvaliado() {
        // aprovado, mas o dono ainda não marcou que o atendimento aconteceu
        atendimento.setStatus(StatusAgendamento.APROVADO);
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(atendimento.getId(), gabrielId)).thenReturn(Optional.of(atendimento));

        ApiException erro = assertThrows(ApiException.class,
                () -> avaliacaoService.avaliar(atendimento.getId(), new AvaliacaoRequest(5, null)));

        assertEquals(409, erro.getStatus().value());
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void atendimentoJaAvaliadoNaoPodeSerAvaliadoDeNovo() {
        logarComo("ROLE_CLIENTE");
        when(agendamentoRepository.findByIdAndClienteId(atendimento.getId(), gabrielId)).thenReturn(Optional.of(atendimento));
        when(avaliacaoRepository.existsByAgendamentoId(atendimento.getId())).thenReturn(true);

        ApiException erro = assertThrows(ApiException.class,
                () -> avaliacaoService.avaliar(atendimento.getId(), new AvaliacaoRequest(1, "mudei de ideia")));

        assertEquals(409, erro.getStatus().value());
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void atendimentoDeOutroClienteDevolve404() {
        logarComo("ROLE_CLIENTE");
        // a busca é por id E cliente: o atendimento de outra pessoa não é encontrado
        when(agendamentoRepository.findByIdAndClienteId(atendimento.getId(), gabrielId)).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> avaliacaoService.avaliar(atendimento.getId(), new AvaliacaoRequest(5, null)));

        assertEquals(404, erro.getStatus().value());
        verify(avaliacaoRepository, never()).save(any());
    }

    @Test
    void quemNaoEhClienteNaoAvalia() {
        // o SecurityConfig já barra o dono em /cliente/**; o service confere de novo por segurança
        logarComo("ROLE_DONO");

        ApiException erro = assertThrows(ApiException.class,
                () -> avaliacaoService.avaliar(atendimento.getId(), new AvaliacaoRequest(5, null)));

        assertEquals(401, erro.getStatus().value());
        verifyNoInteractions(agendamentoRepository, avaliacaoRepository);
    }

    // ---------- avaliações no portal (rota pública) ----------

    @Test
    void portalMostraMediaComUmaCasaDecimalTotalEAvaliacoes() {
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setAgendamento(atendimento);
        avaliacao.setNota((short) 5);
        avaliacao.setComentario("Corte excelente");
        when(barbeariaRepository.findBySlug("barbearia-teste")).thenReturn(Optional.of(barbearia));
        when(avaliacaoRepository.mediaDaBarbearia(barbearia.getId())).thenReturn(4.666666);
        when(avaliacaoRepository.countByAgendamentoBarbeariaId(barbearia.getId())).thenReturn(3L);
        when(avaliacaoRepository.findTop20ByAgendamentoBarbeariaIdOrderByCriadoEmDesc(barbearia.getId()))
                .thenReturn(List.of(avaliacao));

        AvaliacoesDoPortalResponse resposta = avaliacaoService.listarDoPortal("barbearia-teste");

        assertEquals(4.7, resposta.media());
        assertEquals(3, resposta.total());
        assertEquals(1, resposta.avaliacoes().size());
        assertEquals("Diego Santos", resposta.avaliacoes().get(0).profissional());
        assertEquals("Corte", resposta.avaliacoes().get(0).servico());
    }

    @Test
    void portalSemAvaliacoesFicaSemMedia() {
        when(barbeariaRepository.findBySlug("barbearia-teste")).thenReturn(Optional.of(barbearia));
        when(avaliacaoRepository.mediaDaBarbearia(barbearia.getId())).thenReturn(null);
        when(avaliacaoRepository.countByAgendamentoBarbeariaId(barbearia.getId())).thenReturn(0L);
        when(avaliacaoRepository.findTop20ByAgendamentoBarbeariaIdOrderByCriadoEmDesc(barbearia.getId()))
                .thenReturn(List.of());

        AvaliacoesDoPortalResponse resposta = avaliacaoService.listarDoPortal("barbearia-teste");

        assertNull(resposta.media());
        assertEquals(0, resposta.total());
        assertTrue(resposta.avaliacoes().isEmpty());
    }

    @Test
    void portalQueAindaNaoFoiPublicadoDevolve404() {
        barbearia.setPublico(false);
        when(barbeariaRepository.findBySlug("barbearia-teste")).thenReturn(Optional.of(barbearia));

        ApiException erro = assertThrows(ApiException.class, () -> avaliacaoService.listarDoPortal("barbearia-teste"));

        assertEquals(404, erro.getStatus().value());
        verifyNoInteractions(avaliacaoRepository);
    }

    // simula o que o JwtAuthenticationFilter faz com o token do Gabriel
    private void logarComo(String papel) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                gabrielId.toString(), null, List.of(new SimpleGrantedAuthority(papel))));
    }
}
