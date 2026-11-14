package br.com.cadeiralivre.api.barbearia;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import br.com.cadeiralivre.api.horario.dto.HorariosDoPainelResponse;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalHorario;
import br.com.cadeiralivre.api.profissional.ProfissionalHorarioRepository;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.servico.ComboRepository;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.servico.dto.ComboResponse;
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
    private final ProfissionalHorarioRepository profissionalHorarioRepository;
    private final ComboRepository comboRepository;
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

        // o cliente só vê quem atende: os outros saem da lista e dos serviços e combos
        List<Profissional> queAtendem = profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId()).stream()
                .filter(p -> p.isAtivo() && p.isAtende()).toList();
        Set<UUID> idsQueAtendem = queAtendem.stream().map(Profissional::getId).collect(Collectors.toSet());
        Map<UUID, List<ProfissionalHorario>> horarios = profissionalHorarioRepository
                .findAllByProfissionalBarbeariaId(b.getId()).stream()
                .collect(Collectors.groupingBy(h -> h.getProfissional().getId()));

        List<ServicoResponse> servicos = servicoRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(b.getId()).stream()
                .map(ServicoResponse::de)
                .map(s -> new ServicoResponse(s.id(), s.nome(), s.descricao(), s.preco(), s.duracao(),
                        s.profissionalIds().stream().filter(idsQueAtendem::contains).toList(), s.destaque()))
                .toList();
        List<ComboResponse> combos = comboRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(b.getId()).stream()
                .map(ComboResponse::de)
                .map(c -> new ComboResponse(c.id(), c.nome(), c.servicoIds(), c.preco(), c.duracao(),
                        c.profissionalIds().stream().filter(idsQueAtendem::contains).toList()))
                .toList();
        List<PortalPublicoResponse.ProfissionalDoPortal> profissionais = queAtendem.stream()
                .map(p -> new PortalPublicoResponse.ProfissionalDoPortal(p.getId(),
                        p.getNomeSocial() != null ? p.getNomeSocial() : p.getNome(),
                        p.getServicos().stream().filter(Servico::isAtivo).map(Servico::getId).toList(),
                        HorariosDoPainelResponse.semanaDoProfissional(horarios.getOrDefault(p.getId(), List.of()))))
                .toList();

        return PortalPublicoResponse.de(b,
                fotoRepository.findAllByBarbeariaIdOrderByOrdem(b.getId()).stream().map(BarbeariaFoto::getImagem).toList(),
                HorariosDoPainelResponse.semanaDoSalao(horarioRepository.findAllByBarbeariaId(b.getId())),
                servicos, combos, profissionais);
    }

    /**
     * Busca de salões publicados (tela "Encontre seu salão"). A cidade compara o começo do nome, sem acento
     * ("goi" acha Goiânia, mas "goiania" não acha "Aparecida de Goiânia"); a categoria "todas" não filtra;
     * o serviço e o nome procuram o texto em qualquer parte.
     */
    @Transactional(readOnly = true)
    public List<PortalPublicoResponse.SalaoDaBusca> buscar(String cidade, String categoria, String servico, String nome) {
        String cidadeBuscada = semAcento(antesDaVirgula(cidade));
        String servicoBuscado = semAcento(servico);
        String nomeBuscado = semAcento(nome);
        boolean filtraCategoria = categoria != null && !categoria.isBlank() && !"todas".equals(categoria);

        return barbeariaRepository.findAllByPublicoTrueOrderByNomeFantasia().stream()
                .filter(b -> semAcento(antesDaVirgula(b.getCidade())).startsWith(cidadeBuscada))
                .filter(b -> !filtraCategoria || categoria.equals(b.getCategoria()))
                .filter(b -> semAcento(b.getNomeFantasia()).contains(nomeBuscado))
                .filter(b -> servicoBuscado.isEmpty() || servicoRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(b.getId())
                        .stream().anyMatch(s -> semAcento(s.getNome()).contains(servicoBuscado)))
                .map(PortalPublicoResponse.SalaoDaBusca::de)
                .toList();
    }

    // "Goiânia, GO" -> "goiania"
    private String antesDaVirgula(String texto) {
        return texto == null ? "" : texto.split(",")[0];
    }

    private String semAcento(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
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

    /**
     * Regra de publicação (RF09): o cliente precisa ter o que escolher. Devolve o que falta; lista vazia = pode publicar.
     * Só contam serviços ativos e profissionais ativos que atendem clientes.
     */
    @Transactional(readOnly = true)
    public List<String> problemasParaPublicar() {
        return problemasParaPublicar(buscarDoTenant());
    }

    /** Liga ou desliga o portal público. Para ligar, a regra de publicação tem que passar. */
    @Transactional
    public boolean alterarPublicacao(boolean publico) {
        Barbearia b = buscarDoTenant();
        if (publico) {
            List<String> problemas = problemasParaPublicar(b);
            if (!problemas.isEmpty()) throw ApiException.invalido("O portal ainda não pode ficar público.", problemas);
        }
        if (b.isPublico() != publico) {
            b.setPublico(publico);
            // RNF09: publicar e ocultar ficam registrados com o usuário e a hora
            auditoriaService.registrar(publico ? AcaoAuditoria.PORTAL_PUBLICADO : AcaoAuditoria.PORTAL_OCULTADO, null);
        }
        return b.isPublico();
    }

    private List<String> problemasParaPublicar(Barbearia b) {
        List<Servico> servicos = servicoRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(b.getId());
        List<Profissional> queAtendem = profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId()).stream()
                .filter(p -> p.isAtivo() && p.isAtende())
                .toList();

        List<String> problemas = new ArrayList<>();
        if (servicos.isEmpty()) problemas.add("Cadastre ao menos um serviço.");
        if (queAtendem.isEmpty()) {
            problemas.add("Cadastre ao menos um profissional que atenda clientes (pode ser você mesmo, em Profissionais).");
        }
        for (Servico s : servicos) {
            boolean temQuemFaca = queAtendem.stream()
                    .anyMatch(p -> p.getServicos().stream().anyMatch(feito -> feito.getId().equals(s.getId())));
            if (!temQuemFaca) problemas.add("O serviço \"" + s.getNome() + "\" não tem nenhum profissional vinculado.");
        }
        return problemas;
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
