package br.com.cadeiralivre.api.barbearia;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.auditoria.AcaoAuditoria;
import br.com.cadeiralivre.api.auditoria.AuditoriaService;
import br.com.cadeiralivre.api.barbearia.dto.PortalPublicoResponse;
import br.com.cadeiralivre.api.barbearia.dto.PortalRequest;
import br.com.cadeiralivre.api.barbearia.dto.PortalResponse;
import br.com.cadeiralivre.api.common.Slugs;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.HorarioFuncionamentoRepository;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.servico.dto.ServicoResponse;
import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/** Portal público (leitura por slug) e customização white-label (escrita pelo dono, via tenant do JWT). */
@Service
@RequiredArgsConstructor
public class BarbeariaService {

    private final BarbeariaRepository barbeariaRepository;
    private final BarbeariaFotoRepository fotoRepository;
    private final ServicoRepository servicoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final HorarioFuncionamentoRepository horarioRepository;
    private final AuditoriaService auditoriaService;

    /** Chamado no cadastro do dono. Nasce oculta (publico = false) até o dono publicar. */
    @Transactional
    public Barbearia criar(String razaoSocial, String documento) {
        Barbearia b = new Barbearia();
        b.setRazaoSocial(razaoSocial.trim());
        b.setNomeFantasia(razaoSocial.trim());
        b.setDocumento(documento);
        b.setSlug(slugUnico(razaoSocial));
        return barbeariaRepository.save(b);
    }

    @Transactional(readOnly = true)
    public PortalPublicoResponse portalPublico(String slug) {
        Barbearia b = barbeariaRepository.findBySlug(slug)
                .orElseThrow(() -> ApiException.naoEncontrado("Esta página não existe."));
        if (!b.isPublico()) return PortalPublicoResponse.oculto(b);

        List<ServicoResponse> servicos = servicoRepository
                .findAllByBarbeariaIdAndAtivoTrueOrderByNome(b.getId())
                .stream().map(ServicoResponse::de).toList();
        return PortalPublicoResponse.de(b, servicos);
    }

    /** Tudo o que a tela "Meu portal" do painel mostra. */
    @Transactional(readOnly = true)
    public PortalResponse obterDoPainel() {
        return montarPortal(buscarDoTenant());
    }

    /** Salva os dados do portal (RF06–RF09). A publicação tem rota própria. */
    @Transactional
    public PortalResponse atualizar(PortalRequest req) {
        Barbearia b = buscarDoTenant();
        b.setNomeFantasia(req.nome().trim());
        if (req.temaId() != null) b.setTemaId(req.temaId());
        if (req.categoria() != null) b.setCategoria(req.categoria());
        b.setSobre(textoOuNulo(req.sobre()));
        b.setTelefone(textoOuNulo(req.whatsapp()));
        b.setCidade(textoOuNulo(req.cidade()));
        b.setEndereco(textoOuNulo(req.endereco()));
        b.setRedes(textoOuNulo(req.redes()));
        b.setLogoUrl(req.logo());
        b.setCapaUrl(req.capa());
        if (req.galeria() != null) trocarGaleria(b, req.galeria());
        if (req.adminIds() != null && !req.adminIds().isEmpty()) definirAdministradores(b, req.adminIds());

        // RNF09: alteração da customização fica registrada com o usuário e a hora
        auditoriaService.registrar(AcaoAuditoria.PORTAL_ALTERADO,
                "Nome: " + b.getNomeFantasia() + " | Tema: " + b.getTemaId());
        return montarPortal(b);
    }

    // apaga as fotos antigas direto no banco e grava a galeria nova na ordem em que veio
    private void trocarGaleria(Barbearia b, List<String> imagens) {
        fotoRepository.apagarDaBarbearia(b.getId());
        List<BarbeariaFoto> fotos = new ArrayList<>();
        for (int ordem = 0; ordem < imagens.size(); ordem++) {
            fotos.add(new BarbeariaFoto(b, imagens.get(ordem), ordem));
        }
        fotoRepository.saveAll(fotos);
    }

    // os escolhidos viram administradores e os outros deixam de ser; só vale para profissionais desta barbearia
    private void definirAdministradores(Barbearia b, List<UUID> adminIds) {
        List<Profissional> profissionais = profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId());
        Set<UUID> daBarbearia = profissionais.stream().map(Profissional::getId).collect(Collectors.toSet());
        if (!daBarbearia.containsAll(adminIds)) {
            throw ApiException.invalido("Escolha os administradores entre os profissionais do salão.");
        }
        profissionais.forEach(p -> p.setAdministrador(adminIds.contains(p.getId())));
    }

    private PortalResponse montarPortal(Barbearia b) {
        return PortalResponse.de(b,
                fotoRepository.findAllByBarbeariaIdOrderByOrdem(b.getId()),
                horarioRepository.findAllByBarbeariaId(b.getId()),
                profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId()));
    }

    // campo em branco é gravado como vazio (null)
    private String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private Barbearia buscarDoTenant() {
        return barbeariaRepository.findById(TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Barbearia não encontrada."));
    }

    private String slugUnico(String texto) {
        String base = Slugs.de(texto);
        String slug = base;
        int n = 2;
        while (barbeariaRepository.existsBySlug(slug)) slug = base + "-" + n++;
        return slug;
    }
}
