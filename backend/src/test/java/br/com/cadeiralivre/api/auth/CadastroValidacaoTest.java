package br.com.cadeiralivre.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.cadeiralivre.api.auth.dto.CadastroClienteRequest;
import br.com.cadeiralivre.api.auth.dto.CadastroDonoRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Confere as validações dos cadastros, as mesmas que o @Valid aplica no controller. */
class CadastroValidacaoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void clienteSemAceitarOsTermosNaoPodeSeCadastrar() {
        var pedido = new CadastroClienteRequest("Cliente", "cliente@teste.com", "62999999999", "12345678", false);

        Set<ConstraintViolation<CadastroClienteRequest>> erros = validator.validate(pedido);

        assertEquals(1, erros.size());
        assertEquals("aceitouTermos", erros.iterator().next().getPropertyPath().toString());
    }

    @Test
    void donoSemAceitarOsTermosNaoPodeSeCadastrar() {
        var pedido = new CadastroDonoRequest("Dono", "Barbearia", "12345678901", "dono@teste.com", "12345678", false);

        Set<ConstraintViolation<CadastroDonoRequest>> erros = validator.validate(pedido);

        assertEquals(1, erros.size());
        assertEquals("aceitouTermos", erros.iterator().next().getPropertyPath().toString());
    }

    @Test
    void clienteQueAceitouOsTermosPassaNaValidacao() {
        var pedido = new CadastroClienteRequest("Cliente", "cliente@teste.com", "62999999999", "12345678", true);

        assertTrue(validator.validate(pedido).isEmpty());
    }
}
