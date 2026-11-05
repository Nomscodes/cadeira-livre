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

/** CRUD do catálogo (RF10). Toda operação é restrita à barbearia do token. */
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
        return ServicoResponse.de(s);
    }

    @Transactional
    public void excluir(UUID id) {
        servicoRepository.delete(buscar(id));
        servicoRepository.flush(); // força o erro de FK aqui (409 no handler) se houver agendamentos
    }

    private Servico buscar(UUID id) {
        return servicoRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Serviço não encontrado."));
    }

    private void preencher(Servico s, ServicoRequest req) {
        s.setNome(req.nome().trim());
        s.setDescricao(req.descricao());
        s.setPreco(req.preco());
        s.setDuracaoMinutos(req.duracaoMinutos());
    }
}
