package br.com.cadeiralivre.api.barbearia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** Os valores padrão da entidade têm que ser os mesmos da migration V13 (são usados no cadastro do dono). */
class BarbeariaTest {

    @Test
    void novaBarbeariaNasceComOsPadroesDoPortal() {
        Barbearia barbearia = new Barbearia();
        barbearia.setNomeFantasia("Barbearia Gabriel Naoki");

        assertEquals("barbearia", barbearia.getCategoria());
        assertEquals("vermelho-escuro", barbearia.getTemaId());
        assertEquals("local", barbearia.getAtendimento());
        assertEquals("autonomo", barbearia.getAtuacao());
        assertFalse(barbearia.isFuncionariosEditamHorario());
    }

    @Test
    void clientePodeCancelarAte4HorasAntesPorPadrao() {
        assertEquals(4, new Barbearia().getCancelamentoHoras());
    }

    @Test
    void portalComecaOcultoESemOsDadosQueODonoPreenche() {
        Barbearia barbearia = new Barbearia();

        assertFalse(barbearia.isPublico());
        assertEquals("#9B1C2E", barbearia.getCorPrimaria()); // mesma cor do tema vermelho escuro
        assertNull(barbearia.getCidade());
        assertNull(barbearia.getSobre());
        assertNull(barbearia.getRedes());
        assertNull(barbearia.getCapaUrl());
    }

    @Test
    void fotoDaGaleriaGuardaABarbeariaAImagemEAOrdem() {
        Barbearia barbearia = new Barbearia();
        barbearia.setNomeFantasia("Barbearia Gabriel Naoki");

        BarbeariaFoto foto = new BarbeariaFoto(barbearia, "data:image/png;base64,AAAA", 2);

        assertSame(barbearia, foto.getBarbearia());
        assertEquals("data:image/png;base64,AAAA", foto.getImagem());
        assertEquals(2, foto.getOrdem());
    }
}
