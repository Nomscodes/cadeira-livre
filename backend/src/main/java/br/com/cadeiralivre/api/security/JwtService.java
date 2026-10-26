package br.com.cadeiralivre.api.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.cadeiralivre.api.usuario.Papel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Gera e valida o JWT. Claims: sub = id do usuário, papel e barbeariaId (só para DONO). */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiracaoMinutos;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiracao-minutos}") long expiracaoMinutos) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMinutos = expiracaoMinutos;
    }

    public String gerar(UUID usuarioId, Papel papel, UUID barbeariaId) {
        Instant agora = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(usuarioId.toString())
                .claim("papel", papel.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracaoMinutos, ChronoUnit.MINUTES)));
        if (barbeariaId != null) builder.claim("barbeariaId", barbeariaId.toString());
        return builder.signWith(key).compact();
    }

    /** Lança JwtException se o token for inválido ou estiver expirado. */
    public Claims validar(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
