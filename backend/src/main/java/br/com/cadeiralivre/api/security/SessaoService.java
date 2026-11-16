package br.com.cadeiralivre.api.security;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.usuario.Usuario;

/**
 * Sessões de login (RF04 e RF05). Sozinho, o JWT não tem como "deslogar": ele vale até vencer.
 * Por isso cada login abre uma sessão no banco, o token leva o id dela e, em toda requisição,
 * o filtro do JWT confere se a sessão continua aberta. O logout encerra a sessão e o token para de valer.
 */
@Service
public class SessaoService {

    private static final Logger log = LoggerFactory.getLogger(SessaoService.class);

    private final SessaoRepository sessaoRepository;
    private final Clock clock;
    private final long duracaoMinutos;

    public SessaoService(SessaoRepository sessaoRepository, Clock clock,
                         @Value("${app.jwt.expiracao-minutos}") long duracaoMinutos) {
        this.sessaoRepository = sessaoRepository;
        this.clock = clock;
        this.duracaoMinutos = duracaoMinutos;
    }

    /** Login e cadastro: abre uma sessão nova, que vence junto com o token (24 horas). */
    @Transactional
    public Sessao abrir(Usuario usuario) {
        Instant agora = Instant.now(clock);
        Sessao sessao = new Sessao();
        sessao.setUsuario(usuario);
        sessao.setCriadaEm(agora);
        sessao.setExpiraEm(agora.plus(duracaoMinutos, ChronoUnit.MINUTES));
        return sessaoRepository.save(sessao);
    }

    /** O filtro do JWT pergunta isso em toda requisição que chega com token. */
    @Transactional(readOnly = true)
    public boolean estaAtiva(UUID sessaoId) {
        return sessaoRepository.existsByIdAndEncerradaEmIsNull(sessaoId);
    }

    /** Logout (RF05): marca a sessão como encerrada. A data fica guardada como registro de quando o usuário saiu. */
    @Transactional
    public void encerrar(UUID sessaoId) {
        sessaoRepository.findById(sessaoId)
                .filter(sessao -> sessao.getEncerradaEm() == null)
                .ifPresent(sessao -> sessao.setEncerradaEm(Instant.now(clock)));
    }

    /** Todo dia às 3h apaga as sessões que já venceram, para a tabela não crescer a cada login. */
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void apagarVencidas() {
        long apagadas = sessaoRepository.deleteByExpiraEmBefore(Instant.now(clock));
        if (apagadas > 0) {
            log.info("Sessões vencidas apagadas: {}.", apagadas);
        }
    }
}
