package br.com.cadeiralivre.api.barbearia;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.auditoria.AcaoAuditoria;
import br.com.cadeiralivre.api.auditoria.AuditoriaService;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import br.com.cadeiralivre.api.tenant.TenantContext;
import br.com.cadeiralivre.api.usuario.Usuario;
import br.com.cadeiralivre.api.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;

/**
 * Conta do dono na tela "Configurações": trocar a senha, apagar os dados do salão e encerrar a conta (LGPD, RNF02).
 * Apagar e encerrar usam SQL direto, na ordem das chaves estrangeiras: primeiro o que depende, depois o principal.
 */
@Service
@RequiredArgsConstructor
public class ContaDoPainelService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoriaService;

    @Transactional
    public void trocarSenha(String atual, String nova) {
        UUID usuarioId = UsuarioLogado.usuarioId();
        Usuario usuario = usuarioId == null ? null : usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) throw ApiException.naoAutorizado("Faça login para continuar.");
        if (!passwordEncoder.matches(atual, usuario.getSenhaHash())) {
            throw ApiException.invalido("A senha atual está incorreta.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(nova));
    }

    /**
     * Apaga os agendamentos (com avaliações e notificações), os serviços, os combos e os bloqueios do salão,
     * e oculta o portal. A barbearia, a equipe e os horários continuam.
     */
    @Transactional
    public void apagarDados() {
        UUID b = TenantContext.require();
        String agendamentos = "(select id from agendamento where barbearia_id = ?)";
        jdbc.update("delete from notificacao where agendamento_id in " + agendamentos, b);
        jdbc.update("delete from avaliacao where agendamento_id in " + agendamentos, b);
        jdbc.update("delete from agendamento where barbearia_id = ?", b); // os itens vão junto (ON DELETE CASCADE)
        String combos = "(select id from combo where barbearia_id = ?)";
        jdbc.update("delete from combo_servico where combo_id in " + combos, b);
        jdbc.update("delete from combo_profissional where combo_id in " + combos, b);
        jdbc.update("delete from combo where barbearia_id = ?", b);
        jdbc.update("delete from profissional_servico where servico_id in (select id from servico where barbearia_id = ?)", b);
        jdbc.update("delete from servico where barbearia_id = ?", b);
        jdbc.update("delete from bloqueio_horario where barbearia_id = ?", b);
        jdbc.update("update barbearia set publico = false where id = ?", b);
        auditoriaService.registrar(AcaoAuditoria.PORTAL_OCULTADO, "Dados do salão apagados pelo dono");
    }

    /**
     * Encerra a conta: apaga os dados do salão, a equipe, os horários, as fotos, a conta do dono (com as sessões)
     * e a barbearia. Fica só o registro da auditoria, que não guarda dados pessoais.
     */
    @Transactional
    public void encerrarConta() {
        UUID b = TenantContext.require();
        apagarDados();
        jdbc.update("delete from notificacao where barbearia_id = ?", b);
        jdbc.update("delete from profissional where barbearia_id = ?", b); // horários dos profissionais vão junto
        jdbc.update("delete from horario_funcionamento where barbearia_id = ?", b);
        // apagar o usuário apaga junto o dono_barbearia / funcionario e as sessões (ON DELETE CASCADE)
        jdbc.update("delete from usuario where id in (select id from funcionario where barbearia_id = ?)", b);
        jdbc.update("delete from usuario where id in (select id from dono_barbearia where barbearia_id = ?)", b);
        jdbc.update("delete from barbearia where id = ?", b); // as fotos da galeria vão junto
    }
}
