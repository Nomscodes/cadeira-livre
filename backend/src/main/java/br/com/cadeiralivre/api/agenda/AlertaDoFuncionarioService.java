package br.com.cadeiralivre.api.agenda;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
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

    // lista fechada de motivos (D5): os mesmos da tela "Minha agenda"
    static final List<String> MOTIVOS = List.of(
            "Não pagou o último serviço",
            "Não me sinto seguro(a) com esse cliente",
            "Cliente agressivo",
            "Outro (descrever abaixo)");
    static final int LIMITE_DESCRICAO = 300;
    // tamanho da coluna notificacao.texto (V18)
    static final int LIMITE_TEXTO = 500;

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
        if (!MOTIVOS.contains(m)) throw ApiException.invalido("Escolha um dos motivos da lista.");
        if (d.length() > LIMITE_DESCRICAO) {
            throw ApiException.invalido("A descrição pode ter até " + LIMITE_DESCRICAO + " caracteres.");
        }

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
        alerta.setTexto(cabeNaColuna(p.getNome() + " sinalizou o cliente " + a.getClienteNome() + ": " + m + "."
                + (d.isEmpty() ? "" : " " + d)));
        alerta.setRemetente(p.getNome());
        alerta.setCriadaEm(Instant.now(clock));
        notificacaoRepository.save(alerta);
    }

    // com nomes muito longos o texto passaria de 500 e daria erro no banco: corta o fim com reticências
    static String cabeNaColuna(String texto) {
        if (texto.length() <= LIMITE_TEXTO) return texto;
        return texto.substring(0, LIMITE_TEXTO - 3) + "...";
    }
}
