package br.com.cadeiralivre.api.barbearia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.cadeiralivre.api.barbearia.dto.PortalRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Confere as validações da tela "Meu portal", as mesmas que o @Valid aplica no controller. */
class PortalRequestValidacaoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void portalCompletoComoOFrontEnviaPassaNaValidacao() {
        var pedido = new PortalRequest("Barbearia Naoki", "verde-escuro", "Cortes desde 2020.", "(62) 98888-7777",
                "barbearia", "Goiânia, GO", "Rua 10, 100", "instagram.com/naoki",
                "data:image/png;base64,iVBORw0KGgo=", "data:image/jpeg;base64,/9j/4AAQ",
                List.of("data:image/webp;base64,UklGRg=="), List.of(UUID.randomUUID()));

        assertTrue(validator.validate(pedido).isEmpty());
    }

    @Test
    void portalSoComONomePassaNaValidacao() {
        assertTrue(validator.validate(soComNome("Barbearia Naoki")).isEmpty());
    }

    @Test
    void nomeEmBrancoEhRecusado() {
        assertUmErroEm("nome", soComNome("  "));
    }

    @Test
    void temaQueNaoExisteEhRecusado() {
        assertUmErroEm("temaId", new PortalRequest("Barbearia Naoki", "rosa-choque", null, null, null, null, null,
                null, null, null, null, null));
    }

    @Test
    void categoriaForaDaListaEhRecusada() {
        assertUmErroEm("categoria", new PortalRequest("Barbearia Naoki", null, null, null, "pet shop", null, null,
                null, null, null, null, null));
    }

    @Test
    void logoQueNaoEhImagemEhRecusada() {
        assertUmErroEm("logo", comLogo("http://site.com/logo.png"));
    }

    @Test
    void logoMaiorQue1MbEhRecusada() {
        assertUmErroEm("logo", comLogo("data:image/png;base64," + "A".repeat(1_400_000)));
    }

    @Test
    void fotoDaGaleriaQueNaoEhImagemEhRecusada() {
        var pedido = new PortalRequest("Barbearia Naoki", null, null, null, null, null, null, null, null, null,
                List.of("data:image/png;base64,AAAA", "foto.png"), null);

        Set<ConstraintViolation<PortalRequest>> erros = validator.validate(pedido);

        assertEquals(1, erros.size());
        assertTrue(erros.iterator().next().getPropertyPath().toString().startsWith("galeria"));
    }

    private void assertUmErroEm(String campo, PortalRequest pedido) {
        Set<ConstraintViolation<PortalRequest>> erros = validator.validate(pedido);

        assertEquals(1, erros.size());
        assertEquals(campo, erros.iterator().next().getPropertyPath().toString());
    }

    private PortalRequest soComNome(String nome) {
        return new PortalRequest(nome, null, null, null, null, null, null, null, null, null, null, null);
    }

    private PortalRequest comLogo(String logo) {
        return new PortalRequest("Barbearia Naoki", null, null, null, null, null, null, null, logo, null, null, null);
    }
}
