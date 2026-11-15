package br.com.cadeiralivre.api.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.dto.NotificacaoDaEquipeResponse;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import br.com.cadeiralivre.api.tenant.TenantContext;
import br.com.cadeiralivre.api.usuario.Usuario;
import br.com.cadeiralivre.api.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;

/**
 * Sino do painel no formato do front: lista, marcar como lida e as ações do responsável
 * (por enquanto, "Enviar aviso à equipe"). A barbearia vem sempre do JWT.
 */
@Service
@RequiredArgsConstructor
public class NotificacaoDaEquipeService {

    private final NotificacaoRepository notificacaoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    /** As notificações do dono (sem os avisos que ele mandou para a equipe). */
    @Transactional(readOnly = true)
    public List<NotificacaoDaEquipeResponse> listar() {
        return notificacaoRepository.findTop30ByBarbeariaIdAndProfissionalIsNullOrderByCriadaEmDesc(TenantContext.require())
                .stream().map(n -> NotificacaoDaEquipeResponse.de(n, true)).toList();
    }

    @Transactional
    public void marcarComoLida(UUID id) {
        Notificacao n = doDono(id);
        // RF04: guarda quando foi vista; abrir de novo não muda a data
        if (n.getLidaEm() == null) {
            n.setLidaEm(Instant.now(clock));
        }
    }

    /** Executa a ação escolhida no modal. Devolve quantos avisos foram enviados. */
    @Transactional
    public int executarAcao(UUID id, String acao, String mensagem, List<UUID> profissionalIds) {
        Notificacao n = doDono(id);
        if (!"aviso".equals(acao) || n.getTipo() == TipoNotificacao.AVISO_EQUIPE) {
            throw ApiException.invalido("Esta ação não está disponível para esta notificação.");
        }
        return enviarAviso(n, mensagem, profissionalIds);
    }

    // cria uma notificação para cada profissional escolhido (o próprio dono não recebe)
    private int enviarAviso(Notificacao origem, String mensagem, List<UUID> profissionalIds) {
        String texto = mensagem == null ? "" : mensagem.trim();
        if (texto.isEmpty()) throw ApiException.invalido("Escreva a mensagem do aviso.");
        if (texto.length() > 500) throw ApiException.invalido("O aviso pode ter no máximo 500 caracteres.");

        UUID barbeariaId = TenantContext.require();
        Usuario dono = usuarioRepository.findById(UsuarioLogado.usuarioId())
                .orElseThrow(() -> ApiException.naoAutorizado("Faça login para continuar."));
        UUID profissionalDoDono = profissionalRepository.findByUsuarioId(dono.getId())
                .map(Profissional::getId).orElse(null);

        // só profissionais desta barbearia, sem repetir e sem o próprio dono
        List<Profissional> alvos = new ArrayList<>();
        for (UUID pid : profissionalIds == null ? List.<UUID>of() : profissionalIds.stream().distinct().toList()) {
            if (pid.equals(profissionalDoDono)) continue;
            profissionalRepository.findByIdAndBarbeariaId(pid, barbeariaId).ifPresent(alvos::add);
        }
        if (alvos.isEmpty()) {
            throw ApiException.invalido("Escolha ao menos um profissional da equipe para receber o aviso.");
        }

        for (Profissional p : alvos) {
            Notificacao aviso = new Notificacao();
            aviso.setTipo(TipoNotificacao.AVISO_EQUIPE);
            aviso.setAgendamento(origem.getAgendamento());
            aviso.setBarbearia(origem.getBarbearia());
            aviso.setProfissional(p);
            aviso.setTexto(texto);
            aviso.setRemetente(dono.getNome());
            aviso.setCriadaEm(Instant.now(clock));
            notificacaoRepository.save(aviso);
        }
        return alvos.size();
    }

    // notificação da barbearia logada que é do dono (não pega os avisos que foram para os profissionais)
    private Notificacao doDono(UUID id) {
        return notificacaoRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .filter(n -> n.getProfissional() == null)
                .orElseThrow(() -> ApiException.naoEncontrado("Notificação não encontrada."));
    }
}
