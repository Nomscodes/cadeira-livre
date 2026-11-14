package br.com.cadeiralivre.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.cadeiralivre.api.auth.dto.CadastroClienteRequest;
import br.com.cadeiralivre.api.auth.dto.CadastroDonoRequest;
import br.com.cadeiralivre.api.auth.dto.LoginRequest;
import br.com.cadeiralivre.api.auth.dto.SessaoResponse;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaService;
import br.com.cadeiralivre.api.common.cripto.Criptografia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.security.JwtService;
import br.com.cadeiralivre.api.security.Sessao;
import br.com.cadeiralivre.api.security.SessaoService;
import br.com.cadeiralivre.api.usuario.Cliente;
import br.com.cadeiralivre.api.usuario.DonoBarbearia;
import br.com.cadeiralivre.api.usuario.Papel;
import br.com.cadeiralivre.api.usuario.Usuario;
import br.com.cadeiralivre.api.usuario.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private BarbeariaService barbeariaService;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private SessaoService sessaoService;

    // a criptografia é a de verdade, com uma chave de teste
    private final Criptografia criptografia = new Criptografia("Y2hhdmUtZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW9rISE=");

    private final Instant agora = Instant.parse("2026-11-10T12:00:00Z");

    private AuthService authService;

    // a sessão que o login abre no banco (RF05)
    private Sessao sessaoAberta;

    @BeforeEach
    void preparar() {
        // a Criptografia e o Clock não são mocks, por isso o service é criado na mão
        authService = new AuthService(usuarioRepository, barbeariaService, profissionalRepository, passwordEncoder,
                jwtService, sessaoService, criptografia, Clock.fixed(agora, ZoneOffset.UTC));

        sessaoAberta = new Sessao();
        sessaoAberta.setId(UUID.randomUUID());
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void cadastroDeClienteComEmailJaUsadoDevolve409() {
        when(usuarioRepository.existsByEmailHash(criptografia.hash("cliente@teste.com"))).thenReturn(true);

        ApiException erro = assertThrows(ApiException.class,
                () -> authService.cadastrarCliente(novoCliente("cliente@teste.com")));

        assertEquals(409, erro.getStatus().value());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cadastroDeClienteGuardaHashDoEmailSenhaCriptografadaEDataDoAceite() {
        String hashEsperado = criptografia.hash("cliente@teste.com");
        when(usuarioRepository.existsByEmailHash(hashEsperado)).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("senha-criptografada");
        when(sessaoService.abrir(any())).thenReturn(sessaoAberta);
        when(jwtService.gerar(any(), eq(Papel.CLIENTE), isNull(), eq(sessaoAberta.getId()))).thenReturn("token-falso");

        SessaoResponse sessao = authService.cadastrarCliente(novoCliente("  Cliente@Teste.com "));

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(salvo.capture());
        // no Java o e-mail fica normal; quem criptografa ao gravar no banco é o CriptografiaConverter
        assertEquals("cliente@teste.com", salvo.getValue().getEmail());
        assertEquals(hashEsperado, salvo.getValue().getEmailHash());
        assertEquals("senha-criptografada", salvo.getValue().getSenhaHash());
        assertEquals(agora, salvo.getValue().getAceiteTermosEm());
        assertEquals("token-falso", sessao.token());
        assertEquals("CLIENTE", sessao.usuario().papel());
    }

    @Test
    void cadastroDeDonoCriaBarbeariaEDevolveTokenComABarbearia() {
        Barbearia barbearia = new Barbearia();
        barbearia.setId(UUID.randomUUID());
        when(barbeariaService.criar("Barbearia Teste", "12345678901")).thenReturn(barbearia);
        when(sessaoService.abrir(any())).thenReturn(sessaoAberta);
        when(jwtService.gerar(any(), eq(Papel.DONO), eq(barbearia.getId()), eq(sessaoAberta.getId()))).thenReturn("token-falso");

        SessaoResponse sessao = authService.cadastrarDono(new CadastroDonoRequest(
                "Dono Teste", "Barbearia Teste", "12345678901", "dono@teste.com", "12345678", true));

        assertEquals("DONO", sessao.usuario().papel());
        assertEquals(barbearia.getId(), sessao.usuario().barbeariaId());
    }

    @Test
    void donoAutonomoViraProfissionalAdministradorQueAtende() {
        Barbearia barbearia = new Barbearia(); // atuação padrão: autonomo
        when(barbeariaService.criar("Barbearia Naoki", "12345678901")).thenReturn(barbearia);
        when(sessaoService.abrir(any())).thenReturn(sessaoAberta);

        authService.cadastrarDono(new CadastroDonoRequest(
                "Gabriel Naoki", "Barbearia Naoki", "12345678901", "naoki@teste.com", "12345678", true));

        ArgumentCaptor<Usuario> dono = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(dono.capture());
        ArgumentCaptor<Profissional> salvo = ArgumentCaptor.forClass(Profissional.class);
        verify(profissionalRepository).save(salvo.capture());
        Profissional profissional = salvo.getValue();
        assertSame(barbearia, profissional.getBarbearia());
        assertSame(dono.getValue(), profissional.getUsuario()); // o login do dono fica ligado ao profissional
        assertEquals("Gabriel Naoki", profissional.getNome());
        assertEquals("Gabriel", profissional.getNomeSocial());
        assertTrue(profissional.isAdministrador());
        assertTrue(profissional.isAtende());
    }

    @Test
    void donoDeSalaoComEquipeViraAdministradorQueNaoAtende() {
        Barbearia barbearia = new Barbearia();
        barbearia.setAtuacao("equipe");
        when(barbeariaService.criar("Barbearia Naoki", "12345678901")).thenReturn(barbearia);
        when(sessaoService.abrir(any())).thenReturn(sessaoAberta);

        authService.cadastrarDono(new CadastroDonoRequest(
                "Gabriel Naoki", "Barbearia Naoki", "12345678901", "naoki@teste.com", "12345678", true));

        ArgumentCaptor<Profissional> salvo = ArgumentCaptor.forClass(Profissional.class);
        verify(profissionalRepository).save(salvo.capture());
        assertTrue(salvo.getValue().isAdministrador());
        assertFalse(salvo.getValue().isAtende());
        assertTrue(salvo.getValue().getUsuario() instanceof DonoBarbearia);
    }

    @Test
    void cadastroDeDonoComEmailJaUsadoNaoCriaProfissional() {
        when(usuarioRepository.existsByEmailHash(criptografia.hash("naoki@teste.com"))).thenReturn(true);

        assertThrows(ApiException.class, () -> authService.cadastrarDono(new CadastroDonoRequest(
                "Gabriel Naoki", "Barbearia Naoki", "12345678901", "naoki@teste.com", "12345678", true)));

        verify(profissionalRepository, never()).save(any());
    }

    @Test
    void cadastroDeClienteNaoCriaProfissional() {
        when(sessaoService.abrir(any())).thenReturn(sessaoAberta);

        authService.cadastrarCliente(novoCliente("cliente@teste.com"));

        verify(profissionalRepository, never()).save(any());
    }

    @Test
    void loginAceitaEmailComMaiusculasEEspacos() {
        Cliente cliente = new Cliente();
        cliente.setSenhaHash("hash-salvo");
        when(usuarioRepository.findByEmailHash(criptografia.hash("cliente@teste.com"))).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("12345678", "hash-salvo")).thenReturn(true);
        when(sessaoService.abrir(cliente)).thenReturn(sessaoAberta);
        when(jwtService.gerar(any(), eq(Papel.CLIENTE), isNull(), eq(sessaoAberta.getId()))).thenReturn("token-falso");

        SessaoResponse sessao = authService.login(new LoginRequest("  Cliente@Teste.COM ", "12345678"));

        // o token devolvido é o que leva o id da sessão aberta neste login
        assertEquals("token-falso", sessao.token());
    }

    @Test
    void loginComSenhaErradaDevolve401() {
        Cliente cliente = new Cliente();
        cliente.setSenhaHash("hash-salvo");
        when(usuarioRepository.findByEmailHash(criptografia.hash("cliente@teste.com"))).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("senha-errada", "hash-salvo")).thenReturn(false);

        ApiException erro = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("cliente@teste.com", "senha-errada")));

        assertEquals(401, erro.getStatus().value());
        verify(sessaoService, never()).abrir(any());
        verify(jwtService, never()).gerar(any(), any(), any(), any());
    }

    @Test
    void loginComEmailInexistenteDevolve401() {
        when(usuarioRepository.findByEmailHash(criptografia.hash("ninguem@teste.com"))).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("ninguem@teste.com", "12345678")));

        // mesma resposta da senha errada: não revela se o e-mail existe
        assertEquals(401, erro.getStatus().value());
    }

    @Test
    void logoutEncerraASessaoDoTokenUsado() {
        UUID gabrielId = UUID.randomUUID();
        UUID sessaoDoToken = UUID.randomUUID();
        logarComo(gabrielId, sessaoDoToken);

        authService.logout();

        verify(sessaoService).encerrar(sessaoDoToken);
    }

    @Test
    void logoutSemLoginDevolve401() {
        ApiException erro = assertThrows(ApiException.class, () -> authService.logout());

        assertEquals(401, erro.getStatus().value());
        verify(sessaoService, never()).encerrar(any());
    }

    // simula o que o JwtAuthenticationFilter faz com o token: usuário no "nome" e sessão nos "detalhes"
    private void logarComo(UUID usuarioId, UUID sessaoId) {
        var auth = new UsernamePasswordAuthenticationToken(
                usuarioId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));
        auth.setDetails(sessaoId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CadastroClienteRequest novoCliente(String email) {
        return new CadastroClienteRequest("Cliente Teste", email, "62999999999", "12345678", true);
    }
}
