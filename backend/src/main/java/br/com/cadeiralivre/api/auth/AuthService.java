package br.com.cadeiralivre.api.auth;

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
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.security.JwtService;
import br.com.cadeiralivre.api.usuario.Cliente;
import br.com.cadeiralivre.api.usuario.DonoBarbearia;
import br.com.cadeiralivre.api.usuario.Usuario;
import br.com.cadeiralivre.api.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;

/** Regras de cadastro (RF01–RF03) e login (RF04). */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final BarbeariaService barbeariaService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public SessaoResponse login(LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmail(normalizar(req.email()))
                .filter(u -> passwordEncoder.matches(req.senha(), u.getSenhaHash()))
                .orElseThrow(() -> ApiException.naoAutorizado("E-mail ou senha incorretos."));
        return sessao(usuario);
    }

    /** Cria a barbearia (tenant) e o usuário dono na mesma transação. */
    @Transactional
    public SessaoResponse cadastrarDono(CadastroDonoRequest req) {
        String email = normalizar(req.email());
        validarEmailUnico(email);

        Barbearia barbearia = barbeariaService.criar(req.razaoSocial(), req.documento());

        DonoBarbearia dono = new DonoBarbearia();
        dono.setNome(req.nome().trim());
        dono.setEmail(email);
        dono.setSenhaHash(passwordEncoder.encode(req.senha()));
        dono.setBarbearia(barbearia);
        usuarioRepository.save(dono);
        return sessao(dono);
    }

    @Transactional
    public SessaoResponse cadastrarCliente(CadastroClienteRequest req) {
        String email = normalizar(req.email());
        validarEmailUnico(email);

        Cliente cliente = new Cliente();
        cliente.setNome(req.nome().trim());
        cliente.setEmail(email);
        cliente.setTelefone(req.telefone().trim());
        cliente.setSenhaHash(passwordEncoder.encode(req.senha()));
        usuarioRepository.save(cliente);
        return sessao(cliente);
    }

    private void validarEmailUnico(String email) {
        if (usuarioRepository.existsByEmail(email)) throw ApiException.conflito("Este e-mail já está cadastrado.");
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
