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

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.cadeiralivre.api.auditoria.AcaoAuditoria;
import br.com.cadeiralivre.api.auditoria.AuditoriaService;
import br.com.cadeiralivre.api.barbearia.dto.PortalPublicoResponse;
import br.com.cadeiralivre.api.barbearia.dto.PortalRequest;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.servico.ServicoRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class BarbeariaServiceTest {

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @Mock
    private ServicoRepository servicoRepository;

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
        when(barbeariaRepository.findBySlug("barbearia-teste")).thenReturn(Optional.of(oculta));

        PortalPublicoResponse portal = barbeariaService.portalPublico("barbearia-teste");

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

    // ---------- customização e publicação (painel) ----------

    @Test
    void alterarOPortalRegistraAuditoria() {
        Barbearia b = logarComoDonoDe(barbearia(false));

        barbeariaService.atualizar(pedido("Barbearia Nova", null));

        assertEquals("Barbearia Nova", b.getNomeFantasia());
        verify(auditoriaService).registrar(eq(AcaoAuditoria.PORTAL_ALTERADO), anyString());
        verify(auditoriaService, never()).registrar(eq(AcaoAuditoria.PORTAL_PUBLICADO), any());
    }

    @Test
    void naoDaPraPublicarSemServicoAtivo() {
        Barbearia b = logarComoDonoDe(barbearia(false));
        when(servicoRepository.existsByBarbeariaIdAndAtivoTrue(b.getId())).thenReturn(false);

        ApiException erro = assertThrows(ApiException.class,
                () -> barbeariaService.atualizar(pedido("Barbearia Teste", true)));

        assertEquals(422, erro.getStatus().value());
        assertFalse(b.isPublico());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void publicarComServicoAtivoColocaOPortalNoAr() {
        Barbearia b = logarComoDonoDe(barbearia(false));
        when(servicoRepository.existsByBarbeariaIdAndAtivoTrue(b.getId())).thenReturn(true);

        barbeariaService.atualizar(pedido("Barbearia Teste", true));

        assertTrue(b.isPublico());
        verify(auditoriaService).registrar(eq(AcaoAuditoria.PORTAL_PUBLICADO), any());
    }

    @Test
    void ocultarOPortalRegistraAuditoria() {
        Barbearia b = logarComoDonoDe(barbearia(true));

        barbeariaService.atualizar(pedido("Barbearia Teste", false));

        assertFalse(b.isPublico());
        verify(auditoriaService).registrar(eq(AcaoAuditoria.PORTAL_OCULTADO), any());
    }

    // ---------- métodos de apoio ----------

    private Barbearia barbearia(boolean publico) {
        Barbearia b = new Barbearia();
        b.setId(UUID.randomUUID());
        b.setSlug("barbearia-teste");
        b.setNomeFantasia("Barbearia Teste");
        b.setPublico(publico);
        return b;
    }

    private Barbearia logarComoDonoDe(Barbearia b) {
        TenantContext.set(b.getId());
        when(barbeariaRepository.findById(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    private PortalRequest pedido(String nome, Boolean publico) {
        return new PortalRequest(nome, "#9B1C2E", null, null, null, publico);
    }
}
