package br.com.cadeiralivre.api.barbearia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.auditoria.AcaoAuditoria;
import br.com.cadeiralivre.api.auditoria.AuditoriaService;
import br.com.cadeiralivre.api.barbearia.dto.PortalPublicoResponse;
import br.com.cadeiralivre.api.barbearia.dto.PortalRequest;
import br.com.cadeiralivre.api.barbearia.dto.PortalResponse;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.HorarioFuncionamento;
import br.com.cadeiralivre.api.horario.HorarioFuncionamentoRepository;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalHorario;
import br.com.cadeiralivre.api.profissional.ProfissionalHorarioRepository;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.servico.ComboRepository;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class BarbeariaServiceTest {

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @Mock
    private BarbeariaFotoRepository fotoRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private HorarioFuncionamentoRepository horarioRepository;

    @Mock
    private ProfissionalHorarioRepository profissionalHorarioRepository;

    @Mock
    private ComboRepository comboRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private BarbeariaService barbeariaService;

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    // ---------- criação e portal público ----------

    @Test
    void novaBarbeariaGanhaSlugENasceOculta() {
        when(barbeariaRepository.existsBySlug("barbearia-do-rafael")).thenReturn(false);
        when(barbeariaRepository.save(any(Barbearia.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Barbearia criada = barbeariaService.criar("Barbearia do Rafael", "12345678901");

        assertEquals("barbearia-do-rafael", criada.getSlug());
        assertFalse(criada.isPublico());
    }

    @Test
    void slugJaUsadoGanhaNumeroNoFim() {
        when(barbeariaRepository.existsBySlug("barbearia-teste")).thenReturn(true);
        when(barbeariaRepository.existsBySlug("barbearia-teste-2")).thenReturn(false);
        when(barbeariaRepository.save(any(Barbearia.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Barbearia criada = barbeariaService.criar("Barbearia Teste", "12345678901");

        assertEquals("barbearia-teste-2", criada.getSlug());
    }

    @Test
    void portalAindaNaoPublicadoMostraSoONome() {
        Barbearia oculta = barbearia(false);
        when(barbeariaRepository.findBySlug("barbearia-naoki")).thenReturn(Optional.of(oculta));

        PortalPublicoResponse portal = barbeariaService.portalPublico("barbearia-naoki");

        assertFalse(portal.publico());
        assertEquals("Barbearia Naoki", portal.nome());
        assertEquals("vermelho-escuro", portal.temaId());
        assertNull(portal.logo());
        assertTrue(portal.servicos().isEmpty());
        verifyNoInteractions(servicoRepository);
    }

    @Test
    void portalPublicadoMostraSoQuemAtendeNosServicosECombos() {
        Barbearia b = barbearia(true);
        b.setTelefone("62999990000");
        when(barbeariaRepository.findBySlug("barbearia-naoki")).thenReturn(Optional.of(b));
        Servico corte = new Servico();
        corte.setId(UUID.randomUUID());
        corte.setNome("Corte");
        corte.setPreco(new BigDecimal("40.00"));
        corte.setDuracaoMinutos(30);
        Profissional gabriel = profissional(b, "Gabriel Naoki", true);
        gabriel.setNomeSocial("Gabriel");
        Profissional soGerencia = profissional(b, "Dono do Salão", true);
        soGerencia.setAtende(false);
        gabriel.getServicos().add(corte);
        corte.getProfissionais().add(gabriel);
        soGerencia.getServicos().add(corte);
        corte.getProfissionais().add(soGerencia);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId())).thenReturn(List.of(soGerencia, gabriel));
        when(servicoRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(b.getId())).thenReturn(List.of(corte));
        when(profissionalHorarioRepository.findAllByProfissionalBarbeariaId(b.getId())).thenReturn(List.of(
                new ProfissionalHorario(gabriel, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0), true)));

        PortalPublicoResponse portal = barbeariaService.portalPublico("barbearia-naoki");

        assertTrue(portal.publico());
        assertEquals("62999990000", portal.whatsapp());
        assertEquals(List.of(gabriel.getId()), portal.servicos().get(0).profissionalIds()); // quem só gerencia sai
        assertEquals(1, portal.profissionais().size());
        PortalPublicoResponse.ProfissionalDoPortal p = portal.profissionais().get(0);
        assertEquals("Gabriel", p.nome()); // o cliente vê o nome social
        assertEquals(List.of(corte.getId()), p.servicoIds());
        assertTrue(p.horarios().get("1").ativo());
        assertEquals(7, portal.horarios().size());
    }

    // ---------- busca de salões ----------

    @Test
    void buscaPelaCidadeComparaOComecoSemAcento() {
        Barbearia goiania = publicada("Barbearia Naoki", "Goiânia, GO", "barbearia");
        Barbearia aparecida = publicada("Naoki Aparecida", "Aparecida de Goiânia, GO", "barbearia");
        when(barbeariaRepository.findAllByPublicoTrueOrderByNomeFantasia()).thenReturn(List.of(goiania, aparecida));

        List<PortalPublicoResponse.SalaoDaBusca> achados = barbeariaService.buscar("goiania", null, null, null);

        assertEquals(List.of("Barbearia Naoki"), achados.stream().map(PortalPublicoResponse.SalaoDaBusca::nome).toList());
        assertEquals(2, barbeariaService.buscar(null, "todas", null, null).size()); // sem filtro
        assertEquals(1, barbeariaService.buscar("GOI", null, null, null).size());
    }

    @Test
    void buscaFiltraPorCategoriaENomeEServico() {
        Barbearia barbearia = publicada("Barbearia Naoki", "Goiânia, GO", "barbearia");
        Barbearia unhas = publicada("Espaço Unhas", "Goiânia, GO", "unhas");
        when(barbeariaRepository.findAllByPublicoTrueOrderByNomeFantasia()).thenReturn(List.of(barbearia, unhas));
        Servico manicure = new Servico();
        manicure.setNome("Manicure");
        when(servicoRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(barbearia.getId())).thenReturn(List.of());
        when(servicoRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(unhas.getId())).thenReturn(List.of(manicure));

        assertEquals("Espaço Unhas", barbeariaService.buscar(null, "unhas", null, null).get(0).nome());
        assertEquals("Barbearia Naoki", barbeariaService.buscar(null, null, null, "naoki").get(0).nome());
        assertEquals("Espaço Unhas", barbeariaService.buscar(null, null, "MANIC", null).get(0).nome());
    }

    @Test
    void portalQueNaoExisteDevolve404() {
        when(barbeariaRepository.findBySlug("nao-existe")).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> barbeariaService.portalPublico("nao-existe"));

        assertEquals(404, erro.getStatus().value());
    }

    // ---------- portal no painel (tela "Meu portal") ----------

    @Test
    void painelRecebeOPortalNoFormatoDoFront() {
        Barbearia b = logarComoDonoDe(barbearia(false));
        b.setTelefone("62999990000");
        b.setCidade("Goiânia, GO");
        Profissional gabriel = profissional(b, "Gabriel Naoki", true);
        Profissional diego = profissional(b, "Diego Santos", false);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId())).thenReturn(List.of(diego, gabriel));
        when(fotoRepository.findAllByBarbeariaIdOrderByOrdem(b.getId())).thenReturn(List.of(
                new BarbeariaFoto(b, "data:image/png;base64,AAAA", 0),
                new BarbeariaFoto(b, "data:image/png;base64,BBBB", 1)));
        when(horarioRepository.findAllByBarbeariaId(b.getId())).thenReturn(List.of(
                horario(b, DayOfWeek.MONDAY, true), horario(b, DayOfWeek.SUNDAY, false)));

        PortalResponse portal = barbeariaService.obterDoPainel();

        assertEquals("Barbearia Naoki", portal.nome());
        assertEquals("62999990000", portal.whatsapp()); // o telefone da barbearia é o WhatsApp do portal
        assertEquals("Goiânia, GO", portal.cidade());
        assertEquals("vermelho-escuro", portal.temaId());
        assertEquals(List.of("data:image/png;base64,AAAA", "data:image/png;base64,BBBB"), portal.galeria());
        assertEquals(List.of(gabriel.getId()), portal.adminIds());
        assertEquals(2, portal.profissionais().size());
        assertEquals("Diego Santos", portal.profissionais().get(0).nome());
        // as chaves vão de "0" (domingo) a "6" (sábado); dia sem horário vem fechado
        assertEquals(7, portal.horarios().size());
        assertEquals(new PortalResponse.Dia(true, "09:00", "18:00"), portal.horarios().get("1"));
        assertFalse(portal.horarios().get("0").ativo());
        assertFalse(portal.horarios().get("3").ativo());
    }

    @Test
    void salvarAtualizaOsDadosDoPortalERegistraAuditoria() {
        Barbearia b = logarComoDonoDe(barbearia(false));

        barbeariaService.atualizar(new PortalRequest("Barbearia Naoki Centro", "azul-pastel", "Cortes desde 2020.",
                "62988887777", "salão de beleza", "Goiânia, GO", "Rua 10, 100", "instagram.com/naoki",
                "data:image/png;base64,AAAA", "data:image/jpeg;base64,BBBB", null, null));

        assertEquals("Barbearia Naoki Centro", b.getNomeFantasia());
        assertEquals("azul-pastel", b.getTemaId());
        assertEquals("salão de beleza", b.getCategoria());
        assertEquals("62988887777", b.getTelefone());
        assertEquals("Goiânia, GO", b.getCidade());
        assertEquals("data:image/png;base64,AAAA", b.getLogoUrl());
        assertEquals("data:image/jpeg;base64,BBBB", b.getCapaUrl());
        assertFalse(b.isPublico()); // salvar não publica: a publicação tem rota própria
        verify(auditoriaService).registrar(eq(AcaoAuditoria.PORTAL_ALTERADO), anyString());
        verify(auditoriaService, never()).registrar(eq(AcaoAuditoria.PORTAL_PUBLICADO), any());
    }

    @Test
    void camposEmBrancoFicamVaziosETemaECategoriaNaoMudamSeNaoVierem() {
        Barbearia b = logarComoDonoDe(barbearia(false));
        b.setSobre("texto antigo");
        b.setCategoria("unhas");

        barbeariaService.atualizar(pedido("Barbearia Naoki"));

        assertNull(b.getSobre());
        assertNull(b.getLogoUrl());
        assertEquals("unhas", b.getCategoria());
        assertEquals("vermelho-escuro", b.getTemaId());
    }

    @Test
    void salvarTrocaAGaleriaInteiraNaOrdemEnviada() {
        Barbearia b = logarComoDonoDe(barbearia(false));
        PortalRequest req = new PortalRequest("Barbearia Naoki", null, null, null, null, null, null, null, null, null,
                List.of("data:image/png;base64,PRIMEIRA", "data:image/png;base64,SEGUNDA"), null);

        barbeariaService.atualizar(req);

        verify(fotoRepository).apagarDaBarbearia(b.getId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BarbeariaFoto>> salvas = ArgumentCaptor.forClass(List.class);
        verify(fotoRepository).saveAll(salvas.capture());
        assertEquals(2, salvas.getValue().size());
        assertEquals("data:image/png;base64,PRIMEIRA", salvas.getValue().get(0).getImagem());
        assertEquals(0, salvas.getValue().get(0).getOrdem());
        assertEquals(1, salvas.getValue().get(1).getOrdem());
    }

    @Test
    void semGaleriaNoPedidoAsFotosNaoMudam() {
        logarComoDonoDe(barbearia(false));

        barbeariaService.atualizar(pedido("Barbearia Naoki"));

        verify(fotoRepository, never()).apagarDaBarbearia(any());
        verify(fotoRepository, never()).saveAll(any());
    }

    @Test
    void administradoresSaoOsProfissionaisEscolhidos() {
        Barbearia b = logarComoDonoDe(barbearia(false));
        Profissional gabriel = profissional(b, "Gabriel Naoki", true);
        Profissional diego = profissional(b, "Diego Santos", false);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId())).thenReturn(List.of(diego, gabriel));

        barbeariaService.atualizar(new PortalRequest("Barbearia Naoki", null, null, null, null, null, null, null,
                null, null, null, List.of(diego.getId())));

        assertTrue(diego.isAdministrador());
        assertFalse(gabriel.isAdministrador());
    }

    @Test
    void administradorQueNaoEhDoSalaoDevolve422() {
        Barbearia b = logarComoDonoDe(barbearia(false));
        Profissional gabriel = profissional(b, "Gabriel Naoki", true);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId())).thenReturn(List.of(gabriel));

        ApiException erro = assertThrows(ApiException.class, () -> barbeariaService.atualizar(new PortalRequest(
                "Barbearia Naoki", null, null, null, null, null, null, null, null, null, null,
                List.of(UUID.randomUUID()))));

        assertEquals(422, erro.getStatus().value());
        assertTrue(gabriel.isAdministrador());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void listaDeAdministradoresVaziaNaoMudaNinguem() {
        logarComoDonoDe(barbearia(false));

        barbeariaService.atualizar(new PortalRequest("Barbearia Naoki", null, null, null, null, null, null, null,
                null, null, null, List.of()));

        // a lista de profissionais só é lida para montar a resposta, uma vez
        verify(profissionalRepository).findAllByBarbeariaIdOrderByNome(any());
    }

    // ---------- publicação (RF09) ----------

    @Test
    void salaoSemServicoNemProfissionalQueAtendaNaoPodePublicar() {
        Barbearia b = logarComoDonoDe(barbearia(false));
        Profissional gabriel = profissional(b, "Gabriel Naoki", true);
        gabriel.setAtende(false); // dono de salão com equipe: só gerencia
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId())).thenReturn(List.of(gabriel));

        List<String> problemas = barbeariaService.problemasParaPublicar();

        assertEquals(List.of("Cadastre ao menos um serviço.",
                "Cadastre ao menos um profissional que atenda clientes (pode ser você mesmo, em Profissionais)."),
                problemas);
    }

    @Test
    void servicoSemProfissionalQueAtendaApareceNosProblemas() {
        Barbearia b = logarComoDonoDe(barbearia(false));
        Servico corte = servico(b, "Corte");
        Servico barba = servico(b, "Barba");
        Profissional gabriel = profissional(b, "Gabriel Naoki", true);
        gabriel.getServicos().add(corte);
        Profissional diego = profissional(b, "Diego Santos", false);
        diego.getServicos().add(barba);
        diego.setAtivo(false); // inativo não conta
        when(servicoRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(b.getId())).thenReturn(List.of(barba, corte));
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId())).thenReturn(List.of(diego, gabriel));

        assertEquals(List.of("O serviço \"Barba\" não tem nenhum profissional vinculado."),
                barbeariaService.problemasParaPublicar());
    }

    @Test
    void naoDaPraPublicarComProblemasE422LevaALista() {
        Barbearia b = logarComoDonoDe(barbearia(false));

        ApiException erro = assertThrows(ApiException.class, () -> barbeariaService.alterarPublicacao(true));

        assertEquals(422, erro.getStatus().value());
        assertEquals("O portal ainda não pode ficar público.", erro.getMessage());
        assertEquals(2, erro.getProblemas().size());
        assertFalse(b.isPublico());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void publicarComTudoCertoColocaOPortalNoArERegistraAuditoria() {
        Barbearia b = logarComoDonoDe(barbearia(false));
        Servico corte = servico(b, "Corte");
        Profissional gabriel = profissional(b, "Gabriel Naoki", true);
        gabriel.getServicos().add(corte);
        when(servicoRepository.findAllByBarbeariaIdAndAtivoTrueOrderByNome(b.getId())).thenReturn(List.of(corte));
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(b.getId())).thenReturn(List.of(gabriel));

        assertTrue(barbeariaService.alterarPublicacao(true));

        assertTrue(b.isPublico());
        verify(auditoriaService).registrar(eq(AcaoAuditoria.PORTAL_PUBLICADO), any());
    }

    @Test
    void ocultarNaoPrecisaDaRegraERegistraAuditoria() {
        Barbearia b = logarComoDonoDe(barbearia(true));

        assertFalse(barbeariaService.alterarPublicacao(false));

        assertFalse(b.isPublico());
        verify(auditoriaService).registrar(eq(AcaoAuditoria.PORTAL_OCULTADO), any());
        verifyNoInteractions(servicoRepository);
    }

    @Test
    void ocultarPortalQueJaEstaOcultoNaoRegistraNada() {
        logarComoDonoDe(barbearia(false));

        assertFalse(barbeariaService.alterarPublicacao(false));

        verifyNoInteractions(auditoriaService);
    }

    // ---------- métodos de apoio ----------

    private Barbearia publicada(String nome, String cidade, String categoria) {
        Barbearia b = barbearia(true);
        b.setNomeFantasia(nome);
        b.setCidade(cidade);
        b.setCategoria(categoria);
        return b;
    }

    private Servico servico(Barbearia b, String nome) {
        Servico s = new Servico();
        s.setId(UUID.randomUUID());
        s.setBarbearia(b);
        s.setNome(nome);
        return s;
    }

    private Barbearia barbearia(boolean publico) {
        Barbearia b = new Barbearia();
        b.setId(UUID.randomUUID());
        b.setSlug("barbearia-naoki");
        b.setNomeFantasia("Barbearia Naoki");
        b.setPublico(publico);
        return b;
    }

    private Barbearia logarComoDonoDe(Barbearia b) {
        TenantContext.set(b.getId());
        when(barbeariaRepository.findById(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    private Profissional profissional(Barbearia b, String nome, boolean administrador) {
        Profissional p = new Profissional();
        p.setId(UUID.randomUUID());
        p.setBarbearia(b);
        p.setNome(nome);
        p.setAdministrador(administrador);
        return p;
    }

    private HorarioFuncionamento horario(Barbearia b, DayOfWeek dia, boolean ativo) {
        HorarioFuncionamento h = new HorarioFuncionamento();
        h.setBarbearia(b);
        h.setDiaSemana(dia);
        h.setHoraAbertura(LocalTime.of(9, 0));
        h.setHoraFechamento(LocalTime.of(18, 0));
        h.setAtivo(ativo);
        return h;
    }

    private PortalRequest pedido(String nome) {
        return new PortalRequest(nome, null, "   ", null, null, null, null, null, null, null, null, null);
    }
}
