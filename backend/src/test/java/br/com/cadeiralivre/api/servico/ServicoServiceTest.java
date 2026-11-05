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
import br.com.cadeiralivre.api.servico.dto.ServicoRequest;
import br.com.cadeiralivre.api.servico.dto.ServicoResponse;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class ServicoServiceTest {

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @InjectMocks
    private ServicoService servicoService;

    private final UUID minhaBarbearia = UUID.randomUUID();

    @BeforeEach
    void logarComoDono() {
        // simula o que o JwtAuthenticationFilter faz com o token do dono
        TenantContext.set(minhaBarbearia);
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    // ---------- isolamento por barbearia ----------

    @Test
    void listarBuscaSoOsServicosDaBarbeariaDoToken() {
        servicoService.listar();

        verify(servicoRepository).findAllByBarbeariaIdOrderByNome(minhaBarbearia);
    }

    @Test
    void criarServicoVinculaABarbeariaDoTokenETiraEspacosDoNome() {
        Barbearia barbearia = new Barbearia();
        barbearia.setId(minhaBarbearia);
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);
        when(servicoRepository.save(any(Servico.class))).thenAnswer(chamada -> chamada.getArgument(0));

        ServicoResponse criado = servicoService.criar(
                new ServicoRequest("  Corte  ", "Corte na tesoura", new BigDecimal("40.00"), 30, null));

        ArgumentCaptor<Servico> salvo = ArgumentCaptor.forClass(Servico.class);
        verify(servicoRepository).save(salvo.capture());
        assertEquals(minhaBarbearia, salvo.getValue().getBarbearia().getId());
        assertEquals("Corte", criado.nome());
        assertEquals(30, criado.duracaoMinutos());
        assertTrue(criado.ativo());
    }

    @Test
    void editarServicoDeOutraBarbeariaDevolve404() {
        UUID servicoDeOutraBarbearia = UUID.randomUUID();
        when(servicoRepository.findByIdAndBarbeariaId(servicoDeOutraBarbearia, minhaBarbearia))
                .thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class, () -> servicoService.atualizar(
                servicoDeOutraBarbearia, pedido(null)));

        assertEquals(404, erro.getStatus().value());
    }

    @Test
    void excluirServicoDeOutraBarbeariaDevolve404ENaoApagaNada() {
        UUID servicoDeOutraBarbearia = UUID.randomUUID();
        when(servicoRepository.findByIdAndBarbeariaId(servicoDeOutraBarbearia, minhaBarbearia))
                .thenReturn(Optional.empty());

        ApiException erro = assertThrows(ApiException.class,
                () -> servicoService.excluir(servicoDeOutraBarbearia));

        assertEquals(404, erro.getStatus().value());
        verify(servicoRepository, never()).delete(any());
    }

    @Test
    void semBarbeariaNoTokenDevolve403() {
        TenantContext.clear();

        ApiException erro = assertThrows(ApiException.class, () -> servicoService.listar());

        assertEquals(403, erro.getStatus().value());
    }

    // ---------- exclusão lógica ----------

    @Test
    void excluirDesativaOServicoEmVezDeApagar() {
        Servico corte = servicoExistente(true, false);

        servicoService.excluir(corte.getId());

        assertFalse(corte.isAtivo());
        verify(servicoRepository, never()).delete(any());
    }

    @Test
    void naoDaPraDesativarOUltimoServicoDeUmPortalPublicado() {
        Servico corte = servicoExistente(true, true);
        when(servicoRepository.countByBarbeariaIdAndAtivoTrue(minhaBarbearia)).thenReturn(1L);

        ApiException erro = assertThrows(ApiException.class, () -> servicoService.excluir(corte.getId()));

        assertEquals(422, erro.getStatus().value());
        assertTrue(corte.isAtivo());
    }

    @Test
    void portalPublicadoComOutrosServicosAtivosPermiteDesativar() {
        Servico corte = servicoExistente(true, true);
        when(servicoRepository.countByBarbeariaIdAndAtivoTrue(minhaBarbearia)).thenReturn(2L);

        servicoService.excluir(corte.getId());

        assertFalse(corte.isAtivo());
    }

    @Test
    void editarComAtivoTrueReativaOServico() {
        Servico corte = servicoExistente(false, false);

        ServicoResponse editado = servicoService.atualizar(corte.getId(), pedido(true));

        assertTrue(editado.ativo());
    }

    @Test
    void editarSemInformarAtivoMantemComoEsta() {
        Servico corte = servicoExistente(false, false);

        ServicoResponse editado = servicoService.atualizar(corte.getId(), pedido(null));

        assertFalse(editado.ativo());
    }

    // ---------- métodos de apoio ----------

    private Servico servicoExistente(boolean ativo, boolean portalPublicado) {
        Barbearia barbearia = new Barbearia();
        barbearia.setId(minhaBarbearia);
        barbearia.setPublico(portalPublicado);

        Servico s = new Servico();
        s.setId(UUID.randomUUID());
        s.setBarbearia(barbearia);
        s.setNome("Corte");
        s.setPreco(new BigDecimal("40.00"));
        s.setDuracaoMinutos(30);
        s.setAtivo(ativo);
        when(servicoRepository.findByIdAndBarbeariaId(s.getId(), minhaBarbearia)).thenReturn(Optional.of(s));
        return s;
    }

    private ServicoRequest pedido(Boolean ativo) {
        return new ServicoRequest("Corte", null, new BigDecimal("40.00"), 30, ativo);
    }
}
