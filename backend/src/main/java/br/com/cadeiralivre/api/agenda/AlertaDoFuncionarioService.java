package br.com.cadeiralivre.api.agenda;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.notificacao.Notificacao;
import br.com.cadeiralivre.api.notificacao.NotificacaoRepository;
import br.com.cadeiralivre.api.notificacao.TipoNotificacao;
import br.com.cadeiralivre.api.profissional.Profissional;
import lombok.RequiredArgsConstructor;

/**
 * Alerta do funcionário sobre um cliente de um atendimento dele (ex.: "Cliente agressivo").
 * Vira uma notificação no sino do dono, com o nome de quem mandou.
 */
@Service
@RequiredArgsConstructor
public class AlertaDoFuncionarioService {

    private final AgendaService agendaService;
    private final AgendamentoRepository agendamentoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final Clock clock;

    @Transactional
    public void alertar(UUID agendamentoId, String motivo, String descricao) {
        Profissional p = agendaService.profissionalLogado();
        String m = motivo == null ? "" : motivo.trim();
        String d = descricao == null ? "" : descricao.trim();
        if (m.isEmpty()) throw ApiException.invalido("Escolha o motivo do alerta.");
        if (m.length() > 100 || d.length() > 300) throw ApiException.invalido("O alerta ficou grande demais.");

        // só dá para alertar sobre um atendimento em que ele trabalha, no salão dele
        Agendamento a = agendamentoId == null ? null : agendamentoRepository
                .findByIdAndBarbeariaId(agendamentoId, p.getBarbearia().getId())
                .filter(ag -> ag.getItens().stream().anyMatch(i -> i.getProfissional().getId().equals(p.getId())))
                .orElse(null);
        if (a == null) throw ApiException.naoEncontrado("Agendamento não encontrado.");

        Notificacao alerta = new Notificacao();
        alerta.setTipo(TipoNotificacao.ALERTA_FUNCIONARIO);
        alerta.setAgendamento(a);
        alerta.setBarbearia(a.getBarbearia()); // vai para o dono (sem profissional)
        alerta.setTexto(p.getNome() + " sinalizou o cliente " + a.getClienteNome() + ": " + m + "."
                + (d.isEmpty() ? "" : " " + d));
        alerta.setRemetente(p.getNome());
        alerta.setCriadaEm(Instant.now(clock));
        notificacaoRepository.save(alerta);
    }
}
