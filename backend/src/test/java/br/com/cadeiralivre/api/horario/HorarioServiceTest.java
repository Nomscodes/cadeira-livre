package br.com.cadeiralivre.api.horario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
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
import br.com.cadeiralivre.api.horario.dto.HorarioDiaRequest;
import br.com.cadeiralivre.api.horario.dto.HorarioDiaResponse;
import br.com.cadeiralivre.api.horario.dto.HorarioSemanaRequest;
import br.com.cadeiralivre.api.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class HorarioServiceTest {

    @Mock
    private HorarioFuncionamentoRepository horarioRepository;

    @Mock
    private BarbeariaRepository barbeariaRepository;

    @InjectMocks
    private HorarioService horarioService;

    private final UUID minhaBarbearia = UUID.randomUUID();
    private final LocalTime noveHoras = LocalTime.of(9, 0);
    private final LocalTime dezoitoHoras = LocalTime.of(18, 0);

    private Barbearia barbearia;

    @BeforeEach
    void preparar() {
        // simula o que o JwtAuthenticationFilter faz com o token do dono
        TenantContext.set(minhaBarbearia);

        barbearia = new Barbearia();
        barbearia.setId(minhaBarbearia);
    }

    @AfterEach
    void limparTenant() {
        TenantContext.clear();
    }

    @Test
    void listarSempreDevolveOsSeteDiasDeSegundaADomingo() {
        when(horarioRepository.findAllByBarbeariaId(minhaBarbearia))
                .thenReturn(List.of(horario(DayOfWeek.MONDAY, true)));

        List<HorarioDiaResponse> semana = horarioService.listar();

        assertEquals(7, semana.size());
        assertEquals(DayOfWeek.MONDAY, semana.get(0).diaSemana());
        assertTrue(semana.get(0).aberto());
        assertEquals(noveHoras, semana.get(0).abertura());
        // domingo não foi configurado, então aparece fechado
        assertEquals(DayOfWeek.SUNDAY, semana.get(6).diaSemana());
        assertFalse(semana.get(6).aberto());
    }

    @Test
    void salvarCriaODiaQueAindaNaoExisteNaBarbeariaDoToken() {
        when(horarioRepository.findAllByBarbeariaId(minhaBarbearia)).thenReturn(List.of());
        when(barbeariaRepository.getReferenceById(minhaBarbearia)).thenReturn(barbearia);

        horarioService.salvar(semana(new HorarioDiaRequest(DayOfWeek.TUESDAY, true, noveHoras, dezoitoHoras)));

        ArgumentCaptor<HorarioFuncionamento> salvo = ArgumentCaptor.forClass(HorarioFuncionamento.class);
        verify(horarioRepository).save(salvo.capture());
        assertEquals(minhaBarbearia, salvo.getValue().getBarbearia().getId());
        assertEquals(DayOfWeek.TUESDAY, salvo.getValue().getDiaSemana());
        assertTrue(salvo.getValue().isAtivo());
        assertEquals(dezoitoHoras, salvo.getValue().getHoraFechamento());
    }

    @Test
    void salvarAtualizaODiaQueJaExisteSemCriarOutro() {
        HorarioFuncionamento segunda = horario(DayOfWeek.MONDAY, true);
        when(horarioRepository.findAllByBarbeariaId(minhaBarbearia)).thenReturn(List.of(segunda));

        // fecha a segunda-feira
        horarioService.salvar(semana(new HorarioDiaRequest(DayOfWeek.MONDAY, false, noveHoras, dezoitoHoras)));

        ArgumentCaptor<HorarioFuncionamento> salvo = ArgumentCaptor.forClass(HorarioFuncionamento.class);
        verify(horarioRepository).save(salvo.capture());
        assertSame(segunda, salvo.getValue());
        assertFalse(segunda.isAtivo());
        verify(barbeariaRepository, never()).getReferenceById(any());
    }

    @Test
    void aberturaDepoisDoFechamentoDevolve422ENaoSalva() {
        ApiException erro = assertThrows(ApiException.class, () -> horarioService.salvar(
                semana(new HorarioDiaRequest(DayOfWeek.MONDAY, true, dezoitoHoras, noveHoras))));

        assertEquals(422, erro.getStatus().value());
        verify(horarioRepository, never()).save(any());
    }

    @Test
    void diaRepetidoNaListaDevolve422() {
        HorarioDiaRequest segunda = new HorarioDiaRequest(DayOfWeek.MONDAY, true, noveHoras, dezoitoHoras);

        ApiException erro = assertThrows(ApiException.class,
                () -> horarioService.salvar(semana(segunda, segunda)));

        assertEquals(422, erro.getStatus().value());
        verify(horarioRepository, never()).save(any());
    }

    @Test
    void semBarbeariaNoTokenDevolve403() {
        TenantContext.clear();

        ApiException erro = assertThrows(ApiException.class, () -> horarioService.listar());

        assertEquals(403, erro.getStatus().value());
    }

    // ---------- métodos de apoio ----------

    private HorarioFuncionamento horario(DayOfWeek dia, boolean aberto) {
        HorarioFuncionamento h = new HorarioFuncionamento();
        h.setBarbearia(barbearia);
        h.setDiaSemana(dia);
        h.setAtivo(aberto);
        h.setHoraAbertura(noveHoras);
        h.setHoraFechamento(dezoitoHoras);
        return h;
    }

    private HorarioSemanaRequest semana(HorarioDiaRequest... dias) {
        return new HorarioSemanaRequest(List.of(dias));
    }
}
