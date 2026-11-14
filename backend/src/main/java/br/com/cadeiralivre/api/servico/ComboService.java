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
import br.com.cadeiralivre.api.servico.dto.ComboRequest;
import br.com.cadeiralivre.api.servico.dto.ComboResponse;
import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/**
 * Combos do painel: pacote de 2 ou mais serviços com preço próprio. Toda operação é restrita à barbearia do token.
 * O combo não é apagado do banco: é desativado, para não perder o histórico dos agendamentos.
 */
@Service
@RequiredArgsConstructor
public class ComboService {

    private final ComboRepository comboRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final BarbeariaRepository barbeariaRepository;

    @Transactional(readOnly = true)
    public List<ComboResponse> listar() {
        return comboRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(TenantContext.require())
                .stream().map(ComboResponse::de).toList();
    }

    @Transactional
    public ComboResponse criar(ComboRequest req) {
        Combo combo = new Combo();
        combo.setBarbearia(barbeariaRepository.getReferenceById(TenantContext.require()));
        preencher(combo, req);
        return ComboResponse.de(comboRepository.save(combo));
    }

    @Transactional
    public ComboResponse atualizar(UUID id, ComboRequest req) {
        Combo combo = buscar(id);
        preencher(combo, req);
        return ComboResponse.de(combo);
    }

    /** O DELETE desativa o combo: ele some do painel e do portal, mas continua nos agendamentos antigos. */
    @Transactional
    public void excluir(UUID id) {
        buscar(id).setAtivo(false);
    }

    private Combo buscar(UUID id) {
        return comboRepository.findByIdAndBarbeariaIdAndAtivoTrue(id, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Combo não encontrado."));
    }

    private void preencher(Combo combo, ComboRequest req) {
        UUID barbeariaId = TenantContext.require();

        // o mesmo serviço repetido não conta duas vezes
        Set<UUID> servicoIds = new HashSet<>(req.servicoIds());
        if (servicoIds.size() < 2) throw ApiException.invalido("Um combo precisa de pelo menos dois serviços diferentes.");
        List<Servico> servicos = servicoRepository.findAllByIdInAndBarbeariaId(servicoIds, barbeariaId).stream()
                .filter(Servico::isAtivo).toList();
        if (servicos.size() != servicoIds.size()) throw ApiException.invalido("Escolha serviços ativos do seu salão.");

        Set<UUID> profissionalIds = req.profissionalIds() == null ? Set.of() : new HashSet<>(req.profissionalIds());
        List<Profissional> profissionais = profissionalRepository.findAllByBarbeariaIdOrderByNome(barbeariaId).stream()
                .filter(p -> profissionalIds.contains(p.getId())).toList();
        if (profissionais.size() != profissionalIds.size()) throw ApiException.invalido("Escolha profissionais do seu salão.");

        combo.setNome(req.nome().trim());
        combo.setPreco(req.preco());
        combo.getServicos().clear();
        combo.getServicos().addAll(servicos);
        combo.getProfissionais().clear();
        combo.getProfissionais().addAll(profissionais);
    }
}
