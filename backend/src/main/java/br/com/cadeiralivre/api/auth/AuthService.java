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
import br.com.cadeiralivre.api.security.JwtService;
import br.com.cadeiralivre.api.usuario.Cliente;
import br.com.cadeiralivre.api.usuario.DonoBarbearia;
import br.com.cadeiralivre.api.usuario.Usuario;
import br.com.cadeiralivre.api.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;

/** Regras de cadastro (RF01–RF03), login (RF04) e aceite dos termos (RNF02). */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final BarbeariaService barbeariaService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Criptografia criptografia;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SessaoResponse login(LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmailHash(hashDoEmail(req.email()))
                .filter(u -> passwordEncoder.matches(req.senha(), u.getSenhaHash()))
                .orElseThrow(() -> ApiException.naoAutorizado("E-mail ou senha incorretos."));
        return sessao(usuario);
    }

    /** Cria a barbearia (tenant) e o usuário dono na mesma transação. */
    @Transactional
    public SessaoResponse cadastrarDono(CadastroDonoRequest req) {
        String emailHash = hashDoEmail(req.email());
        validarEmailUnico(emailHash);

        Barbearia barbearia = barbeariaService.criar(req.razaoSocial(), req.documento());

        DonoBarbearia dono = new DonoBarbearia();
        dono.setNome(req.nome().trim());
        dono.setEmail(normalizar(req.email()));
        dono.setEmailHash(emailHash);
        dono.setSenhaHash(passwordEncoder.encode(req.senha()));
        dono.setAceiteTermosEm(Instant.now(clock));
        dono.setBarbearia(barbearia);
        usuarioRepository.save(dono);
        return sessao(dono);
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

    private SessaoResponse sessao(Usuario u) {
        UUID barbeariaId = u instanceof DonoBarbearia d ? d.getBarbearia().getId() : null;
        String token = jwtService.gerar(u.getId(), u.getPapel(), barbeariaId);
        return new SessaoResponse(token,
                new SessaoResponse.UsuarioSessao(u.getId(), u.getNome(), u.getPapel().name(), barbeariaId));
    }
}
