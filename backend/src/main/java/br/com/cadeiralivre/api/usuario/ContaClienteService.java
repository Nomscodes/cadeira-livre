package br.com.cadeiralivre.api.usuario;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.agendamento.Agendamento;
import br.com.cadeiralivre.api.agendamento.AgendamentoRepository;
import br.com.cadeiralivre.api.agendamento.StatusAgendamento;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.security.UsuarioLogado;
import br.com.cadeiralivre.api.usuario.dto.ContaResponse;
import br.com.cadeiralivre.api.usuario.dto.ExclusaoContaRequest;
import br.com.cadeiralivre.api.usuario.dto.TrocaSenhaRequest;
import lombok.RequiredArgsConstructor;

/** Conta do cliente: ver os próprios dados, trocar a senha e excluir a conta (LGPD). */
@Service
@RequiredArgsConstructor
public class ContaClienteService {

    // o que fica no lugar dos dados pessoais nos agendamentos de quem excluiu a conta
    private static final String NOME_REMOVIDO = "Cliente removido";
    private static final String TELEFONE_REMOVIDO = "removido";

    private final ClienteRepository clienteRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /** LGPD (direito de acesso): o cliente vê os dados pessoais que o sistema guarda dele. */
    @Transactional(readOnly = true)
    public ContaResponse meusDados() {
        return ContaResponse.de(clienteLogado());
    }

    @Transactional
    public void trocarSenha(TrocaSenhaRequest req) {
        Cliente cliente = clienteLogado();
        conferirSenha(cliente, req.senhaAtual(), "A senha atual está incorreta.");
        if (passwordEncoder.matches(req.novaSenha(), cliente.getSenhaHash())) {
            throw ApiException.invalido("A nova senha precisa ser diferente da atual.");
        }
        cliente.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
    }

    /**
     * LGPD (direito de exclusão): apaga a conta e os dados pessoais do cliente.
     * Os agendamentos continuam no histórico da barbearia, mas sem nome, sem telefone e sem ligação com a conta.
     * Os que ainda iam acontecer (pendentes ou aprovados) são cancelados, para liberar a agenda.
     */
    @Transactional
    public void excluirConta(ExclusaoContaRequest req) {
        Cliente cliente = clienteLogado();
        conferirSenha(cliente, req.senha(), "Senha incorreta.");

        LocalDateTime agora = LocalDateTime.now(clock);
        for (Agendamento a : agendamentoRepository.findAllByClienteIdOrderByDataDescHoraInicioDesc(cliente.getId())) {
            boolean aindaVaiAcontecer = LocalDateTime.of(a.getData(), a.getHoraInicio()).isAfter(agora);
            boolean ativo = a.getStatus() == StatusAgendamento.PENDENTE || a.getStatus() == StatusAgendamento.APROVADO;
            if (aindaVaiAcontecer && ativo) {
                a.setStatus(StatusAgendamento.CANCELADO);
            }
            a.setCliente(null);
            a.setClienteNome(NOME_REMOVIDO);
            a.setClienteTelefone(TELEFONE_REMOVIDO);
        }
        clienteRepository.delete(cliente);
    }

    private Cliente clienteLogado() {
        UUID clienteId = UsuarioLogado.clienteId();
        if (clienteId == null) throw ApiException.naoAutorizado("Faça login para continuar.");
        // token antigo de uma conta que já foi excluída: é como se não estivesse logado
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> ApiException.naoAutorizado("Faça login para continuar."));
    }

    private void conferirSenha(Cliente cliente, String senha, String mensagemDeErro) {
        if (!passwordEncoder.matches(senha, cliente.getSenhaHash())) {
            throw ApiException.invalido(mensagemDeErro);
        }
    }
}
