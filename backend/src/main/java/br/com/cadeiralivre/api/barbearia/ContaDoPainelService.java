package br.com.cadeiralivre.api.barbearia;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.auditoria.AcaoAuditoria;
import br.com.cadeiralivre.api.auditoria.AuditoriaService;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.NotificacaoService;
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
    private final AgendamentoRepository agendamentoRepository;
    private final NotificacaoService notificacaoService;
    private final Clock clock;

    @Transactional
    public void trocarSenha(String atual, String nova) {
        Usuario usuario = usuarioLogado();
        if (!passwordEncoder.matches(atual, usuario.getSenhaHash())) {
            throw ApiException.invalido("A senha atual está incorreta.");
        }
        usuario.setSenhaHash(passwordEncoder.encode(nova));
    }

    /**
     * Apaga os agendamentos (com avaliações e notificações), os serviços, os combos e os bloqueios do salão,
     * e oculta o portal. A barbearia, a equipe e os horários continuam. Pede a senha do dono (D2).
     * Os clientes com horário marcado recebem o aviso de que o salão cancelou (D3).
     */
    @Transactional
    public void apagarDados(String senha) {
        conferirSenha(senha);
        apagarDadosDoSalao();
    }

    /**
     * Encerra a conta: apaga os dados do salão, a equipe, os horários, as fotos, a conta do dono (com as sessões)
     * e a barbearia. Fica só o registro da auditoria, que não guarda dados pessoais. Pede a senha do dono (D2).
     */
    @Transactional
    public void encerrarConta(String senha) {
        conferirSenha(senha);
        UUID b = TenantContext.require();
        apagarDadosDoSalao();
        jdbc.update("delete from notificacao where barbearia_id = ?", b);
        jdbc.update("delete from profissional where barbearia_id = ?", b); // horários dos profissionais vão junto
        jdbc.update("delete from horario_funcionamento where barbearia_id = ?", b);
        // apagar o usuário apaga junto o dono_barbearia / funcionario e as sessões (ON DELETE CASCADE)
        jdbc.update("delete from usuario where id in (select id from funcionario where barbearia_id = ?)", b);
        jdbc.update("delete from usuario where id in (select id from dono_barbearia where barbearia_id = ?)", b);
        jdbc.update("delete from barbearia where id = ?", b); // as fotos da galeria vão junto
    }

    private void apagarDadosDoSalao() {
        UUID b = TenantContext.require();
        avisarClientesComHorarioMarcado(b);
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

    // D3: quem tinha horário marcado (pendente ou aprovado, ainda por acontecer) fica sabendo que o salão cancelou.
    // O aviso é criado antes de apagar, porque o texto usa os serviços e a data do agendamento.
    private void avisarClientesComHorarioMarcado(UUID barbeariaId) {
        LocalDateTime agora = LocalDateTime.now(clock);
        agendamentoRepository.findAllByBarbeariaIdOrderByDataDescHoraInicioDesc(barbeariaId).stream()
                .filter(a -> a.getStatus() == StatusAgendamento.PENDENTE || a.getStatus() == StatusAgendamento.APROVADO)
                .filter(a -> LocalDateTime.of(a.getData(), a.getHoraInicio()).isAfter(agora))
                .forEach(notificacaoService::cancelamentoPeloSalao);
    }

    // ações que não dá para desfazer pedem a senha de quem está logado (D2)
    private void conferirSenha(String senha) {
        Usuario usuario = usuarioLogado();
        if (senha == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw ApiException.invalido("A senha está incorreta.");
        }
    }

    private Usuario usuarioLogado() {
        UUID usuarioId = UsuarioLogado.usuarioId();
        Usuario usuario = usuarioId == null ? null : usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario == null) throw ApiException.naoAutorizado("Faça login para continuar.");
        return usuario;
    }
}
