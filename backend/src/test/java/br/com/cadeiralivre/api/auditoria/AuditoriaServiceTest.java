package br.com.cadeiralivre.api.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private LogAuditoriaRepository logAuditoriaRepository;

    private final Instant agora = Instant.parse("2026-11-10T12:00:00Z");

    @AfterEach
    void limpar() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void registroGuardaQuemFezQuandoEmQualBarbeariaEOQue() {
        UUID usuarioId = UUID.randomUUID();
        UUID barbeariaId = UUID.randomUUID();
        // simula o que o JwtAuthenticationFilter faz com o token do dono
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuarioId.toString(), null, List.of()));
        TenantContext.set(barbeariaId);
        AuditoriaService auditoriaService = new AuditoriaService(logAuditoriaRepository, Clock.fixed(agora, ZoneOffset.UTC));

        auditoriaService.registrar(AcaoAuditoria.PORTAL_ALTERADO, "Nome: Barbearia Teste");

        ArgumentCaptor<LogAuditoria> salvo = ArgumentCaptor.forClass(LogAuditoria.class);
        verify(logAuditoriaRepository).save(salvo.capture());
        assertEquals(usuarioId, salvo.getValue().getUsuarioId());
        assertEquals(barbeariaId, salvo.getValue().getBarbeariaId());
        assertEquals(agora, salvo.getValue().getDataHora());
        assertEquals(AcaoAuditoria.PORTAL_ALTERADO, salvo.getValue().getAcao());
        assertEquals("Nome: Barbearia Teste", salvo.getValue().getDetalhe());
    }
}
