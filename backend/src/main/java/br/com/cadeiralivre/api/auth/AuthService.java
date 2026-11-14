package br.com.cadeiralivre.api.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.auth.dto.CadastroClienteRequest;
import br.com.cadeiralivre.api.auth.dto.CadastroDonoRequest;
import br.com.cadeiralivre.api.auth.dto.LoginRequest;
import br.com.cadeiralivre.api.auth.dto.SessaoResponse;
import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaService;
import br.com.cadeiralivre.api.common.cripto.Criptografia;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalHorario;
import br.com.cadeiralivre.api.profissional.ProfissionalHorarioRepository;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.security.JwtService;
import br.com.cadeiralivre.api.security.Sessao;
import br.com.cadeiralivre.api.security.SessaoService;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import br.com.cadeiralivre.api.usuario.Cliente;
import br.com.cadeiralivre.api.usuario.DonoBarbearia;
import br.com.cadeiralivre.api.usuario.Usuario;
import br.com.cadeiralivre.api.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;

/** Regras de cadastro (RF01–RF03), login (RF04), logout (RF05) e aceite dos termos (RNF02). */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final BarbeariaService barbeariaService;
    private final ProfissionalRepository profissionalRepository;
    private final ProfissionalHorarioRepository profissionalHorarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessaoService sessaoService;
    private final Criptografia criptografia;
    private final Clock clock;

    // não é readOnly porque o login grava a sessão nova no banco
    @Transactional
    public SessaoResponse login(LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmailHash(hashDoEmail(req.email()))
                .filter(u -> passwordEncoder.matches(req.senha(), u.getSenhaHash()))
                .orElseThrow(() -> ApiException.naoAutorizado("E-mail ou senha incorretos."));
        return sessao(usuario);
    }

    /** RF05: encerra a sessão do token usado na requisição. A partir daí esse token não vale mais. */
    public void logout() {
        UUID sessaoId = UsuarioLogado.sessaoId();
        if (sessaoId == null) throw ApiException.naoAutorizado("Faça login para continuar.");
        sessaoService.encerrar(sessaoId);
    }

    /** Cria a barbearia (tenant), o usuário dono e o profissional do dono na mesma transação. */
    @Transactional
    public SessaoResponse cadastrarDono(CadastroDonoRequest req) {
        String emailHash = hashDoEmail(req.email());
        validarEmailUnico(emailHash);

        Barbearia barbearia = barbeariaService.criar(req.razaoSocial(), req.documento());
        if (req.atuacao() != null) barbearia.setAtuacao(req.atuacao());

        DonoBarbearia dono = new DonoBarbearia();
        dono.setNome(req.nome().trim());
        dono.setEmail(normalizar(req.email()));
        dono.setEmailHash(emailHash);
        dono.setSenhaHash(passwordEncoder.encode(req.senha()));
        dono.setAceiteTermosEm(Instant.now(clock));
        dono.setBarbearia(barbearia);
        usuarioRepository.save(dono);
        criarProfissionalDoDono(dono, barbearia);
        return sessao(dono);
    }

    // o dono já entra como profissional administrador; o autônomo atende e o dono de salão com equipe só gerencia
    private void criarProfissionalDoDono(DonoBarbearia dono, Barbearia barbearia) {
        Profissional profissional = new Profissional();
        profissional.setBarbearia(barbearia);
        profissional.setUsuario(dono);
        profissional.setNome(dono.getNome());
        profissional.setNomeSocial(dono.getNome().split("\\s+")[0]);
        profissional.setAtende(!"equipe".equals(barbearia.getAtuacao()));
        profissional.setAdministrador(true);
        profissionalRepository.save(profissional);
        // começa com a semana padrão (seg a sex, 9h às 18h); o dono ajusta em "Calendário e horários"
        profissionalHorarioRepository.saveAll(ProfissionalHorario.semanaPadrao(profissional));
    }

    @Transactional
    public SessaoResponse cadastrarCliente(CadastroClienteRequest req) {
        String emailHash = hashDoEmail(req.email());
        validarEmailUnico(emailHash);

        Cliente cliente = new Cliente();
        cliente.setNome(req.nome().trim());
        cliente.setEmail(normalizar(req.email()));
        cliente.setEmailHash(emailHash);
        cliente.setTelefone(req.telefone().trim());
        cliente.setSenhaHash(passwordEncoder.encode(req.senha()));
        cliente.setAceiteTermosEm(Instant.now(clock));
        usuarioRepository.save(cliente);
        return sessao(cliente);
    }

    private void validarEmailUnico(String emailHash) {
        if (usuarioRepository.existsByEmailHash(emailHash)) throw ApiException.conflito("Este e-mail já está cadastrado.");
    }

    private String hashDoEmail(String email) {
        return criptografia.hash(normalizar(email));
    }

    private String normalizar(String email) {
        return email.trim().toLowerCase();
    }

    // abre a sessão no banco e gera o token com o id dela
    private SessaoResponse sessao(Usuario u) {
        UUID barbeariaId = u instanceof DonoBarbearia d ? d.getBarbearia().getId() : null;
        // o painel usa o profissional da conta para marcar "(você)" nas listas; o cliente não tem
        UUID profissionalId = barbeariaId == null ? null : profissionalRepository.findByUsuarioId(u.getId())
                .map(Profissional::getId).orElse(null);
        Sessao sessaoAberta = sessaoService.abrir(u);
        String token = jwtService.gerar(u.getId(), u.getPapel(), barbeariaId, sessaoAberta.getId());
        return new SessaoResponse(token, new SessaoResponse.UsuarioSessao(
                u.getId(), u.getNome(), u.getPapel().name(), barbeariaId, profissionalId));
    }
}
