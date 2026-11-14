package br.com.cadeiralivre.api.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.barbearia.Barbearia;
import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.profissional.Profissional;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.servico.dto.ServicoRequest;
import br.com.cadeiralivre.api.servico.dto.ServicoResponse;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class ServicoServiceTest {

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private ComboRepository comboRepository;

    @InjectMocks
    private ServicoService servicoService;

    private final Barbearia minhaBarbearia = new Barbearia();
    private Profissional gabriel;
    private Profissional diego;

    @BeforeEach
    void logarComoDono() {
        minhaBarbearia.setId(UUID.randomUUID());
        minhaBarbearia.setNomeFantasia("Barbearia Gabriel Naoki");
        // simula o que o JwtAuthenticationFilter faz com o token do dono
        TenantContext.set(minhaBarbearia.getId());
        gabriel = profissional("Gabriel Naoki");
        diego = profissional("Diego Santos");
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    // ---------- isolamento por barbearia ----------

    @Test
    void listarBuscaSoOsServicosAtivosDaBarbeariaDoToken() {
        servicoService.listar();

        verify(servicoRepository).findAllByBarbeariaIdAndAtivoTrueOrderByNome(minhaBarbearia.getId());
    }

    @Test
    void semBarbeariaNoTokenDevolve403() {
        TenantContext.clear();

        ApiException erro = assertThrows(ApiException.class, () -> servicoService.listar());

        assertEquals(403, erro.getStatus().value());
    }

    @Test
    void editarServicoDeOutraBarbeariaDevolve404() {
        UUID deOutraBarbearia = UUID.randomUUID();
        when(servicoRepository.findByIdAndBarbeariaId(deOutraBarbearia, minhaBarbearia.getId())).thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> servicoService.atualizar(deOutraBarbearia, pedido(List.of())));

        assertEquals(404, erro.getStatus().value());
    }

    // ---------- cadastro e profissionais ----------

    @Test
    void criarServicoVinculaABarbeariaEOsProfissionaisEscolhidos() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia.getId())).thenReturn(minhaBarbearia);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(diego, gabriel));

        ServicoResponse criado = servicoService.criar(new ServicoRequest("  Corte  ", "Corte na tesoura",
                new BigDecimal("40.00"), 30, List.of(gabriel.getId())));

        ArgumentCaptor<Servico> salvo = ArgumentCaptor.forClass(Servico.class);
        verify(servicoRepository).save(salvo.capture());
        assertEquals(minhaBarbearia, salvo.getValue().getBarbearia());
        assertEquals("Corte", criado.nome());
        assertEquals(30, criado.duracao());
        assertTrue(criado.destaque());
        assertEquals(List.of(gabriel.getId()), criado.profissionalIds());
        assertTrue(gabriel.getServicos().contains(salvo.getValue())); // o vínculo fica do lado do profissional
        assertTrue(diego.getServicos().isEmpty());
    }

    @Test
    void profissionalDeOutroSalaoDevolve422() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia.getId())).thenReturn(minhaBarbearia);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(gabriel));

        ApiException erro = assertThrows(ApiException.class,
                () -> servicoService.criar(pedido(List.of(UUID.randomUUID()))));

        assertEquals(422, erro.getStatus().value());
        assertEquals("Escolha profissionais do seu salão.", erro.getMessage());
    }

    @Test
    void editarTrocaQuemFazOServico() {
        Servico corte = servicoExistente("Corte", false);
        ligar(diego, corte);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(diego, gabriel));

        ServicoResponse editado = servicoService.atualizar(corte.getId(),
                new ServicoRequest("Corte degradê", null, new BigDecimal("50.00"), 40, List.of(gabriel.getId())));

        assertEquals("Corte degradê", corte.getNome());
        assertEquals(40, editado.duracao());
        assertEquals(List.of(gabriel.getId()), editado.profissionalIds());
        assertFalse(diego.getServicos().contains(corte));
        assertTrue(gabriel.getServicos().contains(corte));
    }

    // ---------- exclusão ----------

    @Test
    void excluirDesativaETiraDosProfissionaisEDosCombos() {
        Servico corte = servicoExistente("Corte", false);
        Servico barba = servico("Barba");
        ligar(gabriel, corte);
        Combo corteEBarba = combo(corte, barba);
        Combo soCorte = combo(corte);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(minhaBarbearia.getId())).thenReturn(List.of(gabriel));
        when(comboRepository.findAllByBarbeariaIdAndAtivoTrueAndServicosId(minhaBarbearia.getId(), corte.getId()))
                .thenReturn(List.of(corteEBarba, soCorte));

        servicoService.excluir(corte.getId());

        assertFalse(corte.isAtivo());
        verify(servicoRepository, never()).delete(any());
        assertFalse(gabriel.getServicos().contains(corte));
        assertEquals(1, corteEBarba.getServicos().size());
        assertTrue(corteEBarba.isAtivo());
        assertFalse(soCorte.isAtivo()); // ficou sem nenhum serviço
    }

    @Test
    void naoDaPraExcluirOUltimoServicoDeUmPortalPublicado() {
        Servico corte = servicoExistente("Corte", true);
        when(servicoRepository.countByBarbeariaIdAndAtivoTrue(minhaBarbearia.getId())).thenReturn(1L);

        ApiException erro = assertThrows(ApiException.class, () -> servicoService.excluir(corte.getId()));

        assertEquals(422, erro.getStatus().value());
        assertTrue(corte.isAtivo());
    }

    @Test
    void portalPublicadoComOutrosServicosAtivosPermiteExcluir() {
        Servico corte = servicoExistente("Corte", true);
        when(servicoRepository.countByBarbeariaIdAndAtivoTrue(minhaBarbearia.getId())).thenReturn(2L);

        servicoService.excluir(corte.getId());

        assertFalse(corte.isAtivo());
    }

    @Test
    void servicoJaExcluidoDevolve404() {
        Servico corte = servicoExistente("Corte", false);
        corte.setAtivo(false);

        ApiException erro = assertThrows(ApiException.class, () -> servicoService.excluir(corte.getId()));

        assertEquals(404, erro.getStatus().value());
    }

    @Test
    void listaDeEscolhaTemSoQuemAtendeEEstaAtivo() {
        Profissional donoQueSoGerencia = profissional("Dono do Salão");
        donoQueSoGerencia.setAtende(false);
        diego.setAtivo(false);
        when(profissionalRepository.findAllByBarbeariaIdOrderByNome(minhaBarbearia.getId()))
                .thenReturn(List.of(diego, donoQueSoGerencia, gabriel));

        assertEquals(List.of(gabriel), servicoService.profissionaisQueAtendem());
    }

    // ---------- métodos de apoio ----------

    private Servico servicoExistente(String nome, boolean portalPublicado) {
        minhaBarbearia.setPublico(portalPublicado);
        Servico s = servico(nome);
        when(servicoRepository.findByIdAndBarbeariaId(s.getId(), minhaBarbearia.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private Servico servico(String nome) {
        Servico s = new Servico();
        s.setId(UUID.randomUUID());
        s.setBarbearia(minhaBarbearia);
        s.setNome(nome);
        s.setPreco(new BigDecimal("40.00"));
        s.setDuracaoMinutos(30);
        return s;
    }

    private Profissional profissional(String nome) {
        Profissional p = new Profissional();
        p.setId(UUID.randomUUID());
        p.setBarbearia(minhaBarbearia);
        p.setNome(nome);
        return p;
    }

    private void ligar(Profissional p, Servico s) {
        p.getServicos().add(s);
        s.getProfissionais().add(p);
    }

    private Combo combo(Servico... servicos) {
        Combo c = new Combo();
        c.setId(UUID.randomUUID());
        c.setBarbearia(minhaBarbearia);
        c.setNome("Combo");
        c.setPreco(new BigDecimal("60.00"));
        c.getServicos().addAll(List.of(servicos));
        return c;
    }

    private ServicoRequest pedido(List<UUID> profissionalIds) {
        return new ServicoRequest("Corte", null, new BigDecimal("40.00"), 30, profissionalIds);
    }
}
