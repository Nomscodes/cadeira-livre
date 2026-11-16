package br.com.cadeiralivre.api.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.usuario.dto.ContaResponse;
import br.com.cadeiralivre.api.usuario.dto.ExclusaoContaRequest;
import br.com.cadeiralivre.api.usuario.dto.TrocaSenhaRequest;

@ExtendWith(MockitoExtension.class)
class ContaClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ContaClienteService contaClienteService;

    // "Agora" fixo nos testes: 10/11/2026 às 09:00 no horário de Brasília
    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private Cliente gabriel;

    @BeforeEach
    void preparar() {
        // o Clock não é mock, por isso o service é criado na mão
        contaClienteService = new ContaClienteService(clienteRepository, agendamentoRepository, passwordEncoder, relogio);

        gabriel = new Cliente();
        gabriel.setId(UUID.randomUUID());
        gabriel.setNome("Gabriel Naoki");
        gabriel.setEmail("gabriel@teste.com");
        gabriel.setTelefone("62981234567");
        gabriel.setSenhaHash("hash-da-senha-atual");
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    // ---------- meus dados ----------

    @Test
    void meusDadosMostraOsDadosDoClienteLogado() {
        logarComoGabriel();

        ContaResponse dados = contaClienteService.meusDados();

        assertEquals("Gabriel Naoki", dados.nome());
        assertEquals("gabriel@teste.com", dados.email());
        assertEquals("62981234567", dados.telefone());
    }

    @Test
    void tokenDeUmaContaJaExcluidaDevolve401() {
        logarComo(gabriel.getId(), "ROLE_CLIENTE");
        when(clienteRepository.findById(gabriel.getId())).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> contaClienteService.meusDados());

        assertEquals(401, erro.getStatus().value());
    }

    @Test
    void quemNaoEhClienteNaoAcessaAConta() {
        // o SecurityConfig já barra o dono em /cliente/**; o service confere de novo por segurança
        logarComo(UUID.randomUUID(), "ROLE_DONO");

        ApiException erro = assertThrows(ApiException.class, () -> contaClienteService.meusDados());

        assertEquals(401, erro.getStatus().value());
        verifyNoInteractions(clienteRepository);
    }

    // ---------- troca de senha ----------

    @Test
    void trocarSenhaComASenhaAtualCertaGravaANovaCriptografada() {
        logarComoGabriel();
        when(passwordEncoder.matches("senhaAtual1", "hash-da-senha-atual")).thenReturn(true);
        when(passwordEncoder.matches("senhaNova2", "hash-da-senha-atual")).thenReturn(false);
        when(passwordEncoder.encode("senhaNova2")).thenReturn("hash-da-senha-nova");

        contaClienteService.trocarSenha(new TrocaSenhaRequest("senhaAtual1", "senhaNova2"));

        assertEquals("hash-da-senha-nova", gabriel.getSenhaHash());
    }

    @Test
    void senhaAtualErradaNaoTrocaASenha() {
        logarComoGabriel();
        when(passwordEncoder.matches("senhaErrada", "hash-da-senha-atual")).thenReturn(false);

        ApiException erro = assertThrows(ApiException.class,
                () -> contaClienteService.trocarSenha(new TrocaSenhaRequest("senhaErrada", "senhaNova2")));

        assertEquals(422, erro.getStatus().value());
        assertEquals("hash-da-senha-atual", gabriel.getSenhaHash());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void novaSenhaIgualAAtualDevolve422() {
        logarComoGabriel();
        when(passwordEncoder.matches("senhaAtual1", "hash-da-senha-atual")).thenReturn(true);

        ApiException erro = assertThrows(ApiException.class,
                () -> contaClienteService.trocarSenha(new TrocaSenhaRequest("senhaAtual1", "senhaAtual1")));

        assertEquals(422, erro.getStatus().value());
        verify(passwordEncoder, never()).encode(any());
    }

    // ---------- exclusão da conta (LGPD) ----------

    @Test
    void excluirContaApagaOClienteETiraOsDadosPessoaisDosAgendamentos() {
        logarComoGabriel();
        when(passwordEncoder.matches("senhaAtual1", "hash-da-senha-atual")).thenReturn(true);
        // atendimento que já aconteceu: continua no histórico da barbearia
        Agendamento concluido = agendamento(LocalDate.of(2026, 11, 6), StatusAgendamento.CONCLUIDO);
        when(agendamentoRepository.findAllByClienteIdOrderByDataDescHoraInicioDesc(gabriel.getId()))
                .thenReturn(List.of(concluido));

        contaClienteService.excluirConta(new ExclusaoContaRequest("senhaAtual1"));

        verify(clienteRepository).delete(gabriel);
        assertNull(concluido.getCliente());
        assertEquals("Cliente removido", concluido.getClienteNome());
        assertEquals("removido", concluido.getClienteTelefone());
        assertEquals(StatusAgendamento.CONCLUIDO, concluido.getStatus());
    }

    @Test
    void excluirContaCancelaOsAgendamentosQueAindaIamAcontecer() {
        logarComoGabriel();
        when(passwordEncoder.matches("senhaAtual1", "hash-da-senha-atual")).thenReturn(true);
        Agendamento aprovadoAmanha = agendamento(LocalDate.of(2026, 11, 11), StatusAgendamento.APROVADO);
        Agendamento pendenteAmanha = agendamento(LocalDate.of(2026, 11, 11), StatusAgendamento.PENDENTE);
        when(agendamentoRepository.findAllByClienteIdOrderByDataDescHoraInicioDesc(gabriel.getId()))
                .thenReturn(List.of(aprovadoAmanha, pendenteAmanha));

        contaClienteService.excluirConta(new ExclusaoContaRequest("senhaAtual1"));

        // cancelados, o horário volta a ficar livre na agenda do profissional
        assertEquals(StatusAgendamento.CANCELADO, aprovadoAmanha.getStatus());
        assertEquals(StatusAgendamento.CANCELADO, pendenteAmanha.getStatus());
        assertEquals("Cliente removido", aprovadoAmanha.getClienteNome());
    }

    @Test
    void excluirContaComSenhaErradaNaoApagaNada() {
        logarComoGabriel();
        when(passwordEncoder.matches("senhaErrada", "hash-da-senha-atual")).thenReturn(false);

        ApiException erro = assertThrows(ApiException.class,
                () -> contaClienteService.excluirConta(new ExclusaoContaRequest("senhaErrada")));

        assertEquals(422, erro.getStatus().value());
        verify(clienteRepository, never()).delete(any());
        verifyNoInteractions(agendamentoRepository);
    }

    // ---------- métodos de apoio ----------

    private void logarComoGabriel() {
        logarComo(gabriel.getId(), "ROLE_CLIENTE");
        when(clienteRepository.findById(gabriel.getId())).thenReturn(Optional.of(gabriel));
    }

    // simula o que o JwtAuthenticationFilter faz com o token
    private void logarComo(UUID usuarioId, String papel) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuarioId.toString(), null, List.of(new SimpleGrantedAuthority(papel))));
    }

    private Agendamento agendamento(LocalDate data, StatusAgendamento status) {
        Agendamento a = new Agendamento();
        a.setId(UUID.randomUUID());
        a.setCliente(gabriel);
        a.setClienteNome("Gabriel Naoki");
        a.setClienteTelefone("62981234567");
        a.setData(data);
        a.setHoraInicio(LocalTime.of(10, 0));
        a.setHoraFim(LocalTime.of(10, 30));
        a.setStatus(status);
        return a;
    }
}
