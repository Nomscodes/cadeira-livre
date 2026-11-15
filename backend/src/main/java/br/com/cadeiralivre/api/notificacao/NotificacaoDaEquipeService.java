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
 * ("Enviar aviso à equipe" e "Pedir avaliação ao cliente"). O funcionário só lê os avisos dele. A barbearia vem do JWT.
 */
@Service
@RequiredArgsConstructor
public class NotificacaoDaEquipeService {

    private final NotificacaoRepository notificacaoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    /**
     * Dono: as notificações do salão (sem os avisos que ele mandou para a equipe), com as ações.
     * Funcionário: só o que foi mandado para o profissional dele, sem ações (ele apenas lê).
     */
    @Transactional(readOnly = true)
    public List<NotificacaoDaEquipeResponse> listar() {
        if (UsuarioLogado.ehFuncionario()) {
            return notificacaoRepository.findTop30ByProfissionalIdOrderByCriadaEmDesc(profissionalDoFuncionario())
                    .stream().map(n -> NotificacaoDaEquipeResponse.de(n, false, false)).toList();
        }
        return notificacaoRepository.findTop30ByBarbeariaIdAndProfissionalIsNullOrderByCriadaEmDesc(TenantContext.require())
                .stream().map(n -> NotificacaoDaEquipeResponse.de(n, true, avaliacaoJaPedida(n))).toList();
    }

    @Transactional
    public void marcarComoLida(UUID id) {
        Notificacao n = UsuarioLogado.ehFuncionario()
                ? notificacaoRepository.findByIdAndProfissionalId(id, profissionalDoFuncionario())
                        .orElseThrow(() -> ApiException.naoEncontrado("Notificação não encontrada."))
                : doDono(id);
        // RF04: guarda quando foi vista; abrir de novo não muda a data
        if (n.getLidaEm() == null) {
            n.setLidaEm(Instant.now(clock));
        }
    }

    /**
     * Executa a ação escolhida no modal. Devolve quantas notificações foram enviadas.
     * Atendimento concluído só tem "avaliacao"; as outras (menos o aviso) só têm "aviso".
     */
    @Transactional
    public int executarAcao(UUID id, String acao, String mensagem, List<UUID> profissionalIds) {
        if (UsuarioLogado.ehFuncionario()) {
            throw ApiException.proibido("Só o responsável do salão pode fazer isso.");
        }
        Notificacao n = doDono(id);
        boolean concluido = n.getTipo() == TipoNotificacao.CONCLUIDO_PARA_DONO;
        if ("avaliacao".equals(acao) && concluido) {
            return pedirAvaliacao(n);
        }
        if ("aviso".equals(acao) && !concluido && n.getTipo() != TipoNotificacao.AVISO_EQUIPE) {
            return enviarAviso(n, mensagem, profissionalIds);
        }
        throw ApiException.invalido("Esta ação não está disponível para esta notificação.");
    }

    // manda para o sino do cliente um pedido de avaliação (uma vez por atendimento)
    private int pedirAvaliacao(Notificacao origem) {
        if (avaliacaoJaPedida(origem)) {
            throw ApiException.invalido("A avaliação deste atendimento já foi pedida.");
        }
        if (origem.getAgendamento().getCliente() == null) {
            throw ApiException.invalido("Este cliente não tem conta para receber o pedido de avaliação.");
        }
        Notificacao pedido = new Notificacao();
        pedido.setTipo(TipoNotificacao.PEDIDO_AVALIACAO);
        pedido.setAgendamento(origem.getAgendamento());
        pedido.setCliente(origem.getAgendamento().getCliente());
        pedido.setCriadaEm(Instant.now(clock));
        notificacaoRepository.save(pedido);
        return 1;
    }

    private boolean avaliacaoJaPedida(Notificacao n) {
        return n.getTipo() == TipoNotificacao.CONCLUIDO_PARA_DONO
                && notificacaoRepository.existsByAgendamentoIdAndTipo(n.getAgendamento().getId(), TipoNotificacao.PEDIDO_AVALIACAO);
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

    // o profissional ligado à conta do funcionário logado
    private UUID profissionalDoFuncionario() {
        return profissionalRepository.findByUsuarioId(UsuarioLogado.usuarioId())
                .map(Profissional::getId)
                .orElseThrow(() -> ApiException.naoAutorizado("Faça login para continuar."));
    }

    // notificação da barbearia logada que é do dono (não pega os avisos que foram para os profissionais)
    private Notificacao doDono(UUID id) {
        return notificacaoRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .filter(n -> n.getProfissional() == null)
                .orElseThrow(() -> ApiException.naoEncontrado("Notificação não encontrada."));
    }
}
