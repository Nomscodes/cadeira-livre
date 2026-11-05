package br.com.cadeiralivre.api.servico;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.servico.dto.ServicoRequest;
import br.com.cadeiralivre.api.servico.dto.ServicoResponse;
import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/**
 * CRUD do catálogo (RF10). Toda operação é restrita à barbearia do token.
 * Serviço não é apagado do banco: é desativado, para não perder o histórico dos agendamentos.
 */
@Service
@RequiredArgsConstructor
public class ServicoService {

    private final ServicoRepository servicoRepository;
    private final BarbeariaRepository barbeariaRepository;

    @Transactional(readOnly = true)
    public List<ServicoResponse> listar() {
        return servicoRepository.findAllByBarbeariaIdOrderByNome(TenantContext.require())
                .stream().map(ServicoResponse::de).toList();
    }

    @Transactional
    public ServicoResponse criar(ServicoRequest req) {
        Servico s = new Servico();
        s.setBarbearia(barbeariaRepository.getReferenceById(TenantContext.require()));
        preencher(s, req);
        return ServicoResponse.de(servicoRepository.save(s));
    }

    @Transactional
    public ServicoResponse atualizar(UUID id, ServicoRequest req) {
        Servico s = buscar(id);
        preencher(s, req);
        if (Boolean.TRUE.equals(req.ativo())) s.setAtivo(true);
        if (Boolean.FALSE.equals(req.ativo())) desativar(s);
        return ServicoResponse.de(s);
    }

    /** O DELETE da API desativa o serviço: ele some do portal (RF12), mas continua nos agendamentos antigos. */
    @Transactional
    public void excluir(UUID id) {
        desativar(buscar(id));
    }

    private Servico buscar(UUID id) {
        return servicoRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Serviço não encontrado."));
    }

    private void desativar(Servico s) {
        if (!s.isAtivo()) return;
        // mesma regra da publicação (RF09): portal no ar precisa ter pelo menos um serviço
        boolean portalPublicado = s.getBarbearia().isPublico();
        if (portalPublicado && servicoRepository.countByBarbeariaIdAndAtivoTrue(s.getBarbearia().getId()) == 1) {
            throw ApiException.invalido("Este é o último serviço ativo do portal. Oculte o portal antes de desativá-lo.");
        }
        s.setAtivo(false);
    }

    private void preencher(Servico s, ServicoRequest req) {
        s.setNome(req.nome().trim());
        s.setDescricao(req.descricao());
        s.setPreco(req.preco());
        s.setDuracaoMinutos(req.duracaoMinutos());
    }
}
