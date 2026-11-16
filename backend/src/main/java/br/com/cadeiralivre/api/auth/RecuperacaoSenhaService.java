package br.com.cadeiralivre.api.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.common.cripto.Criptografia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.security.SessaoRepository;
import br.com.cadeiralivre.api.usuario.Usuario;
import br.com.cadeiralivre.api.usuario.UsuarioRepository;

/**
 * Recuperação de senha em 3 passos, como a tela do front: pedir o código pelo e-mail, conferir o código
 * e definir a nova senha. O envio do código por e-mail está fora do escopo da V7; em desenvolvimento
 * o código aparece no log da API (app.recuperacao.mostrar-codigo-no-log).
 */
@Service
public class RecuperacaoSenhaService {

    static final int LIMITE_DE_TENTATIVAS = 5;
    static final Duration VALIDADE = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final SecureRandom SORTEIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final RecuperacaoSenhaRepository recuperacaoRepository;
    private final SessaoRepository sessaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final Criptografia criptografia;
    private final Clock clock;
    private final boolean mostrarCodigoNoLog;

    public RecuperacaoSenhaService(UsuarioRepository usuarioRepository, RecuperacaoSenhaRepository recuperacaoRepository,
                                   SessaoRepository sessaoRepository, PasswordEncoder passwordEncoder,
                                   Criptografia criptografia, Clock clock,
                                   @Value("${app.recuperacao.mostrar-codigo-no-log:false}") boolean mostrarCodigoNoLog) {
        this.usuarioRepository = usuarioRepository;
        this.recuperacaoRepository = recuperacaoRepository;
        this.sessaoRepository = sessaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.criptografia = criptografia;
        this.clock = clock;
        this.mostrarCodigoNoLog = mostrarCodigoNoLog;
    }

    /** Passo 1: gera um código de 6 dígitos para a conta do e-mail (pedir de novo substitui o anterior). */
    @Transactional
    public void solicitar(String email) {
        // o contrato do front responde 404 para e-mail que não existe (a tela oferece o cadastro)
        Usuario usuario = usuarioRepository.findByEmailHash(hashDoEmail(email))
                .orElseThrow(() -> ApiException.naoEncontrado("Este e-mail não está cadastrado."));

        String codigo = String.format("%06d", SORTEIO.nextInt(1_000_000));
        Instant agora = Instant.now(clock);
        RecuperacaoSenha pedido = new RecuperacaoSenha();
        pedido.setUsuario(usuario);
        pedido.setCodigoHash(passwordEncoder.encode(codigo));
        pedido.setCriadaEm(agora);
        pedido.setExpiraEm(agora.plus(VALIDADE));
        recuperacaoRepository.save(pedido);

        // aqui entraria o envio por e-mail; o log nunca leva o e-mail, só o id da conta
        if (mostrarCodigoNoLog) {
            log.info("[DEV] Código de recuperação de senha da conta {}: {}", usuario.getId(), codigo);
        } else {
            log.info("Código de recuperação de senha gerado para a conta {}.", usuario.getId());
        }
    }

    /**
     * Passo 2: confere o código. O erro também é gravado (conta a tentativa), por isso a transação
     * não é desfeita quando a ApiException sai daqui.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void conferir(String email, String codigo) {
        pedidoConferido(email, codigo);
    }

    /** Passo 3: troca a senha, marca o código como usado e encerra as sessões abertas da conta. */
    @Transactional(noRollbackFor = ApiException.class)
    public void redefinir(String email, String codigo, String novaSenha) {
        RecuperacaoSenha pedido = pedidoConferido(email, codigo);
        Instant agora = Instant.now(clock);
        Usuario usuario = pedido.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        pedido.setUsadaEm(agora);
        sessaoRepository.encerrarTodasDoUsuario(usuario.getId(), agora);
    }

    // o pedido mais recente da conta, se o código bater; código errado soma uma tentativa
    private RecuperacaoSenha pedidoConferido(String email, String codigo) {
        Instant agora = Instant.now(clock);
        RecuperacaoSenha pedido = usuarioRepository.findByEmailHash(hashDoEmail(email))
                .flatMap(u -> recuperacaoRepository.findFirstByUsuarioIdOrderByCriadaEmDesc(u.getId()))
                .orElseThrow(() -> ApiException.invalido("Código incorreto. Confira o e-mail e tente de novo."));
        if (!pedido.valeAinda(agora, LIMITE_DE_TENTATIVAS)) {
            throw ApiException.invalido("Este código não vale mais. Peça um novo código.");
        }
        String digitado = codigo == null ? "" : codigo.trim();
        if (!passwordEncoder.matches(digitado, pedido.getCodigoHash())) {
            pedido.setTentativas(pedido.getTentativas() + 1);
            throw ApiException.invalido("Código incorreto. Confira o e-mail e tente de novo.");
        }
        return pedido;
    }

    private String hashDoEmail(String email) {
        return criptografia.hash(email == null ? "" : email.trim().toLowerCase());
    }
}
