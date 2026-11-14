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
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
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
        assertNull(portal.logoUrl());
        assertTrue(portal.servicos().isEmpty());
        verifyNoInteractions(servicoRepository);
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

    // ---------- métodos de apoio ----------

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
