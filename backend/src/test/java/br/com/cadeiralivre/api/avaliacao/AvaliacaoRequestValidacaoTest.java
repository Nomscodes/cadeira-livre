package br.com.cadeiralivre.api.avaliacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.cadeiralivre.api.avaliacao.dto.AvaliacaoRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Confere as validações da avaliação, as mesmas que o @Valid aplica no controller. */
class AvaliacaoRequestValidacaoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void notaDe1a5SemComentarioPassaNaValidacao() {
        assertTrue(validator.validate(new AvaliacaoRequest(1, null)).isEmpty());
        assertTrue(validator.validate(new AvaliacaoRequest(5, null)).isEmpty());
    }

    @Test
    void notaForaDe1a5EhRecusada() {
        Set<ConstraintViolation<AvaliacaoRequest>> notaZero = validator.validate(new AvaliacaoRequest(0, null));
        Set<ConstraintViolation<AvaliacaoRequest>> notaSeis = validator.validate(new AvaliacaoRequest(6, null));

        assertEquals("a nota vai de 1 a 5", notaZero.iterator().next().getMessage());
        assertEquals("a nota vai de 1 a 5", notaSeis.iterator().next().getMessage());
    }

    @Test
    void avaliacaoSemNotaEhRecusada() {
        Set<ConstraintViolation<AvaliacaoRequest>> erros = validator.validate(new AvaliacaoRequest(null, "Muito bom"));

        assertEquals(1, erros.size());
        assertEquals("nota", erros.iterator().next().getPropertyPath().toString());
    }

    @Test
    void comentarioComMaisDe500CaracteresEhRecusado() {
        Set<ConstraintViolation<AvaliacaoRequest>> erros = validator.validate(new AvaliacaoRequest(5, "a".repeat(501)));

        assertEquals(1, erros.size());
        assertEquals("comentario", erros.iterator().next().getPropertyPath().toString());
    }
}
