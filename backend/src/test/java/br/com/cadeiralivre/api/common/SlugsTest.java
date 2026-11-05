package br.com.cadeiralivre.api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlugsTest {

    @Test
    void transformaONomeEmEnderecoDeUrl() {
        assertEquals("barbearia-do-rafael", Slugs.de("Barbearia do Rafael"));
    }

    @Test
    void tiraAcentosESimbolos() {
        assertEquals("joao-cortes-cia", Slugs.de("  João Cortes & Cia!  "));
    }

    @Test
    void nomeIgualAUmaRotaDoSiteGanhaPrefixo() {
        assertEquals("barbearia-login", Slugs.de("Login"));
    }

    @Test
    void nomeSemLetrasNemNumerosViraBarbearia() {
        assertEquals("barbearia", Slugs.de("!!!"));
    }

    @Test
    void nomeMuitoLongoECortadoSemHifenNoFim() {
        // 69 letras + espaço + mais texto: o corte em 70 cairia logo depois de um hífen
        String nome = "a".repeat(69) + " barbearia";

        String slug = Slugs.de(nome);

        assertTrue(slug.length() <= 70);
        assertFalse(slug.endsWith("-"));
        assertEquals("a".repeat(69), slug);
    }
}
