package br.com.cadeiralivre.api.profissional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.dto.ProfissionalRequest;
import br.com.cadeiralivre.api.profissional.dto.ProfissionalResponse;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/**
 * Cadastro dos profissionais da barbearia. Toda operação é restrita à barbearia do token.
 * Igual ao serviço, o profissional não é apagado do banco: é desativado, para não perder o histórico.
 */
@Service
@RequiredArgsConstructor
public class ProfissionalService {

    private final ProfissionalRepository profissionalRepository;
    private final ServicoRepository servicoRepository;
    private final BarbeariaRepository barbeariaRepository;

    @Transactional(readOnly = true)
    public List<ProfissionalResponse> listar() {
        return profissionalRepository.findAllByBarbeariaIdOrderByNome(TenantContext.require())
                .stream().map(ProfissionalResponse::de).toList();
    }

    @Transactional
    public ProfissionalResponse criar(ProfissionalRequest req) {
        Profissional p = new Profissional();
        p.setBarbearia(barbeariaRepository.getReferenceById(TenantContext.require()));
        preencher(p, req);
        return ProfissionalResponse.de(profissionalRepository.save(p));
    }

    @Transactional
    public ProfissionalResponse atualizar(UUID id, ProfissionalRequest req) {
        Profissional p = buscar(id);
        preencher(p, req);
        if (req.ativo() != null) p.setAtivo(req.ativo());
        return ProfissionalResponse.de(p);
    }

    /** O DELETE da API desativa o profissional. */
    @Transactional
    public void excluir(UUID id) {
        buscar(id).setAtivo(false);
    }

    private Profissional buscar(UUID id) {
        return profissionalRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Profissional não encontrado."));
    }

    private void preencher(Profissional p, ProfissionalRequest req) {
        List<Servico> servicos = servicosDaBarbearia(req.servicoIds());
        p.setNome(req.nome().trim());
        p.setTelefone(req.telefone() == null || req.telefone().isBlank() ? null : req.telefone().trim());
        p.getServicos().clear();
        p.getServicos().addAll(servicos);
    }

    // só aceita serviços da própria barbearia (RNF05)
    private List<Servico> servicosDaBarbearia(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Servico> encontrados = servicoRepository.findAllByIdInAndBarbeariaId(ids, TenantContext.require());
        if (encontrados.size() != ids.size()) {
            throw ApiException.invalido("Um ou mais serviços informados não existem nesta barbearia.");
        }
        return encontrados;
    }
}
