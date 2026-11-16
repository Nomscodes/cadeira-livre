package br.com.cadeiralivre.api.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ComboTest {

    @Test
    void duracaoDoComboEhASomaDosServicos() {
        Combo combo = comboDoGabriel();

        assertEquals(50, combo.duracaoMinutos()); // 30 do corte + 20 da barba
    }

    @Test
    void somaDosServicosMostraQuantoOClienteEconomiza() {
        Combo combo = comboDoGabriel();
        combo.setPreco(new BigDecimal("60.00"));

        assertEquals(new BigDecimal("75.00"), combo.somaDosServicos());
        assertEquals(new BigDecimal("15.00"), combo.somaDosServicos().subtract(combo.getPreco()));
    }

    @Test
    void comboSemServicoTemDuracaoESomaZero() {
        Combo combo = new Combo();

        assertEquals(0, combo.duracaoMinutos());
        assertEquals(BigDecimal.ZERO, combo.somaDosServicos());
    }

    @Test
    void servicoNovoNasceEmDestaque() {
        assertTrue(new Servico().isDestaque());
    }

    private Combo comboDoGabriel() {
        Combo combo = new Combo();
        combo.setNome("Corte + Barba do Gabriel Naoki");
        combo.getServicos().add(servico("Corte", "45.00", 30));
        combo.getServicos().add(servico("Barba", "30.00", 20));
        return combo;
    }

    private Servico servico(String nome, String preco, int minutos) {
        Servico s = new Servico();
        s.setId(UUID.randomUUID());
        s.setNome(nome);
        s.setPreco(new BigDecimal(preco));
        s.setDuracaoMinutos(minutos);
        return s;
    }
}
