package br.com.cadeiralivre.api.profissional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.dto.ProfissionalRequest;
import br.com.cadeiralivre.api.profissional.dto.ProfissionalResponse;
import br.com.cadeiralivre.api.servico.Combo;
import br.com.cadeiralivre.api.servico.ComboRepository;
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

    // letras e números sem os que se confundem (0/O, 1/I)
    private static final String LETRAS_DO_CONVITE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom SORTEIO = new SecureRandom();

    private final ProfissionalRepository profissionalRepository;
    private final ProfissionalHorarioRepository horarioRepository;
    private final ServicoRepository servicoRepository;
    private final ComboRepository comboRepository;
    private final BarbeariaRepository barbeariaRepository;

    /** Profissionais ativos do salão, com o horário de cada um. */
    @Transactional(readOnly = true)
    public List<ProfissionalResponse> listar() {
        UUID barbeariaId = TenantContext.require();
        Map<UUID, List<ProfissionalHorario>> horarios = horarioRepository.findAllByProfissionalBarbeariaId(barbeariaId)
                .stream().collect(Collectors.groupingBy(h -> h.getProfissional().getId()));
        return profissionalRepository.findAllByBarbeariaIdOrderByNome(barbeariaId).stream()
                .filter(Profissional::isAtivo)
                .map(p -> ProfissionalResponse.de(p, horarios.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    /** Serviços ativos do salão, para a tela escolher o que cada profissional faz. */
    @Transactional(readOnly = true)
    public List<Servico> servicosParaEscolher() {
        return servicoRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(TenantContext.require());
    }

    /** Cadastra o profissional com a semana padrão (seg a sex, 9h às 18h) e gera o código de convite. */
    @Transactional
    public ProfissionalResponse.ComConvite criar(ProfissionalRequest req) {
        Profissional p = new Profissional();
        p.setBarbearia(barbeariaRepository.getReferenceById(TenantContext.require()));
        preencher(p, req);
        p.setCodigoConvite(novoConvite());
        profissionalRepository.save(p);
        List<ProfissionalHorario> semana = horarioRepository.saveAll(ProfissionalHorario.semanaPadrao(p));
        return new ProfissionalResponse.ComConvite(ProfissionalResponse.de(p, semana), p.getCodigoConvite());
    }

    @Transactional
    public ProfissionalResponse.ComConvite atualizar(UUID id, ProfissionalRequest req) {
        Profissional p = buscar(id);
        preencher(p, req);
        return new ProfissionalResponse.ComConvite(
                ProfissionalResponse.de(p, horarioRepository.findAllByProfissionalId(p.getId())), null);
    }

    /** O DELETE desativa o profissional e o tira dos serviços e dos combos. O único administrador não pode sair. */
    @Transactional
    public void excluir(UUID id) {
        Profissional p = buscar(id);
        if (p.isAdministrador() && administradoresAtivos() == 1) {
            throw ApiException.invalido("Este profissional é o administrador do salão. Transfira o cargo antes de remover.");
        }
        p.setAtivo(false);
        p.setCodigoConvite(null);
        p.getServicos().clear();
        tirarDosCombos(p);
    }

    private Profissional buscar(UUID id) {
        return profissionalRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .filter(Profissional::isAtivo)
                .orElseThrow(() -> ApiException.naoEncontrado("Profissional não encontrado."));
    }

    private void preencher(Profissional p, ProfissionalRequest req) {
        String whatsapp = textoOuNulo(req.whatsapp());
        boolean encaminhar = Boolean.TRUE.equals(req.encaminhar());
        if (encaminhar && (whatsapp == null || whatsapp.replaceAll("\\D", "").length() < 10)) {
            throw ApiException.invalido("Para ativar o encaminhamento, informe o WhatsApp do profissional.");
        }
        boolean atende = !Boolean.FALSE.equals(req.atende());

        p.setNome(req.nome().trim());
        p.setNomeSocial(textoOuNulo(req.nomeSocial()));
        p.setTelefone(whatsapp);
        p.setEncaminhar(encaminhar);
        p.setAtende(atende);
        p.getServicos().clear();
        // quem não atende clientes não faz serviços nem combos (continua na equipe)
        if (atende) {
            p.getServicos().addAll(servicosDaBarbearia(req.servicoIds()));
        } else if (p.getId() != null) {
            tirarDosCombos(p);
        }
    }

    private void tirarDosCombos(Profissional p) {
        for (Combo combo : comboRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(TenantContext.require())) {
            combo.getProfissionais().removeIf(outro -> outro.getId().equals(p.getId()));
        }
    }

    private long administradoresAtivos() {
        return profissionalRepository.findAllByBarbeariaIdOrderByNome(TenantContext.require()).stream()
                .filter(p -> p.isAtivo() && p.isAdministrador()).count();
    }

    // só aceita serviços ativos da própria barbearia (RNF05)
    private List<Servico> servicosDaBarbearia(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Servico> encontrados = servicoRepository.findAllByIdInAndBarbeariaId(ids, TenantContext.require()).stream()
                .filter(Servico::isAtivo).toList();
        if (encontrados.size() != ids.size()) {
            throw ApiException.invalido("Um ou mais serviços informados não existem nesta barbearia.");
        }
        return encontrados;
    }

    // código de 6 letras e números; a coluna é única no banco
    private String novoConvite() {
        StringBuilder codigo = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            codigo.append(LETRAS_DO_CONVITE.charAt(SORTEIO.nextInt(LETRAS_DO_CONVITE.length())));
        }
        return codigo.toString();
    }

    private String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
