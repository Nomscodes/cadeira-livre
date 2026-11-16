package br.com.cadeiralivre.api.profissional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class ProfissionalHorarioTest {

    @Test
    void semanaPadraoTemOsSeteDiasDoProfissional() {
        Profissional gabriel = new Profissional();
        gabriel.setNome("Gabriel Naoki");

        List<ProfissionalHorario> semana = ProfissionalHorario.semanaPadrao(gabriel);

        assertEquals(7, semana.size());
        semana.forEach(dia -> assertSame(gabriel, dia.getProfissional()));
    }

    @Test
    void segundaASextaDas9As18ESabadoEDomingoDeFolga() {
        Map<DayOfWeek, ProfissionalHorario> semana = ProfissionalHorario.semanaPadrao(new Profissional()).stream()
                .collect(Collectors.toMap(ProfissionalHorario::getDiaSemana, Function.identity()));

        ProfissionalHorario segunda = semana.get(DayOfWeek.MONDAY);
        assertTrue(segunda.isAtivo());
        assertEquals(LocalTime.of(9, 0), segunda.getHoraInicio());
        assertEquals(LocalTime.of(18, 0), segunda.getHoraFim());
        assertTrue(semana.get(DayOfWeek.FRIDAY).isAtivo());
        assertFalse(semana.get(DayOfWeek.SATURDAY).isAtivo());
        assertFalse(semana.get(DayOfWeek.SUNDAY).isAtivo());
    }

    @Test
    void profissionalNovoAindaNaoTemConvite() {
        assertNull(new Profissional().getCodigoConvite());
    }
}
