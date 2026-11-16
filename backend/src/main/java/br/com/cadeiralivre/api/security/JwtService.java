package br.com.cadeiralivre.api.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
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

/** Gera e valida o JWT. Claims: sub = id do usuário, papel, sessaoId e barbeariaId (só para DONO). */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expiracaoMinutos;
    private final Clock clock;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiracao-minutos}") long expiracaoMinutos,
                      Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMinutos = expiracaoMinutos;
        this.clock = clock;
    }

    public String gerar(UUID usuarioId, Papel papel, UUID barbeariaId, UUID sessaoId) {
        Instant agora = Instant.now(clock);
        JwtBuilder builder = Jwts.builder()
                .subject(usuarioId.toString())
                .claim("papel", papel.name())
                // RF05: o filtro confere em toda requisição se esta sessão ainda está aberta
                .claim("sessaoId", sessaoId.toString())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(expiracaoMinutos, ChronoUnit.MINUTES)));
        if (barbeariaId != null) builder.claim("barbeariaId", barbeariaId.toString());
        return builder.signWith(key).compact();
    }

    /** Lança JwtException se o token for inválido ou estiver expirado. */
    public Claims validar(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))  // usa o mesmo relógio da geração
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
