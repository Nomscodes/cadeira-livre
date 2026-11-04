package br.com.cadeiralivre.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.cadeiralivre.api.usuario.Papel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String CHAVE = "chave-de-teste-com-pelo-menos-32-caracteres";

    private final JwtService jwtService = new JwtService(CHAVE, 60);

    @Test
    void tokenDeDonoGuardaUsuarioPapelEBarbearia() {
        UUID usuarioId = UUID.randomUUID();
        UUID barbeariaId = UUID.randomUUID();

        String token = jwtService.gerar(usuarioId, Papel.DONO, barbeariaId);
        Claims claims = jwtService.validar(token);

        assertEquals(usuarioId.toString(), claims.getSubject());
        assertEquals("DONO", claims.get("papel", String.class));
        assertEquals(barbeariaId.toString(), claims.get("barbeariaId", String.class));
    }

    @Test
    void tokenDeClienteNaoTemBarbearia() {
        String token = jwtService.gerar(UUID.randomUUID(), Papel.CLIENTE, null);

        assertNull(jwtService.validar(token).get("barbeariaId"));
    }

    @Test
    void tokenAssinadoComOutraChaveEhRecusado() {
        JwtService outroServidor = new JwtService("outra-chave-secreta-com-mais-de-32-caracteres", 60);
        String tokenFalso = outroServidor.gerar(UUID.randomUUID(), Papel.DONO, UUID.randomUUID());

        assertThrows(JwtException.class, () -> jwtService.validar(tokenFalso));
    }

    @Test
    void tokenExpiradoEhRecusado() {
        // expiração negativa: o token já nasce vencido
        JwtService geraTokenVencido = new JwtService(CHAVE, -1);
        String tokenVencido = geraTokenVencido.gerar(UUID.randomUUID(), Papel.CLIENTE, null);

        assertThrows(ExpiredJwtException.class, () -> jwtService.validar(tokenVencido));
    }

    @Test
    void textoQueNaoEhTokenEhRecusado() {
        assertThrows(JwtException.class, () -> jwtService.validar("abc"));
    }
}
