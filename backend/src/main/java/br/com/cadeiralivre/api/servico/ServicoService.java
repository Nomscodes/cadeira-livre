package br.com.cadeiralivre.api.servico;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
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
    private final ProfissionalRepository profissionalRepository;
    private final ComboRepository comboRepository;

    /** Serviços ativos do salão (os desativados não aparecem no painel). */
    @Transactional(readOnly = true)
    public List<ServicoResponse> listar() {
        return servicoRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(TenantContext.require())
                .stream().map(ServicoResponse::de).toList();
    }

    /** Profissionais que podem ser escolhidos para um serviço ou combo: os que atendem clientes. */
    @Transactional(readOnly = true)
    public List<Profissional> profissionaisQueAtendem() {
        return profissionalRepository.findAllByBarbeariaIdOrderByNome(TenantContext.require()).stream()
                .filter(p -> p.isAtivo() && p.isAtende()).toList();
    }

    @Transactional
    public ServicoResponse criar(ServicoRequest req) {
        Servico s = new Servico();
        s.setBarbearia(barbeariaRepository.getReferenceById(TenantContext.require()));
        preencher(s, req);
        servicoRepository.save(s);
        vincularProfissionais(s, req.profissionalIds());
        return ServicoResponse.de(s);
    }

    @Transactional
    public ServicoResponse atualizar(UUID id, ServicoRequest req) {
        Servico s = buscar(id);
        preencher(s, req);
        vincularProfissionais(s, req.profissionalIds());
        return ServicoResponse.de(s);
    }

    /**
     * O DELETE desativa o serviço: ele some do painel e do portal (RF12), mas continua nos agendamentos antigos.
     * Ele também sai dos profissionais e dos combos; o combo que fica sem nenhum serviço é desativado.
     */
    @Transactional
    public void excluir(UUID id) {
        Servico s = buscar(id);
        desativar(s);
        vincularProfissionais(s, List.of());
        for (Combo combo : comboRepository.findAllByBarbeariaIdAndAtivoTrueAndServicosId(TenantContext.require(), id)) {
            combo.getServicos().remove(s);
            if (combo.getServicos().isEmpty()) combo.setAtivo(false);
        }
    }

    private Servico buscar(UUID id) {
        return servicoRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .filter(Servico::isAtivo)
                .orElseThrow(() -> ApiException.naoEncontrado("Serviço não encontrado."));
    }

    private void desativar(Servico s) {
        // mesma regra da publicação (RF09): portal no ar precisa ter pelo menos um serviço
        boolean portalPublicado = s.getBarbearia().isPublico();
        if (portalPublicado && servicoRepository.countByBarbeariaIdAndAtivoTrue(s.getBarbearia().getId()) == 1) {
            throw ApiException.invalido("Este é o último serviço ativo do portal. Oculte o portal antes de excluí-lo.");
        }
        s.setAtivo(false);
    }

    // o vínculo é gravado pelo lado do profissional: quem está na lista passa a fazer o serviço, os outros deixam de fazer
    private void vincularProfissionais(Servico s, List<UUID> ids) {
        Set<UUID> escolhidos = ids == null ? Set.of() : new HashSet<>(ids);
        List<Profissional> profissionais = profissionalRepository.findAllByBarbeariaIdOrderByNome(TenantContext.require());
        long doSalao = profissionais.stream().filter(p -> escolhidos.contains(p.getId())).count();
        if (doSalao != escolhidos.size()) throw ApiException.invalido("Escolha profissionais do seu salão.");

        for (Profissional p : profissionais) {
            if (escolhidos.contains(p.getId())) {
                p.getServicos().add(s);
                s.getProfissionais().add(p);
            } else {
                p.getServicos().remove(s);
                s.getProfissionais().remove(p);
            }
        }
    }

    private void preencher(Servico s, ServicoRequest req) {
        s.setNome(req.nome().trim());
        s.setDescricao(req.descricao());
        s.setPreco(req.preco());
        s.setDuracaoMinutos(req.duracao());
    }
}
