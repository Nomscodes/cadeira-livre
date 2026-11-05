package br.com.cadeiralivre.api.profissional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import br.com.cadeiralivre.api.profissional.dto.ProfissionalRequest;
import br.com.cadeiralivre.api.profissional.dto.ProfissionalResponse;
import br.com.cadeiralivre.api.servico.Servico;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class ProfissionalServiceTest {

    @Mock
    private ProfissionalRepository profissionalRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @InjectMocks
    private ProfissionalService profissionalService;

    private final UUID minhaBarbearia = UUID.randomUUID();

    private Barbearia barbearia;
    private Servico corte;
    private Servico barba;

    @BeforeEach
    void preparar() {
        // simula o que o JwtAuthenticationFilter faz com o token do dono
        TenantContext.set(minhaBarbearia);

        barbearia = new Barbearia();
        barbearia.setId(minhaBarbearia);
        corte = servico("Corte");
        barba = servico("Barba");
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    // ---------- isolamento por barbearia ----------

    @Test
    void listarBuscaSoOsProfissionaisDaBarbeariaDoToken() {
        profissionalService.listar();

        verify(profissionalRepository).findAllByBarbeariaIdOrderByNome(minhaBarbearia);
    }

    @Test
    void editarProfissionalDeOutraBarbeariaDevolve404() {
        UUID profissionalDeOutraBarbearia = UUID.randomUUID();
        when(profissionalRepository.findByIdAndBarbeariaId(profissionalDeOutraBarbearia, minhaBarbearia))
                .thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> profissionalService.atualizar(profissionalDeOutraBarbearia, pedido(Set.of(), null)));

        assertEquals(404, erro.getStatus().value());
    }

    @Test
    void servicoDeOutraBarbeariaDevolve422ENaoSalva() {
        UUID servicoDeOutraBarbearia = UUID.randomUUID();
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);
        when(servicoRepository.findAllByIdInAndBarbeariaId(Set.of(servicoDeOutraBarbearia), minhaBarbearia))
                .thenReturn(List.of());

        ApiException erro = assertThrows(ApiException.class,
                () -> profissionalService.criar(pedido(Set.of(servicoDeOutraBarbearia), null)));

        assertEquals(422, erro.getStatus().value());
        verify(profissionalRepository, never()).save(any());
    }

    @Test
    void semBarbeariaNoTokenDevolve403() {
        TenantContext.clear();

        ApiException erro = assertThrows(ApiException.class, () -> profissionalService.listar());

        assertEquals(403, erro.getStatus().value());
    }

    // ---------- cadastro e edição ----------

    @Test
    void criarVinculaABarbeariaDoTokenEOsServicosEscolhidos() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);
        when(servicoRepository.findAllByIdInAndBarbeariaId(Set.of(corte.getId(), barba.getId()), minhaBarbearia))
                .thenReturn(List.of(corte, barba));
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(chamada -> chamada.getArgument(0));

        ProfissionalResponse criado = profissionalService.criar(new ProfissionalRequest(
                "  Rafael Lima  ", "62999990001", Set.of(corte.getId(), barba.getId()), null));

        ArgumentCaptor<Profissional> salvo = ArgumentCaptor.forClass(Profissional.class);
        verify(profissionalRepository).save(salvo.capture());
        assertEquals(minhaBarbearia, salvo.getValue().getBarbearia().getId());
        assertEquals("Rafael Lima", criado.nome());
        assertEquals(2, criado.servicoIds().size());
        assertTrue(criado.ativo());
    }

    @Test
    void criarSemServicosESemTelefoneFuncionaNormalmente() {
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);
        when(profissionalRepository.save(any(Profissional.class))).thenAnswer(chamada -> chamada.getArgument(0));

        ProfissionalResponse criado = profissionalService.criar(new ProfissionalRequest("Rafael", "  ", null, null));

        assertTrue(criado.servicoIds().isEmpty());
        assertNull(criado.telefone());
        verify(servicoRepository, never()).findAllByIdInAndBarbeariaId(any(), any());
    }

    @Test
    void editarTrocaOsServicosDoProfissional() {
        Profissional rafael = profissionalExistente(true);
        rafael.getServicos().add(corte);
        when(servicoRepository.findAllByIdInAndBarbeariaId(Set.of(barba.getId()), minhaBarbearia))
                .thenReturn(List.of(barba));

        ProfissionalResponse editado = profissionalService.atualizar(rafael.getId(), pedido(Set.of(barba.getId()), null));

        assertEquals(List.of(barba.getId()), editado.servicoIds());
    }

    // ---------- exclusão lógica ----------

    @Test
    void excluirDesativaOProfissionalEmVezDeApagar() {
        Profissional rafael = profissionalExistente(true);

        profissionalService.excluir(rafael.getId());

        assertFalse(rafael.isAtivo());
        verify(profissionalRepository, never()).delete(any());
    }

    @Test
    void editarComAtivoTrueReativaOProfissional() {
        Profissional rafael = profissionalExistente(false);

        ProfissionalResponse editado = profissionalService.atualizar(rafael.getId(), pedido(Set.of(), true));

        assertTrue(editado.ativo());
    }

    @Test
    void editarSemInformarAtivoMantemComoEsta() {
        Profissional rafael = profissionalExistente(false);

        ProfissionalResponse editado = profissionalService.atualizar(rafael.getId(), pedido(Set.of(), null));

        assertFalse(editado.ativo());
    }

    // ---------- métodos de apoio ----------

    private Servico servico(String nome) {
        Servico s = new Servico();
        s.setId(UUID.randomUUID());
        s.setBarbearia(barbearia);
        s.setNome(nome);
        s.setPreco(new BigDecimal("40.00"));
        s.setDuracaoMinutos(30);
        return s;
    }

    private Profissional profissionalExistente(boolean ativo) {
        Profissional p = new Profissional();
        p.setId(UUID.randomUUID());
        p.setBarbearia(barbearia);
        p.setNome("Rafael Lima");
        p.setAtivo(ativo);
        when(profissionalRepository.findByIdAndBarbeariaId(p.getId(), minhaBarbearia)).thenReturn(Optional.of(p));
        return p;
    }

    private ProfissionalRequest pedido(Set<UUID> servicoIds, Boolean ativo) {
        return new ProfissionalRequest("Rafael Lima", null, servicoIds, ativo);
    }
}
