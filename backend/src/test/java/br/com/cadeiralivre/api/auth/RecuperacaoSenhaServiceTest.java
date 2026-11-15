package br.com.cadeiralivre.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.cadeiralivre.api.common.cripto.Criptografia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.security.SessaoRepository;
import br.com.cadeiralivre.api.usuario.Cliente;
import br.com.cadeiralivre.api.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RecuperacaoSenhaRepository recuperacaoRepository;
    @Mock
    private SessaoRepository sessaoRepository;

    // o encoder e a criptografia são os de verdade: o código precisa bater com o hash
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final Criptografia criptografia = new Criptografia("Y2hhdmUtZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW9rISE=");
    private final Instant agora = Instant.parse("2026-11-14T13:00:00Z");

    private RecuperacaoSenhaService service;
    private Cliente gabriel;

    @BeforeEach
    void preparar() {
        service = new RecuperacaoSenhaService(usuarioRepository, recuperacaoRepository, sessaoRepository,
                passwordEncoder, criptografia, Clock.fixed(agora, ZoneOffset.UTC), false);
        gabriel = new Cliente();
        gabriel.setId(UUID.randomUUID());
        gabriel.setNome("Gabriel Naoki");
        gabriel.setSenhaHash("senha-antiga");
    }

    // ---------- passo 1: pedir o código ----------

    @Test
    void pedirCodigoGuardaSoOHashComValidadeDe15Minutos() {
        when(usuarioRepository.findByEmailHash(criptografia.hash("gabriel@teste.com"))).thenReturn(Optional.of(gabriel));

        // o e-mail pode vir com maiúsculas e espaços, como no login
        service.solicitar("  Gabriel@Teste.com ");

        ArgumentCaptor<RecuperacaoSenha> salvo = ArgumentCaptor.forClass(RecuperacaoSenha.class);
        verify(recuperacaoRepository).save(salvo.capture());
        RecuperacaoSenha pedido = salvo.getValue();
        assertSame(gabriel, pedido.getUsuario());
        assertTrue(pedido.getCodigoHash().startsWith("$2")); // hash BCrypt, nunca o código puro
        assertEquals(agora.plusSeconds(15 * 60), pedido.getExpiraEm());
        assertEquals(0, pedido.getTentativas());
        assertNull(pedido.getUsadaEm());
    }

    @Test
    void emailNaoCadastradoDevolve404() {
        when(usuarioRepository.findByEmailHash(criptografia.hash("ninguem@teste.com"))).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> service.solicitar("ninguem@teste.com"));

        assertEquals(404, erro.getStatus().value());
        assertEquals("Este e-mail não está cadastrado.", erro.getMessage());
        verify(recuperacaoRepository, never()).save(any());
    }

    // ---------- passo 2: conferir o código ----------

    @Test
    void codigoCertoPassa() {
        pedidoAtivo("123456");

        service.conferir("gabriel@teste.com", " 123456 ");
    }

    @Test
    void codigoErradoSomaUmaTentativa() {
        RecuperacaoSenha pedido = pedidoAtivo("123456");

        ApiException erro = assertThrows(ApiException.class, () -> service.conferir("gabriel@teste.com", "999999"));

        assertEquals(422, erro.getStatus().value());
        assertEquals("Código incorreto. Confira o e-mail e tente de novo.", erro.getMessage());
        assertEquals(1, pedido.getTentativas());
    }

    @Test
    void depoisDe5TentativasNemOCodigoCertoVale() {
        RecuperacaoSenha pedido = pedidoAtivo("123456");
        pedido.setTentativas(5);

        ApiException erro = assertThrows(ApiException.class, () -> service.conferir("gabriel@teste.com", "123456"));

        assertEquals("Este código não vale mais. Peça um novo código.", erro.getMessage());
    }

    @Test
    void codigoVencidoNaoVale() {
        RecuperacaoSenha pedido = pedidoAtivo("123456");
        pedido.setExpiraEm(agora.minusSeconds(1));

        assertThrows(ApiException.class, () -> service.conferir("gabriel@teste.com", "123456"));
    }

    // ---------- passo 3: nova senha ----------

    @Test
    void redefinirTrocaASenhaUsaOCodigoEEncerraAsSessoes() {
        RecuperacaoSenha pedido = pedidoAtivo("123456");

        service.redefinir("gabriel@teste.com", "123456", "novaSenha123");

        assertTrue(passwordEncoder.matches("novaSenha123", gabriel.getSenhaHash()));
        assertNotNull(pedido.getUsadaEm());
        verify(sessaoRepository).encerrarTodasDoUsuario(gabriel.getId(), agora);
    }

    @Test
    void codigoJaUsadoNaoTrocaASenhaDeNovo() {
        RecuperacaoSenha pedido = pedidoAtivo("123456");
        pedido.setUsadaEm(agora.minusSeconds(60));

        assertThrows(ApiException.class, () -> service.redefinir("gabriel@teste.com", "123456", "outraSenha123"));

        assertEquals("senha-antiga", gabriel.getSenhaHash());
        verify(sessaoRepository, never()).encerrarTodasDoUsuario(any(), any());
    }

    // pedido feito há pouco, ainda valendo, com o código informado
    private RecuperacaoSenha pedidoAtivo(String codigo) {
        RecuperacaoSenha pedido = new RecuperacaoSenha();
        pedido.setUsuario(gabriel);
        pedido.setCodigoHash(passwordEncoder.encode(codigo));
        pedido.setCriadaEm(agora.minusSeconds(60));
        pedido.setExpiraEm(agora.plusSeconds(14 * 60));
        when(usuarioRepository.findByEmailHash(criptografia.hash("gabriel@teste.com"))).thenReturn(Optional.of(gabriel));
        when(recuperacaoRepository.findFirstByUsuarioIdOrderByCriadaEmDesc(gabriel.getId())).thenReturn(Optional.of(pedido));
        return pedido;
    }
}
