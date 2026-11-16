package br.com.cadeiralivre.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.tenant.TenantContext;
import br.com.cadeiralivre.api.usuario.Papel;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtAuthenticationFilterTest {

    private static final String CHAVE = "chave-de-teste-com-pelo-menos-32-caracteres";

    private final Clock agora = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService(CHAVE, 60, agora);
    // a sessão vem do banco; aqui o mock responde se ela está aberta
    private final SessaoService sessaoService = mock(SessaoService.class);
    private final JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(jwtService, sessaoService);

    private final UUID sessaoAberta = UUID.randomUUID();

    // o que o resto da aplicação (controller e services) enxergou durante a requisição
    private boolean chegouNoController;
    private Authentication usuarioNaRequisicao;
    private UUID barbeariaNaRequisicao;
    private UUID sessaoNaRequisicao;

    @BeforeEach
    void preparar() {
        when(sessaoService.estaAtiva(sessaoAberta)).thenReturn(true);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void tokenDeDonoIdentificaUsuarioPapelEBarbearia() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID barbeariaId = UUID.randomUUID();
        String token = jwtService.gerar(usuarioId, Papel.DONO, barbeariaId, sessaoAberta);

        enviarComAuthorization("Bearer " + token);

        assertTrue(chegouNoController);
        assertEquals(usuarioId.toString(), usuarioNaRequisicao.getName());
        assertEquals("ROLE_DONO", papelDoUsuario());
        assertEquals(barbeariaId, barbeariaNaRequisicao);
    }

    @Test
    void tokenDeClienteNaoTemBarbearia() throws Exception {
        String token = jwtService.gerar(UUID.randomUUID(), Papel.CLIENTE, null, sessaoAberta);

        enviarComAuthorization("Bearer " + token);

        assertEquals("ROLE_CLIENTE", papelDoUsuario());
        assertNull(barbeariaNaRequisicao);
    }

    @Test
    void sessaoDoTokenFicaGuardadaParaOLogout() throws Exception {
        String token = jwtService.gerar(UUID.randomUUID(), Papel.CLIENTE, null, sessaoAberta);

        enviarComAuthorization("Bearer " + token);

        assertEquals(sessaoAberta, sessaoNaRequisicao);
    }

    @Test
    void tokenDeSessaoEncerradaSegueSemUsuario() throws Exception {
        // RF05: depois do logout, o mesmo token (ainda dentro das 24 horas) não identifica mais ninguém
        UUID sessaoEncerrada = UUID.randomUUID();
        when(sessaoService.estaAtiva(sessaoEncerrada)).thenReturn(false);
        String token = jwtService.gerar(UUID.randomUUID(), Papel.DONO, UUID.randomUUID(), sessaoEncerrada);

        enviarComAuthorization("Bearer " + token);

        assertTrue(chegouNoController);
        assertNull(usuarioNaRequisicao);
        assertNull(barbeariaNaRequisicao);
    }

    @Test
    void tokenSemSessaoSegueSemUsuario() throws Exception {
        // token com a assinatura certa, mas sem sessaoId (como os gerados antes do logout existir)
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("papel", "DONO")
                .claim("barbeariaId", UUID.randomUUID().toString())
                .signWith(Keys.hmacShaKeyFor(CHAVE.getBytes(StandardCharsets.UTF_8)))
                .compact();

        enviarComAuthorization("Bearer " + token);

        assertTrue(chegouNoController);
        assertNull(usuarioNaRequisicao);
        assertNull(barbeariaNaRequisicao);
    }

    @Test
    void semTokenARequisicaoSegueSemUsuario() throws Exception {
        // rota pública (ex.: portal da barbearia) continua funcionando sem login
        enviarComAuthorization(null);

        assertTrue(chegouNoController);
        assertNull(usuarioNaRequisicao);
        assertNull(barbeariaNaRequisicao);
    }

    @Test
    void headerQueNaoEhBearerEhIgnorado() throws Exception {
        enviarComAuthorization("Basic dXN1YXJpbzpzZW5oYQ==");

        assertTrue(chegouNoController);
        assertNull(usuarioNaRequisicao);
    }

    @Test
    void tokenInvalidoSegueSemUsuario() throws Exception {
        // em rota do painel, quem devolve o 401 depois é o SecurityConfig
        enviarComAuthorization("Bearer abc");

        assertTrue(chegouNoController);
        assertNull(usuarioNaRequisicao);
        assertNull(barbeariaNaRequisicao);
    }

    @Test
    void tokenComBarbeariaMalformadaNaoDeixaUsuarioLogadoPelaMetade() throws Exception {
        // token assinado com a chave certa e com sessão aberta, mas com um barbeariaId que não é UUID
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("papel", "DONO")
                .claim("sessaoId", sessaoAberta.toString())
                .claim("barbeariaId", "nao-eh-um-uuid")
                .signWith(Keys.hmacShaKeyFor(CHAVE.getBytes(StandardCharsets.UTF_8)))
                .compact();

        enviarComAuthorization("Bearer " + token);

        assertTrue(chegouNoController);
        assertNull(usuarioNaRequisicao);
        assertNull(barbeariaNaRequisicao);
    }

    @Test
    void usuarioEBarbeariaSaoLimposQuandoARequisicaoTermina() throws Exception {
        // o Tomcat reaproveita as threads: sem limpar, a próxima requisição
        // poderia "herdar" o login e a barbearia da anterior (RNF05)
        String token = jwtService.gerar(UUID.randomUUID(), Papel.DONO, UUID.randomUUID(), sessaoAberta);

        enviarComAuthorization("Bearer " + token);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(barbeariaAtual());
    }

    // passa a requisição pelo filtro; no lugar do controller, anota quem estava logado
    private void enviarComAuthorization(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/painel/agendamentos");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }

        filtro.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            chegouNoController = true;
            usuarioNaRequisicao = SecurityContextHolder.getContext().getAuthentication();
            barbeariaNaRequisicao = barbeariaAtual();
            sessaoNaRequisicao = UsuarioLogado.sessaoId();
        });
    }

    private String papelDoUsuario() {
        return usuarioNaRequisicao.getAuthorities().iterator().next().getAuthority();
    }

    // o TenantContext só tem o require(), que dá erro quando não há barbearia
    private UUID barbeariaAtual() {
        try {
            return TenantContext.require();
        } catch (ApiException e) {
            return null;
        }
    }
}
