package br.com.cadeiralivre.api.barbearia;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.auditoria.AcaoAuditoria;
import br.com.cadeiralivre.api.auditoria.AuditoriaService;
import br.com.cadeiralivre.api.barbearia.dto.PortalPublicoResponse;
import br.com.cadeiralivre.api.barbearia.dto.PortalRequest;
import br.com.cadeiralivre.api.barbearia.dto.PortalResponse;
import br.com.cadeiralivre.api.common.Slugs;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.servico.dto.ServicoResponse;
import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/** Portal público (leitura por slug) e customização white-label (escrita pelo dono, via tenant do JWT). */
@Service
@RequiredArgsConstructor
public class BarbeariaService {

    private final BarbeariaRepository barbeariaRepository;
    private final ServicoRepository servicoRepository;
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

    @Transactional(readOnly = true)
    public PortalResponse obterDoPainel() {
        return PortalResponse.de(buscarDoTenant());
    }

    @Transactional
    public PortalResponse atualizar(PortalRequest req) {
        Barbearia b = buscarDoTenant();
        boolean vaiPublicar = Boolean.TRUE.equals(req.publico()) && !b.isPublico();
        boolean vaiOcultar = Boolean.FALSE.equals(req.publico()) && b.isPublico();

        // RF09: o portal só vai ao ar se o cliente tiver pelo menos um serviço para escolher
        if (vaiPublicar && !servicoRepository.existsByBarbeariaIdAndAtivoTrue(b.getId())) {
            throw ApiException.invalido("Cadastre pelo menos um serviço ativo antes de publicar o portal.");
        }

        b.setNomeFantasia(req.nomeFantasia().trim());
        if (req.corPrimaria() != null) b.setCorPrimaria(req.corPrimaria());
        b.setLogoUrl(req.logoUrl());
        b.setEndereco(req.endereco());
        b.setTelefone(req.telefone());
        auditoriaService.registrar(AcaoAuditoria.PORTAL_ALTERADO,
                "Nome: " + b.getNomeFantasia() + " | Cor: " + b.getCorPrimaria());

        if (vaiPublicar) {
            b.setPublico(true);
            auditoriaService.registrar(AcaoAuditoria.PORTAL_PUBLICADO, null);
        }
        if (vaiOcultar) {
            b.setPublico(false);
            auditoriaService.registrar(AcaoAuditoria.PORTAL_OCULTADO, null);
        }
        return PortalResponse.de(b);
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
