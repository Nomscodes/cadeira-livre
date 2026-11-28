package br.com.cadeiralivre.api.notificacao;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoItem;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.dto.NotificacaoResponse;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import br.com.cadeiralivre.api.tenant.TenantContext;
import br.com.cadeiralivre.api.usuario.Papel;
import lombok.RequiredArgsConstructor;

/**
 * Notificações dentro do sistema (RF03), sem SMS nem e-mail (fora do escopo do doc V7).
 * Os outros services chamam paraDono/paraCliente quando algo acontece com um agendamento.
 */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificacaoRepository notificacaoRepository;
    private final Clock clock;

    /** Aviso para o dono da barbearia do agendamento. */
    @Transactional
    public void paraDono(Agendamento agendamento, TipoNotificacao tipo) {
        Notificacao n = nova(agendamento, tipo);
        n.setBarbearia(agendamento.getBarbearia());
        notificacaoRepository.save(n);
    }

    /**
     * Aviso para cada profissional do atendimento que tem conta de funcionário (o dono já recebe pelo paraDono).
     * Quem ainda não criou a conta com o convite não tem onde ver, então não recebe.
     */
    @Transactional
    public void paraEquipe(Agendamento agendamento, TipoNotificacao tipo) {
        agendamento.getItens().stream()
                .map(AgendamentoItem::getProfissional)
                .distinct()
                .filter(p -> p.getUsuario() != null && p.getUsuario().getPapel() == Papel.FUNCIONARIO)
                .forEach(p -> {
                    Notificacao n = nova(agendamento, tipo);
                    n.setBarbearia(agendamento.getBarbearia());
                    n.setProfissional(p);
                    notificacaoRepository.save(n);
                });
    }

    /** Aviso para o cliente. Quem agendou sem conta não recebe, porque não tem onde ver. */
    @Transactional
    public void paraCliente(Agendamento agendamento, TipoNotificacao tipo) {
        if (agendamento.getCliente() == null) return;
        Notificacao n = nova(agendamento, tipo);
        n.setCliente(agendamento.getCliente());
        notificacaoRepository.save(n);
    }

    /** Sino do painel: a barbearia vem do JWT. */
    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listarDoDono() {
        return notificacaoRepository.findTop30ByBarbeariaIdAndProfissionalIsNullOrderByCriadaEmDesc(TenantContext.require())
                .stream().map(NotificacaoResponse::de).toList();
    }

    /** Sino da área do cliente: o cliente vem do JWT. */
    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listarDoCliente() {
        return notificacaoRepository.findTop30ByClienteIdOrderByCriadaEmDesc(clienteLogado())
                .stream().map(NotificacaoResponse::de).toList();
    }

    @Transactional
    public void marcarComoLidaDoDono(UUID id) {
        marcarComoLida(notificacaoRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Notificação não encontrada.")));
    }

    @Transactional
    public void marcarComoLidaDoCliente(UUID id) {
        marcarComoLida(notificacaoRepository.findByIdAndClienteId(id, clienteLogado())
                .orElseThrow(() -> ApiException.naoEncontrado("Notificação não encontrada.")));
    }

    // RF04: guarda quando o aviso foi visto; abrir de novo não muda a data
    private void marcarComoLida(Notificacao n) {
        if (n.getLidaEm() == null) {
            n.setLidaEm(Instant.now(clock));
        }
    }

    /**
     * D3: o salão apagou os dados ou encerrou a conta, e o horário do cliente vai ser apagado junto.
     * O aviso fica sem o agendamento (V21), com o texto já pronto. Quem agendou sem conta não recebe.
     * Chamar antes de apagar o agendamento (lê os serviços e o salão dele).
     */
    @Transactional
    public void cancelamentoPeloSalao(Agendamento agendamento) {
        if (agendamento.getCliente() == null) return;
        String servicos = agendamento.getItens().isEmpty() ? agendamento.getServico().getNome()
                : agendamento.getItens().stream().map(AgendamentoItem::getNome).distinct()
                        .collect(Collectors.joining(" + "));
        String texto = "Seu horário de " + servicos + " em " + agendamento.getData().format(DIA)
                + " às " + agendamento.getHoraInicio().format(HORA) + " na "
                + agendamento.getBarbearia().getNomeFantasia() + " foi cancelado pelo salão.";

        Notificacao n = nova(null, TipoNotificacao.HORARIO_CANCELADO_PELO_SALAO);
        n.setCliente(agendamento.getCliente());
        n.setTexto(texto.length() <= 500 ? texto : texto.substring(0, 497) + "..."); // coluna texto (V18)
        notificacaoRepository.save(n);
    }

    /**
     * D1: o salão fechou o dia, deu folga ou desativou o profissional, mas o horário continua marcado.
     * O aviso fica ligado ao agendamento, com o texto já pronto. Quem agendou sem conta não recebe.
     */
    @Transactional
    public void avisoDoSalao(Agendamento agendamento, String texto) {
        if (agendamento.getCliente() == null) return;
        Notificacao n = nova(agendamento, TipoNotificacao.AVISO_DO_SALAO);
        n.setCliente(agendamento.getCliente());
        n.setTexto(texto.length() <= 500 ? texto : texto.substring(0, 497) + "..."); // coluna texto (V18)
        notificacaoRepository.save(n);
    }

    private Notificacao nova(Agendamento agendamento, TipoNotificacao tipo) {
        Notificacao n = new Notificacao();
        n.setAgendamento(agendamento);
        n.setTipo(tipo);
        n.setCriadaEm(Instant.now(clock));
        return n;
    }

    private UUID clienteLogado() {
        UUID clienteId = UsuarioLogado.clienteId();
        if (clienteId == null) throw ApiException.naoAutorizado("Faça login para continuar.");
        return clienteId;
    }
}
