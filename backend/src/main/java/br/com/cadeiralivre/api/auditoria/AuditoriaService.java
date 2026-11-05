package br.com.cadeiralivre.api.auditoria;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/** Registra as ações críticas do sistema (RNF09). */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;
    private final Clock clock;

    @Transactional
    public void registrar(AcaoAuditoria acao, String detalhe) {
        LogAuditoria log = new LogAuditoria();
        log.setDataHora(Instant.now(clock));
        log.setUsuarioId(usuarioLogado());
        log.setBarbeariaId(TenantContext.require());
        log.setAcao(acao);
        log.setDetalhe(detalhe);
        logAuditoriaRepository.save(log);
    }

    // o JwtAuthenticationFilter guarda o id do usuário (subject do token) como "nome" da autenticação
    private UUID usuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? UUID.fromString(auth.getName()) : null;
    }
}
