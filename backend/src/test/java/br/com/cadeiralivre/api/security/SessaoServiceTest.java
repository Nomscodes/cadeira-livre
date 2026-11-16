package br.com.cadeiralivre.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.usuario.Cliente;

@ExtendWith(MockitoExtension.class)
class SessaoServiceTest {

    @Mock
    private SessaoRepository sessaoRepository;

    // "agora" fixo nos testes
    private final Instant agora = Instant.parse("2026-11-10T12:00:00Z");

    private SessaoService sessaoService;
    private Cliente gabriel;

    @BeforeEach
    void preparar() {
        // o Clock não é mock e a duração vem do application.yaml, por isso o service é criado na mão
        sessaoService = new SessaoService(sessaoRepository, Clock.fixed(agora, ZoneOffset.UTC), 1440);

        gabriel = new Cliente();
        gabriel.setId(UUID.randomUUID());
        gabriel.setNome("Gabriel Naoki");
    }

    @Test
    void loginAbreSessaoQueVenceJuntoComOToken() {
        when(sessaoRepository.save(any(Sessao.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Sessao sessao = sessaoService.abrir(gabriel);

        assertSame(gabriel, sessao.getUsuario());
        assertEquals(agora, sessao.getCriadaEm());
        assertEquals(Instant.parse("2026-11-11T12:00:00Z"), sessao.getExpiraEm()); // 24 horas depois
        assertNull(sessao.getEncerradaEm());
    }

    @Test
    void sessaoSoEstaAtivaSeNinguemFezLogoutComEla() {
        UUID aberta = UUID.randomUUID();
        UUID encerrada = UUID.randomUUID();
        when(sessaoRepository.existsByIdAndEncerradaEmIsNull(aberta)).thenReturn(true);
        when(sessaoRepository.existsByIdAndEncerradaEmIsNull(encerrada)).thenReturn(false);

        assertTrue(sessaoService.estaAtiva(aberta));
        assertFalse(sessaoService.estaAtiva(encerrada));
    }

    @Test
    void logoutGuardaAHoraEmQueASessaoFoiEncerrada() {
        Sessao sessao = sessaoAberta();
        when(sessaoRepository.findById(sessao.getId())).thenReturn(Optional.of(sessao));

        sessaoService.encerrar(sessao.getId());

        assertEquals(agora, sessao.getEncerradaEm());
    }

    @Test
    void logoutDeSessaoJaEncerradaNaoMudaAData() {
        Sessao sessao = sessaoAberta();
        Instant primeiroLogout = agora.minusSeconds(600);
        sessao.setEncerradaEm(primeiroLogout);
        when(sessaoRepository.findById(sessao.getId())).thenReturn(Optional.of(sessao));

        sessaoService.encerrar(sessao.getId());

        assertEquals(primeiroLogout, sessao.getEncerradaEm());
    }

    @Test
    void limpezaDiariaApagaSoAsSessoesQueJaVenceram() {
        sessaoService.apagarVencidas();

        // vencida = expira_em antes de "agora"
        verify(sessaoRepository).deleteByExpiraEmBefore(agora);
    }

    private Sessao sessaoAberta() {
        Sessao sessao = new Sessao();
        sessao.setId(UUID.randomUUID());
        sessao.setUsuario(gabriel);
        sessao.setCriadaEm(agora.minusSeconds(3600));
        sessao.setExpiraEm(agora.plusSeconds(3600));
        return sessao;
    }
}
