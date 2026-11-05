package br.com.cadeiralivre.api.barbearia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.cadeiralivre.api.barbearia.dto.PortalRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Confere as validações da customização do portal, as mesmas que o @Valid aplica no controller. */
class PortalRequestValidacaoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void logoEmPngPassaNaValidacao() {
        assertTrue(validator.validate(portalComLogo("data:image/png;base64,iVBORw0KGgo=")).isEmpty());
    }

    @Test
    void portalSemLogoPassaNaValidacao() {
        assertTrue(validator.validate(portalComLogo(null)).isEmpty());
    }

    @Test
    void logoQueNaoEhImagemEhRecusada() {
        Set<ConstraintViolation<PortalRequest>> erros = validator.validate(portalComLogo("http://site.com/logo.png"));

        assertEquals(1, erros.size());
        assertEquals("logoUrl", erros.iterator().next().getPropertyPath().toString());
    }

    @Test
    void logoMaiorQue1MbEhRecusada() {
        String logoGrande = "data:image/png;base64," + "A".repeat(1_400_000);

        Set<ConstraintViolation<PortalRequest>> erros = validator.validate(portalComLogo(logoGrande));

        assertEquals(1, erros.size());
        assertEquals("logoUrl", erros.iterator().next().getPropertyPath().toString());
    }

    @Test
    void corForaDoFormatoHexadecimalEhRecusada() {
        var pedido = new PortalRequest("Barbearia Teste", "vermelho", null, null, null, null);

        Set<ConstraintViolation<PortalRequest>> erros = validator.validate(pedido);

        assertEquals(1, erros.size());
        assertEquals("corPrimaria", erros.iterator().next().getPropertyPath().toString());
    }

    private PortalRequest portalComLogo(String logo) {
        return new PortalRequest("Barbearia Teste", "#9B1C2E", logo, null, null, null);
    }
}
