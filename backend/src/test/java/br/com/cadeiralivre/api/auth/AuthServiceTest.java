package br.com.cadeiralivre.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.cadeiralivre.api.auth.dto.CadastroClienteRequest;
import br.com.cadeiralivre.api.auth.dto.CadastroDonoRequest;
import br.com.cadeiralivre.api.auth.dto.LoginRequest;
import br.com.cadeiralivre.api.auth.dto.SessaoResponse;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaService;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.security.JwtService;
import br.com.cadeiralivre.api.usuario.Cliente;
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
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void cadastroDeClienteComEmailJaUsadoDevolve409() {
        when(usuarioRepository.existsByEmail("cliente@teste.com")).thenReturn(true);

        ApiException erro = assertThrows(ApiException.class,
                () -> authService.cadastrarCliente(novoCliente("cliente@teste.com")));

        assertEquals(409, erro.getStatus().value());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cadastroDeClienteSalvaEmailMinusculoESenhaCriptografada() {
        when(usuarioRepository.existsByEmail("cliente@teste.com")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("senha-criptografada");
        when(jwtService.gerar(any(), eq(Papel.CLIENTE), isNull())).thenReturn("token-falso");

        SessaoResponse sessao = authService.cadastrarCliente(novoCliente("  Cliente@Teste.com "));

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(salvo.capture());
        assertEquals("cliente@teste.com", salvo.getValue().getEmail());
        assertEquals("senha-criptografada", salvo.getValue().getSenhaHash());
        assertEquals("token-falso", sessao.token());
        assertEquals("CLIENTE", sessao.usuario().papel());
    }

    @Test
    void cadastroDeDonoCriaBarbeariaEDevolveTokenComABarbearia() {
        Barbearia barbearia = new Barbearia();
        barbearia.setId(UUID.randomUUID());
        when(barbeariaService.criar("Barbearia Teste", "12345678901")).thenReturn(barbearia);
        when(jwtService.gerar(any(), eq(Papel.DONO), eq(barbearia.getId()))).thenReturn("token-falso");

        SessaoResponse sessao = authService.cadastrarDono(new CadastroDonoRequest(
                "Dono Teste", "Barbearia Teste", "12345678901", "dono@teste.com", "12345678"));

        assertEquals("DONO", sessao.usuario().papel());
        assertEquals(barbearia.getId(), sessao.usuario().barbeariaId());
    }

    @Test
    void loginComSenhaErradaDevolve401() {
        Cliente cliente = new Cliente();
        cliente.setSenhaHash("hash-salvo");
        when(usuarioRepository.findByEmail("cliente@teste.com")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.matches("senha-errada", "hash-salvo")).thenReturn(false);

        ApiException erro = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("cliente@teste.com", "senha-errada")));

        assertEquals(401, erro.getStatus().value());
        verify(jwtService, never()).gerar(any(), any(), any());
    }

    @Test
    void loginComEmailInexistenteDevolve401() {
        when(usuarioRepository.findByEmail("ninguem@teste.com")).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("ninguem@teste.com", "12345678")));

        // mesma resposta da senha errada: não revela se o e-mail existe
        assertEquals(401, erro.getStatus().value());
    }

    private CadastroClienteRequest novoCliente(String email) {
        return new CadastroClienteRequest("Cliente Teste", email, "62999999999", "12345678");
    }
}
